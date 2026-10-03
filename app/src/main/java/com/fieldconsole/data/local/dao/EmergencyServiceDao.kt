package com.fieldconsole.data.local.dao

import androidx.room.*
import com.fieldconsole.data.local.entity.EmergencyService
import kotlinx.coroutines.flow.Flow

@Dao
interface EmergencyServiceDao {

    @Query("""
        SELECT * FROM emergency_services
        WHERE scanLat BETWEEN :lat - 0.05 AND :lat + 0.05
          AND scanLon BETWEEN :lon - 0.05 AND :lon + 0.05
        ORDER BY distanceM ASC
    """)
    fun observeNearby(lat: Double, lon: Double): Flow<List<EmergencyService>>

    @Query("""
        SELECT * FROM emergency_services
        WHERE scanLat BETWEEN :lat - 0.05 AND :lat + 0.05
          AND scanLon BETWEEN :lon - 0.05 AND :lon + 0.05
        ORDER BY distanceM ASC
    """)
    suspend fun getNearby(lat: Double, lon: Double): List<EmergencyService>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(services: List<EmergencyService>)

    @Query("DELETE FROM emergency_services WHERE scanLat BETWEEN :lat - 0.05 AND :lat + 0.05 AND scanLon BETWEEN :lon - 0.05 AND :lon + 0.05")
    suspend fun deleteForLocation(lat: Double, lon: Double)

    @Query("DELETE FROM emergency_services WHERE fetchedAt < :olderThanMs")
    suspend fun evictOlderThan(olderThanMs: Long)
}
