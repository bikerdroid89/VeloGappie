package com.velogappie.app.ride

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "location_points")
data class LocationPointEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val rideId: Long,
    val timestamp: Long,
    val latitude: Double,
    val longitude: Double,
    val altitude: Double? = null,
)
