package com.velogappie.app.drive

import android.content.Context
import android.util.Log
import com.google.android.gms.auth.api.identity.AuthorizationRequest
import com.google.android.gms.auth.api.identity.AuthorizationResult
import com.google.android.gms.auth.api.identity.Identity
import com.google.android.gms.common.api.Scope
import com.velogappie.app.ride.RideEntity
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.IOException
import java.io.OutputStreamWriter
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder

private const val TAG = "DriveSync"
private const val SCOPE_DRIVE_FILE = "https://www.googleapis.com/auth/drive.file"
private const val BACKUP_FILE_NAME = "VeloGappie_backup.json"
private const val PREFS_NAME = "drive_sync"
private const val PREF_ENABLED = "enabled"
private const val PREF_FILE_ID = "file_id"
private const val CONNECT_TIMEOUT_MS = 15_000
private const val READ_TIMEOUT_MS = 15_000

/** Caps the backed-up ride count so the upload doesn't grow without bound forever —
 *  most recent rides first (matches RideDao.getAllRides()'s ORDER BY startTime DESC). */
private const val MAX_BACKUP_RIDES = 1_000

/**
 * Optional, explicitly user-opted-in backup of settings + trip history to the user's own
 * Google Drive (drive.file scope: this app can only see/write the one file it creates,
 * nothing else in the user's Drive). The only network traffic this app ever makes, and it
 * never touches any third-party infrastructure.
 */
object DriveSync {
    private fun prefs(context: Context) = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    fun isEnabled(context: Context): Boolean = prefs(context).getBoolean(PREF_ENABLED, false)

    fun setEnabled(context: Context, enabled: Boolean) {
        prefs(context).edit().putBoolean(PREF_ENABLED, enabled).apply()
    }

    /** hasResolution()==true means the caller must launch result.pendingIntent (via an
     *  IntentSenderRequest) before retrying — only happens the first time, or if access
     *  was revoked since. */
    suspend fun authorize(context: Context): AuthorizationResult {
        val request = AuthorizationRequest.builder()
            .setRequestedScopes(listOf(Scope(SCOPE_DRIVE_FILE)))
            .build()
        return Identity.getAuthorizationClient(context).authorize(request).await()
    }

    fun buildBackupJson(lastKnownBikeName: String?, rides: List<RideEntity>): String {
        val settings = JSONObject().apply {
            put("lastKnownBikeName", lastKnownBikeName ?: JSONObject.NULL)
        }
        val ridesJson = JSONArray()
        rides.take(MAX_BACKUP_RIDES).forEach { ride ->
            ridesJson.put(
                JSONObject().apply {
                    put("startTime", ride.startTime)
                    put("endTime", ride.endTime)
                    put("distanceKm", ride.distanceKm)
                    put("avgSpeedKmh", ride.avgSpeedKmh)
                    put("maxSpeedKmh", ride.maxSpeedKmh)
                    put("avgHeartRateBpm", ride.avgHeartRateBpm ?: JSONObject.NULL)
                    put("maxHeartRateBpm", ride.maxHeartRateBpm ?: JSONObject.NULL)
                }
            )
        }
        return JSONObject().apply {
            put("settings", settings)
            put("rides", ridesJson)
        }.toString()
    }

    /** Call only after `authorize()` returned a result needing no further resolution.
     *  Throws on failure (network error, non-2xx response, unexpected body) — callers
     *  are expected to catch and log, per this app's "Drive sync is best-effort, never
     *  let it affect the ride/bike flow" policy. */
    suspend fun uploadBackup(context: Context, accessToken: String, json: String) = withContext(Dispatchers.IO) {
        val knownFileId = prefs(context).getString(PREF_FILE_ID, null)
        val fileId = knownFileId ?: findExistingFileId(accessToken)
        val resultId = if (fileId != null) {
            updateFile(accessToken, fileId, json)
            fileId
        } else {
            createFile(accessToken, json)
        }
        prefs(context).edit().putString(PREF_FILE_ID, resultId).apply()
    }

    private fun openConnection(url: URL): HttpURLConnection =
        (url.openConnection() as HttpURLConnection).apply {
            connectTimeout = CONNECT_TIMEOUT_MS
            readTimeout = READ_TIMEOUT_MS
        }

    /** Reads the response, logging and throwing on any non-2xx status instead of letting
     *  the original "FileNotFoundException reading .inputStream on a 4xx" confuse later. */
    private fun readResponseOrThrow(conn: HttpURLConnection): String {
        val code = conn.responseCode
        val stream = if (code in 200..299) conn.inputStream else conn.errorStream
        val body = stream?.bufferedReader()?.use { it.readText() } ?: ""
        if (code !in 200..299) {
            Log.e(TAG, "Drive API HTTP $code: $body")
            throw IOException("Drive API HTTP $code")
        }
        return body
    }

    private fun findExistingFileId(accessToken: String): String? {
        val query = URLEncoder.encode("name='$BACKUP_FILE_NAME' and trashed=false", "UTF-8")
        val url = URL("https://www.googleapis.com/drive/v3/files?q=$query&spaces=drive&fields=files(id)")
        val conn = openConnection(url).apply {
            requestMethod = "GET"
            setRequestProperty("Authorization", "Bearer $accessToken")
        }
        val body = readResponseOrThrow(conn)
        conn.disconnect()
        val files = JSONObject(body).optJSONArray("files") ?: return null
        return if (files.length() > 0) files.getJSONObject(0).getString("id") else null
    }

    private fun createFile(accessToken: String, json: String): String {
        val boundary = "velogappie_backup_boundary"
        val metadata = JSONObject().apply { put("name", BACKUP_FILE_NAME) }.toString()
        val body = "--$boundary\r\n" +
            "Content-Type: application/json; charset=UTF-8\r\n\r\n" +
            metadata +
            "\r\n--$boundary\r\n" +
            "Content-Type: application/json\r\n\r\n" +
            json +
            "\r\n--$boundary--"
        val url = URL("https://www.googleapis.com/upload/drive/v3/files?uploadType=multipart&fields=id")
        val conn = openConnection(url).apply {
            requestMethod = "POST"
            doOutput = true
            setRequestProperty("Authorization", "Bearer $accessToken")
            setRequestProperty("Content-Type", "multipart/related; boundary=$boundary")
        }
        OutputStreamWriter(conn.outputStream, Charsets.UTF_8).use { it.write(body) }
        val responseBody = readResponseOrThrow(conn)
        conn.disconnect()
        return JSONObject(responseBody).getString("id")
    }

    private fun updateFile(accessToken: String, fileId: String, json: String) {
        // HttpURLConnection can't issue PATCH directly; Drive's API honors this override header.
        val url = URL("https://www.googleapis.com/upload/drive/v3/files/$fileId?uploadType=media")
        val conn = openConnection(url).apply {
            requestMethod = "POST"
            doOutput = true
            setRequestProperty("Authorization", "Bearer $accessToken")
            setRequestProperty("Content-Type", "application/json")
            setRequestProperty("X-HTTP-Method-Override", "PATCH")
        }
        OutputStreamWriter(conn.outputStream, Charsets.UTF_8).use { it.write(json) }
        readResponseOrThrow(conn)
        conn.disconnect()
    }
}
