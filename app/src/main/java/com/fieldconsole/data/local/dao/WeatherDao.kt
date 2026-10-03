package com.fieldconsole.data.local.dao

import androidx.room.*
import com.fieldconsole.data.local.entity.WeatherData

@Dao
interface WeatherDao {
    @Query("SELECT * FROM weather_cache WHERE cacheKey = :key LIMIT 1")
    suspend fun getByKey(key: String): WeatherData?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(data: WeatherData)

    @Query("DELETE FROM weather_cache WHERE fetchedAt < :olderThanMs")
    suspend fun evictOlderThan(olderThanMs: Long)
}
