package com.velogappie.app.ble

import android.content.Context

private const val PREFS_NAME = "last_known_bike"
private const val PREF_ADDRESS = "address"
private const val PREF_NAME = "name"

/**
 * Persists the most recently connected bike so the app can offer a one-tap reconnect
 * without rescanning, and so BikeProximityScanner knows which device to watch for in
 * the background.
 */
object LastKnownBike {
    private fun prefs(context: Context) = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    fun save(context: Context, address: String, name: String) {
        prefs(context).edit().putString(PREF_ADDRESS, address).putString(PREF_NAME, name).apply()
    }

    fun address(context: Context): String? = prefs(context).getString(PREF_ADDRESS, null)
    fun name(context: Context): String? = prefs(context).getString(PREF_NAME, null)
}
