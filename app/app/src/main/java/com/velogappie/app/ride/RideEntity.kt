package com.velogappie.app.ride

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "rides")
data class RideEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val startTime: Long,
    val endTime: Long,
    val distanceKm: Double,
    val avgSpeedKmh: Double,
    val maxSpeedKmh: Double,
    val avgHeartRateBpm: Int? = null,
    val maxHeartRateBpm: Int? = null,
    val avgCadenceRpm: Int? = null,
    val maxCadenceRpm: Int? = null,
    val startBatterySoc: Int? = null,
    val endBatterySoc: Int? = null,
    val elevationGainM: Double? = null,
    val groupId: Long? = null,
    val bikeSerial: String? = null,
)
