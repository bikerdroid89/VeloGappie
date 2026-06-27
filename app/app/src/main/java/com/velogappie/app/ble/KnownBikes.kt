package com.velogappie.app.ble

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject

private const val PREFS_NAME = "known_bikes"
private const val PREF_BIKES = "bikes"
private const val PREF_ACTIVE_SERIAL = "active_serial"

data class KnownBike(val address: String, val name: String, val serial: String)

object KnownBikes {
    private fun prefs(context: Context) = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    fun add(context: Context, address: String, name: String, serial: String) {
        val bikes = getAll(context).toMutableList()
        bikes.removeAll { it.serial == serial }
        bikes.add(0, KnownBike(address, name, serial))
        val arr = JSONArray()
        for (b in bikes) {
            arr.put(JSONObject().apply {
                put("address", b.address)
                put("name", b.name)
                put("serial", b.serial)
            })
        }
        prefs(context).edit()
            .putString(PREF_BIKES, arr.toString())
            .putString(PREF_ACTIVE_SERIAL, serial)
            .apply()
    }

    fun getAll(context: Context): List<KnownBike> {
        val json = prefs(context).getString(PREF_BIKES, null) ?: return emptyList()
        return try {
            val arr = JSONArray(json)
            (0 until arr.length()).map { i ->
                val obj = arr.getJSONObject(i)
                KnownBike(obj.getString("address"), obj.getString("name"), obj.getString("serial"))
            }
        } catch (_: Exception) { emptyList() }
    }

    fun activeSerial(context: Context): String? =
        prefs(context).getString(PREF_ACTIVE_SERIAL, null)

    fun setActiveSerial(context: Context, serial: String) {
        prefs(context).edit().putString(PREF_ACTIVE_SERIAL, serial).apply()
    }

    fun remove(context: Context, serial: String) {
        val bikes = getAll(context).filter { it.serial != serial }
        val arr = JSONArray()
        for (b in bikes) {
            arr.put(JSONObject().apply {
                put("address", b.address)
                put("name", b.name)
                put("serial", b.serial)
            })
        }
        prefs(context).edit().putString(PREF_BIKES, arr.toString()).apply()
        if (activeSerial(context) == serial) {
            prefs(context).edit().putString(PREF_ACTIVE_SERIAL, bikes.firstOrNull()?.serial).apply()
        }
    }
}
