package com.velogappie.app

import android.content.Context
import com.velogappie.app.ui.theme.AppAccent
import com.velogappie.app.ui.theme.AppTheme

private const val PREFS_NAME = "app_settings"
private const val PREF_AUTO_START_ON_BIKE_DETECTED = "auto_start_on_bike_detected"
private const val PREF_AUTO_START_WATCH_ON_PEDAL = "auto_start_watch_on_pedal"
private const val PREF_HEART_RATE_EVER_SEEN = "heart_rate_ever_seen"
private const val PREF_HANDLEBAR_FUNCTION = "handlebar_function"
private const val PREF_APP_THEME = "app_theme"
private const val PREF_APP_ACCENT = "app_accent"
private const val PREF_NAV_BRIDGE_ENABLED = "nav_bridge_enabled"
private const val PREF_WEATHER_DISPLAY_ENABLED = "weather_display_enabled"
private const val PREF_SPEED_ALERT_ENABLED = "speed_alert_enabled"
private const val PREF_SPEED_ALERT_THRESHOLD = "speed_alert_threshold_kmh"
private const val PREF_SERVICE_REMINDER_KM = "service_reminder_km"
private const val PREF_LAST_SERVICE_ODOMETER = "last_service_odometer"
private const val PREF_BATTERY_LOW_ALERT_ENABLED = "battery_low_alert_enabled"
private const val PREF_BATTERY_LOW_ALERT_THRESHOLD = "battery_low_alert_threshold"
private const val PREF_UPDATE_CHECK_ENABLED = "update_check_enabled"
private const val PREF_UPDATE_SKIPPED_VERSION = "update_skipped_version"

/** What the bike's handlebar long-press (normally the SOS/emergency-contact button)
 *  triggers in this app instead of/alongside its stock SOS handling. */
enum class HandlebarFunction {
    LAUNCH_CONTROL
}

/** Small flags that don't belong to any one feature file. */
object AppSettings {
    private fun prefs(context: Context) = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    fun isAutoStartOnBikeDetectedEnabled(context: Context): Boolean =
        prefs(context).getBoolean(PREF_AUTO_START_ON_BIKE_DETECTED, true)

    fun setAutoStartOnBikeDetectedEnabled(context: Context, enabled: Boolean) {
        prefs(context).edit().putBoolean(PREF_AUTO_START_ON_BIKE_DETECTED, enabled).apply()
    }

    fun isAutoStartWatchOnPedalEnabled(context: Context): Boolean =
        prefs(context).getBoolean(PREF_AUTO_START_WATCH_ON_PEDAL, true)

    fun setAutoStartWatchOnPedalEnabled(context: Context, enabled: Boolean) {
        prefs(context).edit().putBoolean(PREF_AUTO_START_WATCH_ON_PEDAL, enabled).apply()
    }

    /** Once the watch has reported a heart rate at least once, the cycle-display feature
     *  offers a clock/heart-rate/both choice instead of just a plain clock toggle. */
    fun hasHeartRateEverBeenSeen(context: Context): Boolean =
        prefs(context).getBoolean(PREF_HEART_RATE_EVER_SEEN, false)

    fun markHeartRateSeen(context: Context) {
        if (!hasHeartRateEverBeenSeen(context)) {
            prefs(context).edit().putBoolean(PREF_HEART_RATE_EVER_SEEN, true).apply()
        }
    }

    /** Default handlebar long-press function. */
    fun handlebarFunction(context: Context): HandlebarFunction {
        val name = prefs(context).getString(PREF_HANDLEBAR_FUNCTION, null)
        return HandlebarFunction.entries.firstOrNull { it.name == name } ?: HandlebarFunction.LAUNCH_CONTROL
    }

    fun setHandlebarFunction(context: Context, function: HandlebarFunction) {
        prefs(context).edit().putString(PREF_HANDLEBAR_FUNCTION, function.name).apply()
    }

    /** Seeded from the system dark-mode setting on first read only; user-controlled (via
     *  the Settings toggle) from then on, since that's an explicit override the system
     *  setting shouldn't silently take back. */
    fun appTheme(context: Context, systemIsDark: Boolean): AppTheme {
        val stored = prefs(context).getString(PREF_APP_THEME, null)
            ?.let { name -> AppTheme.entries.firstOrNull { it.name == name } }
        if (stored != null) return stored
        val initial = if (systemIsDark) AppTheme.DARK else AppTheme.LIGHT
        setAppTheme(context, initial)
        return initial
    }

