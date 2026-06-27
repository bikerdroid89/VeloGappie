package com.velogappie.app.nav

import android.app.Notification
import android.media.session.MediaSession
import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification
import android.util.Log

private const val TAG = "NavBridge"

private const val GOOGLE_MAPS_PACKAGE = "com.google.android.apps.maps"
private const val KOMOOT_PACKAGE = "de.komoot.android"
private const val WAZE_PACKAGE = "com.waze"
private const val OSMAND_PACKAGE = "net.osmand.plus"
private const val OSMAND_FREE_PACKAGE = "net.osmand"
private const val RIDE_WITH_GPS_PACKAGE = "com.ridewithgps.mobile"
private const val BIKEMAP_PACKAGE = "com.toursprung.bikemap"
private const val MAPY_CZ_PACKAGE = "cz.seznam.mapy"

private val NAV_PACKAGES = setOf(
    GOOGLE_MAPS_PACKAGE, KOMOOT_PACKAGE, WAZE_PACKAGE,
    OSMAND_PACKAGE, OSMAND_FREE_PACKAGE,
    RIDE_WITH_GPS_PACKAGE, BIKEMAP_PACKAGE, MAPY_CZ_PACKAGE,
)

@Suppress("DEPRECATION")
class NavBridgeService : NotificationListenerService() {

    override fun onListenerConnected() {
        if (!NavBridgeState.isEnabled) return
        for (sbn in activeNotifications) {
            if (sbn.packageName in NAV_PACKAGES && sbn.isOngoing) {
                val extras = sbn.notification.extras ?: continue
                val title = extras.getCharSequence("android.title")?.toString() ?: continue
                val text = extras.getCharSequence("android.text")?.toString()
                val parsed = parseNavNotification(title, text)
                if (parsed != null) {
                    Log.d(TAG, "recovered nav from ${sbn.packageName}: ${parsed.direction}, ${parsed.distanceMeters}m")
                    NavBridgeState.latestInstruction = parsed
                    return
                }
            }
        }
    }

    override fun onNotificationPosted(sbn: StatusBarNotification) {
        if (!NavBridgeState.isEnabled) return

        // Media notifications — extract track title for the bike display
        val mediaToken = sbn.notification.extras
            ?.getParcelable<MediaSession.Token>(Notification.EXTRA_MEDIA_SESSION)
        if (mediaToken != null) {
            val title = sbn.notification.extras?.getCharSequence("android.title")?.toString()
            if (title != null) {
                NavBridgeState.latestTrack = title
            }
            return
        }

        val pkg = sbn.packageName
        if (pkg !in NAV_PACKAGES) return
        if (!sbn.isOngoing) return

        val extras = sbn.notification.extras ?: return
        val title = extras.getCharSequence("android.title")?.toString() ?: return
        val text = extras.getCharSequence("android.text")?.toString()

        Log.d(TAG, "nav notification from $pkg: title='$title' text='$text'")

        val parsed = parseNavNotification(title, text)

        if (parsed != null) {
            Log.d(TAG, "parsed: direction=${parsed.direction}, distance=${parsed.distanceMeters}m")
            NavBridgeState.latestInstruction = parsed
        }
    }

    override fun onNotificationRemoved(sbn: StatusBarNotification) {
        if (sbn.packageName in NAV_PACKAGES && sbn.isOngoing) {
            NavBridgeState.latestInstruction = null
        }
        val mediaToken = sbn.notification.extras
            ?.getParcelable<MediaSession.Token>(Notification.EXTRA_MEDIA_SESSION)
        if (mediaToken != null) {
            NavBridgeState.latestTrack = null
        }
    }
}

data class NavInstruction(
    val direction: NavDirection,
    val distanceMeters: Int,
    val streetName: String? = null,
    val eta: String? = null,
)

enum class NavDirection { FORWARD, LEFT, RIGHT, U_TURN, ARRIVE }

private val DISTANCE_PATTERN = Regex("""(\d+[.,]?\d*)\s*(m|km|mi|ft|meter|metre|meters|metres|kilometer|kilometres)""", RegexOption.IGNORE_CASE)

private fun parseDistanceMeters(text: String?): Int? {
    if (text == null) return null
    val match = DISTANCE_PATTERN.find(text) ?: return null
    val value = match.groupValues[1].replace(",", ".").toDoubleOrNull() ?: return null
    val unit = match.groupValues[2].lowercase()
    return when {
        unit.startsWith("km") || unit.startsWith("kilom") -> (value * 1000).toInt()
        unit.startsWith("mi") -> (value * 1609.34).toInt()
        unit.startsWith("ft") -> (value * 0.3048).toInt()
        else -> value.toInt()
    }
}

private fun detectDirection(text: String): NavDirection {
    val lower = text.lowercase()
    return when {
        lower.contains("arrive") || lower.contains("arrived") || lower.contains("destination") ||
            lower.contains("bestemming") || lower.contains("aangekomen") ||
            lower.contains("ziel") || lower.contains("angekommen") -> NavDirection.ARRIVE
        lower.contains("u-turn") || lower.contains("u‑turn") || lower.contains("keer om") ||
            lower.contains("umkehren") || lower.contains("wenden") -> NavDirection.U_TURN
        lower.contains("left") || lower.contains("links") || lower.contains("linksaf") ||
            lower.contains("gauche") || lower.contains("links abbiegen") -> NavDirection.LEFT
        lower.contains("right") || lower.contains("rechts") || lower.contains("rechtsaf") ||
            lower.contains("droite") || lower.contains("rechts abbiegen") -> NavDirection.RIGHT
        lower.contains("head") || lower.contains("straight") || lower.contains("continue") ||
            lower.contains("merge") || lower.contains("keep going") ||
            lower.contains("geradeaus") || lower.contains("rechtdoor") || lower.contains("ga door") ||
            lower.contains("weiter") -> NavDirection.FORWARD
        lower.contains("↰") || lower.contains("⬅") || lower.contains("↲") -> NavDirection.LEFT
        lower.contains("↱") || lower.contains("➡") || lower.contains("↳") -> NavDirection.RIGHT
        lower.contains("↑") || lower.contains("⬆") -> NavDirection.FORWARD
        lower.contains("↓") || lower.contains("⬇") -> NavDirection.U_TURN
        else -> NavDirection.FORWARD
    }
}

private val ETA_PATTERN = Regex("""(\d{1,2}:\d{2})\s*(AM|PM|am|pm)?""")
private val DURATION_PATTERN = Regex("""(\d+)\s*min""", RegexOption.IGNORE_CASE)

private fun parseEta(text: String?): String? {
    if (text == null) return null
    ETA_PATTERN.find(text)?.let { return it.groupValues[1] + (it.groupValues[2].let { s -> if (s.isNotEmpty()) " $s" else "" }) }
    DURATION_PATTERN.find(text)?.let { return "${it.groupValues[1]} min" }
    return null
}

private fun parseNavNotification(title: String, text: String?): NavInstruction? {
    val direction = detectDirection(title)
    val distance = parseDistanceMeters(title) ?: parseDistanceMeters(text)
        ?: if (direction == NavDirection.ARRIVE) 0 else return null
    val eta = parseEta(text)
    return NavInstruction(direction, distance, text, eta)
}
