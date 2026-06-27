package com.velogappie.app.ride

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface RideDao {
    @Insert
    suspend fun insert(ride: RideEntity): Long

    @Query("SELECT * FROM rides ORDER BY startTime DESC")
    fun getAllRides(): Flow<List<RideEntity>>

    @Query("SELECT * FROM rides WHERE id IN (:ids) ORDER BY startTime ASC")
    suspend fun getByIds(ids: List<Long>): List<RideEntity>

    @Query("DELETE FROM rides WHERE id = :id")
    suspend fun deleteById(id: Long)

    @Query("DELETE FROM rides WHERE id IN (:ids)")
    suspend fun deleteByIds(ids: List<Long>)

    @Query("DELETE FROM rides")
    suspend fun deleteAll()

    @Query("SELECT COUNT(*) FROM rides")
    suspend fun getRideCount(): Int

    // --- ride groups ---

    @Insert
    suspend fun insertGroup(group: RideGroupEntity): Long

    @Query("SELECT * FROM ride_groups ORDER BY createdAt DESC")
    fun getAllGroups(): Flow<List<RideGroupEntity>>

    @Query("UPDATE rides SET groupId = :groupId WHERE id IN (:rideIds)")
    suspend fun assignRidesToGroup(rideIds: List<Long>, groupId: Long)

    @Query("UPDATE rides SET groupId = NULL WHERE id IN (:rideIds)")
    suspend fun removeRidesFromGroup(rideIds: List<Long>)

    @Query("UPDATE ride_groups SET name = :name WHERE id = :groupId")
    suspend fun renameGroup(groupId: Long, name: String)

    @Query("DELETE FROM ride_groups WHERE id = :groupId")
    suspend fun deleteGroup(groupId: Long)

    @Query("UPDATE rides SET groupId = NULL WHERE groupId = :groupId")
    suspend fun ungroupRidesInGroup(groupId: Long)

    // --- location points ---

    @Insert
    suspend fun insertLocationPoint(point: LocationPointEntity)

    @Insert
    suspend fun insertLocationPoints(points: List<LocationPointEntity>)

    @Query("SELECT * FROM location_points WHERE rideId = :rideId ORDER BY timestamp ASC")
    suspend fun getLocationPointsForRide(rideId: Long): List<LocationPointEntity>

    @Query("SELECT COUNT(*) FROM location_points WHERE rideId = :rideId")
    suspend fun getLocationPointCount(rideId: Long): Int

    @Query("DELETE FROM location_points WHERE rideId IN (:rideIds)")
    suspend fun deleteLocationPointsByRideIds(rideIds: List<Long>)
}