    fun setAppTheme(context: Context, theme: AppTheme) {
        prefs(context).edit().putString(PREF_APP_THEME, theme.name).apply()
    }

    fun appAccent(context: Context): AppAccent {
        val name = prefs(context).getString(PREF_APP_ACCENT, null)
        return AppAccent.entries.firstOrNull { it.name == name } ?: AppAccent.SAGE
    }

    fun setAppAccent(context: Context, accent: AppAccent) {
        prefs(context).edit().putString(PREF_APP_ACCENT, accent.name).apply()
    }

    fun isNavBridgeEnabled(context: Context): Boolean =
        prefs(context).getBoolean(PREF_NAV_BRIDGE_ENABLED, false)

    fun setNavBridgeEnabled(context: Context, enabled: Boolean) {
        prefs(context).edit().putBoolean(PREF_NAV_BRIDGE_ENABLED, enabled).apply()
    }

    fun isWeatherDisplayEnabled(context: Context): Boolean =
        prefs(context).getBoolean(PREF_WEATHER_DISPLAY_ENABLED, false)

    fun setWeatherDisplayEnabled(context: Context, enabled: Boolean) {
        prefs(context).edit().putBoolean(PREF_WEATHER_DISPLAY_ENABLED, enabled).apply()
    }

    fun isSpeedAlertEnabled(context: Context): Boolean =
        prefs(context).getBoolean(PREF_SPEED_ALERT_ENABLED, false)

    fun setSpeedAlertEnabled(context: Context, enabled: Boolean) {
        prefs(context).edit().putBoolean(PREF_SPEED_ALERT_ENABLED, enabled).apply()
    }

    fun speedAlertThresholdKmh(context: Context): Int =
        prefs(context).getInt(PREF_SPEED_ALERT_THRESHOLD, 25)

    fun setSpeedAlertThresholdKmh(context: Context, kmh: Int) {
        prefs(context).edit().putInt(PREF_SPEED_ALERT_THRESHOLD, kmh).apply()
    }

    fun serviceReminderKm(context: Context): Int =
        prefs(context).getInt(PREF_SERVICE_REMINDER_KM, 0)

    fun setServiceReminderKm(context: Context, km: Int) {
        prefs(context).edit().putInt(PREF_SERVICE_REMINDER_KM, km).apply()
    }

    fun lastServiceOdometer(context: Context): Int =
        prefs(context).getInt(PREF_LAST_SERVICE_ODOMETER, 0)

    fun setLastServiceOdometer(context: Context, odometer: Int) {
        prefs(context).edit().putInt(PREF_LAST_SERVICE_ODOMETER, odometer).apply()
    }

    fun isBatteryLowAlertEnabled(context: Context): Boolean =
        prefs(context).getBoolean(PREF_BATTERY_LOW_ALERT_ENABLED, true)

    fun setBatteryLowAlertEnabled(context: Context, enabled: Boolean) {
        prefs(context).edit().putBoolean(PREF_BATTERY_LOW_ALERT_ENABLED, enabled).apply()
    }

    fun batteryLowAlertThreshold(context: Context): Int =
        prefs(context).getInt(PREF_BATTERY_LOW_ALERT_THRESHOLD, 20)

    fun setBatteryLowAlertThreshold(context: Context, threshold: Int) {
        prefs(context).edit().putInt(PREF_BATTERY_LOW_ALERT_THRESHOLD, threshold).apply()
    }

    fun isUpdateCheckEnabled(context: Context): Boolean =
        prefs(context).getBoolean(PREF_UPDATE_CHECK_ENABLED, true)

    fun setUpdateCheckEnabled(context: Context, enabled: Boolean) {
        prefs(context).edit().putBoolean(PREF_UPDATE_CHECK_ENABLED, enabled).apply()
    }

    fun updateSkippedVersion(context: Context): String? =
        prefs(context).getString(PREF_UPDATE_SKIPPED_VERSION, null)

    fun setUpdateSkippedVersion(context: Context, version: String?) {
        prefs(context).edit().putString(PREF_UPDATE_SKIPPED_VERSION, version).apply()
    }
}
