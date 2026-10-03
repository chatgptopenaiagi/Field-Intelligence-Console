package com.fieldconsole.data.local.dao

import androidx.room.*
import com.fieldconsole.data.local.entity.EmergencyService
import kotlinx.coroutines.flow.Flow

@Dao
interface EmergencyServiceDao {

    @Query("""
        SELECT * FROM emergency_services
        WHERE scanLat = :lat AND scanLon = :lon
        ORDER BY distanceM ASC
    """)
    fun observeNearby(lat: Double, lon: Double): Flow<List<EmergencyService>>

    @Query("""
        SELECT * FROM emergency_services
        WHERE scanLat = :lat AND scanLon = :lon
        ORDER BY distanceM ASC
    """)
    suspend fun getNearby(lat: Double, lon: Double): List<EmergencyService>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(services: List<EmergencyService>)

    @Query("DELETE FROM emergency_services WHERE scanLat = :lat AND scanLon = :lon")
    suspend fun deleteForLocation(lat: Double, lon: Double)

    @Transaction
    suspend fun replaceForLocation(lat: Double, lon: Double, services: List<EmergencyService>) {
        deleteForLocation(lat, lon)
        insertAll(services)
    }

    @Query("DELETE FROM emergency_services WHERE fetchedAt < :olderThanMs")
    suspend fun evictOlderThan(olderThanMs: Long)
}
