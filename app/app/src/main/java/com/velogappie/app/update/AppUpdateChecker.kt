package com.velogappie.app.update

import android.content.Context
import android.content.Intent
import android.util.Log
import androidx.core.content.FileProvider
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.File
import java.net.HttpURLConnection
import java.net.URL

data class UpdateInfo(
    val version: String,
    val changelog: String,
    val apkUrl: String,
)

data class UpdateState(
    val available: Boolean = false,
    val version: String = "",
    val changelog: String = "",
    val apkUrl: String = "",
    val downloading: Boolean = false,
    val progress: Int = 0,
    val error: Boolean = false,
)

object AppUpdateChecker {

    private const val TAG = "AppUpdate"
    private const val REPO = "Bikerdroid89/VeloGappie"

    suspend fun check(currentVersion: String): UpdateInfo? = withContext(Dispatchers.IO) {
        try {
            val url = URL("https://api.github.com/repos/$REPO/releases/latest")
            val conn = (url.openConnection() as HttpURLConnection).apply {
                setRequestProperty("Accept", "application/vnd.github+json")
                connectTimeout = 10_000
                readTimeout = 10_000
            }
            if (conn.responseCode != 200) {
                conn.disconnect()
                return@withContext null
            }
            val json = JSONObject(conn.inputStream.bufferedReader().readText())
            conn.disconnect()

            val tagName = json.getString("tag_name")
            val version = tagName.removePrefix("v")
            if (!isNewer(version, currentVersion)) return@withContext null

            val changelog = json.optString("body", "")
            val assets = json.getJSONArray("assets")
            var apkUrl: String? = null
            for (i in 0 until assets.length()) {
                val asset = assets.getJSONObject(i)
                if (asset.getString("name").endsWith(".apk")) {
                    apkUrl = asset.getString("browser_download_url")
                    break
                }
            }
            if (apkUrl == null) return@withContext null

            UpdateInfo(version, changelog, apkUrl)
        } catch (e: Exception) {
            Log.w(TAG, "Update check failed", e)
            null
        }
    }

    suspend fun download(
        context: Context,
        apkUrl: String,
        onProgress: (Int) -> Unit,
    ): File? = withContext(Dispatchers.IO) {
        try {
            val conn = (URL(apkUrl).openConnection() as HttpURLConnection).apply {
                connectTimeout = 15_000
                readTimeout = 60_000
            }
            val totalSize = conn.contentLength
            val file = File(context.cacheDir, "update.apk")
            conn.inputStream.use { input ->
                file.outputStream().use { output ->
                    val buffer = ByteArray(8192)
                    var downloaded = 0L
                    var read: Int
                    while (input.read(buffer).also { read = it } != -1) {
                        output.write(buffer, 0, read)
                        downloaded += read
                        if (totalSize > 0) {
                            onProgress((downloaded * 100 / totalSize).toInt())
                        }
                    }
                }
            }
            conn.disconnect()
            file
        } catch (e: Exception) {
            Log.e(TAG, "Download failed", e)
            null
        }
    }

    fun installIntent(context: Context, apkFile: File): Intent {
        val uri = FileProvider.getUriForFile(
            context, "${context.packageName}.fileprovider", apkFile,
        )
        return Intent(Intent.ACTION_VIEW).apply {
            setDataAndType(uri, "application/vnd.android.package-archive")
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_GRANT_READ_URI_PERMISSION
        }
    }

    internal fun isNewer(latest: String, current: String): Boolean {
        val l = latest.split(".").map { it.toIntOrNull() ?: 0 }
        val c = current.split(".").map { it.toIntOrNull() ?: 0 }
        for (i in 0 until maxOf(l.size, c.size)) {
            val lv = l.getOrElse(i) { 0 }
            val cv = c.getOrElse(i) { 0 }
            if (lv > cv) return true
            if (lv < cv) return false
        }
        return false
    }
}
