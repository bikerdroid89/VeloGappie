package com.velogappie.app.sun

import java.util.Calendar
import java.util.TimeZone
import kotlin.math.*

/**
 * Local sunrise/sunset calculation (NOAA solar position algorithm, simplified).
 * No network call — runs entirely on-device from lat/lon/date, used by the
 * "automatic lights at sunset" feature so the app never has to phone out for
 * weather/astronomy data to stay local-only.
 */
object SunCalculator {

    /** Returns minutes-since-midnight (local time) of sunset for the given lat/lon today. */
    fun sunsetMinutesOfDay(latitude: Double, longitude: Double, timeZone: TimeZone = TimeZone.getDefault()): Double {
        val cal = Calendar.getInstance(timeZone)
        val dayOfYear = cal.get(Calendar.DAY_OF_YEAR)

        val zenith = 90.833 // official sunset zenith
        val lngHour = longitude / 15.0

        val t = dayOfYear + ((18.0 - lngHour) / 24.0) // approx time for sunset
        val meanAnomaly = (0.9856 * t) - 3.289
        var trueLongitude = meanAnomaly + (1.916 * sin(Math.toRadians(meanAnomaly))) +
            (0.020 * sin(2 * Math.toRadians(meanAnomaly))) + 282.634
        trueLongitude = trueLongitude.mod(360.0)

        var rightAscension = Math.toDegrees(atan(0.91764 * tan(Math.toRadians(trueLongitude))))
        rightAscension = rightAscension.mod(360.0)
        val longitudeQuadrant = floor(trueLongitude / 90.0) * 90.0
        val raQuadrant = floor(rightAscension / 90.0) * 90.0
        rightAscension += (longitudeQuadrant - raQuadrant)
        rightAscension /= 15.0

        val sinDeclination = 0.39782 * sin(Math.toRadians(trueLongitude))
        val cosDeclination = cos(asin(sinDeclination))
        val cosHourAngle = (cos(Math.toRadians(zenith)) - (sinDeclination * sin(Math.toRadians(latitude)))) /
            (cosDeclination * cos(Math.toRadians(latitude)))

        if (cosHourAngle > 1.0) return Double.NaN // sun never sets (polar)
        if (cosHourAngle < -1.0) return Double.NaN // sun never rises

        var hourAngle = Math.toDegrees(acos(cosHourAngle))
        hourAngle /= 15.0 // setting, so we don't subtract from 360

        val localMeanTime = hourAngle + rightAscension - (0.06571 * t) - 6.622
        var utcTime = localMeanTime - lngHour
        utcTime = utcTime.mod(24.0)

        val offsetHours = timeZone.getOffset(System.currentTimeMillis()) / 3_600_000.0
        var localTime = utcTime + offsetHours
        localTime = localTime.mod(24.0)

        return localTime * 60.0
    }

    fun isPastSunset(latitude: Double, longitude: Double, timeZone: TimeZone = TimeZone.getDefault()): Boolean {
        val sunset = sunsetMinutesOfDay(latitude, longitude, timeZone)
        if (sunset.isNaN()) return false
        val cal = Calendar.getInstance(timeZone)
        val nowMinutes = cal.get(Calendar.HOUR_OF_DAY) * 60 + cal.get(Calendar.MINUTE)
        return nowMinutes >= sunset
    }
}
