package com.velogappie.app.weather

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.net.URL

data class WeatherData(val tempC: Int, val precipPercent: Int, val isSunny: Boolean)

object WeatherFetcher {

    suspend fun fetch(latitude: Double, longitude: Double): WeatherData? = withContext(Dispatchers.IO) {
        try {
            val url = "https://api.open-meteo.com/v1/forecast" +
                "?latitude=${"%.4f".format(latitude)}" +
                "&longitude=${"%.4f".format(longitude)}" +
                "&current=temperature_2m,precipitation_probability,weather_code"
            val json = JSONObject(URL(url).readText())
            val current = json.getJSONObject("current")
            val temp = current.getDouble("temperature_2m").toInt()
            val precip = current.optInt("precipitation_probability", 0)
            val code = current.optInt("weather_code", 0)
            WeatherData(temp, precip, isSunny = code <= 3)
        } catch (_: Exception) {
            null
        }
    }
}
