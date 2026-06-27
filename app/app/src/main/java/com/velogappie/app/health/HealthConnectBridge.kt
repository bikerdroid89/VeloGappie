package com.velogappie.app.health

import android.content.Context
import androidx.health.connect.client.HealthConnectClient
import androidx.health.connect.client.permission.HealthPermission
import androidx.health.connect.client.records.DistanceRecord
import androidx.health.connect.client.records.ExerciseSessionRecord
import androidx.health.connect.client.records.HeartRateRecord
import androidx.health.connect.client.records.Record
import androidx.health.connect.client.records.metadata.Metadata
import androidx.health.connect.client.units.Length
import android.content.Intent
import android.net.Uri
import java.time.Instant
import java.time.ZoneId

private const val PREFS_NAME = "health_sync"
private const val PREF_ENABLED = "enabled"

/**
 * Writes completed rides to Health Connect's on-device store (Binder IPC to the Health
 * Connect provider app — not a network call, consistent with this app's local-only design).
 * Every entry point here is best-effort: a missing/denied Health Connect install must never
 * affect bike control, so failures are swallowed rather than surfaced.
 */
object HealthConnectBridge {
    val REQUIRED_PERMISSIONS = setOf(
        HealthPermission.getWritePermission(ExerciseSessionRecord::class),
        HealthPermission.getWritePermission(DistanceRecord::class),
        HealthPermission.getWritePermission(HeartRateRecord::class),
    )

    fun isAvailable(context: Context): Boolean =
        HealthConnectClient.getSdkStatus(context) == HealthConnectClient.SDK_AVAILABLE

    suspend fun hasPermissions(context: Context): Boolean {
        if (!isAvailable(context)) return false
        return try {
            val granted = HealthConnectClient.getOrCreate(context).permissionController.getGrantedPermissions()
            granted.containsAll(REQUIRED_PERMISSIONS)
        } catch (e: Exception) {
            false
        }
    }

    /** Local on/off switch independent of the OS permission grant — letting the user pause
     *  syncing without going through Android's permission-revoke flow to turn it back on. */
    fun isSyncEnabled(context: Context): Boolean =
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE).getBoolean(PREF_ENABLED, false)

    fun setSyncEnabled(context: Context, enabled: Boolean) {
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE).edit().putBoolean(PREF_ENABLED, enabled).apply()
    }

    fun openHealthConnect(context: Context) {
        try {
            val intent = Intent("androidx.health.ACTION_HEALTH_CONNECT_SETTINGS")
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            context.startActivity(intent)
        } catch (_: Exception) {
            try {
                val intent = context.packageManager.getLaunchIntentForPackage("com.google.android.apps.healthdata")
                if (intent != null) {
                    context.startActivity(intent)
                } else {
                    context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse("market://details?id=com.google.android.apps.healthdata")).apply {
                        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    })
                }
            } catch (_: Exception) { }
        }
    }

    /** No-ops silently if Health Connect isn't installed/available, permissions aren't
     *  granted, or the user has paused syncing via the local toggle. */
    suspend fun writeRide(
        context: Context,
        startTimeMs: Long,
        endTimeMs: Long,
        distanceKm: Double,
        heartRateSamples: List<Pair<Long, Int>>,
    ) {
        if (!isSyncEnabled(context) || !hasPermissions(context)) return
        try {
            val client = HealthConnectClient.getOrCreate(context)
            val start = Instant.ofEpochMilli(startTimeMs)
            val end = Instant.ofEpochMilli(endTimeMs)
            val zone = ZoneId.systemDefault().rules.getOffset(start)
            val metadata = Metadata()

            val records = mutableListOf<Record>(
                ExerciseSessionRecord(
                    startTime = start,
                    startZoneOffset = zone,
                    endTime = end,
                    endZoneOffset = zone,
                    exerciseType = ExerciseSessionRecord.EXERCISE_TYPE_BIKING,
                    metadata = metadata,
                ),
                DistanceRecord(
                    startTime = start,
                    startZoneOffset = zone,
                    endTime = end,
                    endZoneOffset = zone,
                    distance = Length.meters(distanceKm * 1000.0),
                    metadata = metadata,
                ),
            )
            if (heartRateSamples.isNotEmpty()) {
                records += HeartRateRecord(
                    startTime = start,
                    startZoneOffset = zone,
                    endTime = end,
                    endZoneOffset = zone,
                    samples = heartRateSamples.map { (t, bpm) ->
                        HeartRateRecord.Sample(time = Instant.ofEpochMilli(t), beatsPerMinute = bpm.toLong())
                    },
                    metadata = metadata,
                )
            }
            client.insertRecords(records)
        } catch (e: Exception) {
            // Best-effort only — never let a Health Connect failure affect the ride/bike flow.
        }
    }
}
