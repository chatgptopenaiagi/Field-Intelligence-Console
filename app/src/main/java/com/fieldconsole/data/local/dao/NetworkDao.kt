package com.fieldconsole.data.local.dao

import androidx.room.*
import com.fieldconsole.data.local.entity.NetworkEntity
import com.fieldconsole.data.local.entity.ExchangeReport
import kotlinx.coroutines.flow.Flow

@Dao
interface NetworkDao {
    @Query("SELECT * FROM network_entities ORDER BY displayName COLLATE NOCASE, id")
    fun observeEntities(): Flow<List<NetworkEntity>>
    @Query("SELECT * FROM network_entities ORDER BY displayName COLLATE NOCASE, id")
    suspend fun entities(): List<NetworkEntity>
    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertEntities(entities: List<NetworkEntity>): List<Long>
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun saveEntity(entity: NetworkEntity)
    @Query("DELETE FROM network_entities WHERE id = :id")
    suspend fun deleteEntity(id: String)
    @Query("SELECT * FROM exchange_reports ORDER BY id")
    fun observeReports(): Flow<List<ExchangeReport>>
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun saveReport(report: ExchangeReport)
    @Query("DELETE FROM exchange_reports WHERE id = :id")
    suspend fun deleteReport(id: String)
}
