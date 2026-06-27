package com.velogappie.app.ride

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "ride_groups")
data class RideGroupEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    val createdAt: Long = System.currentTimeMillis(),
)
