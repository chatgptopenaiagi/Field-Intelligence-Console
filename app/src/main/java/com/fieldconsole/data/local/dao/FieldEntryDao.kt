package com.fieldconsole.data.local.dao

import androidx.room.*
import com.fieldconsole.data.local.entity.FieldEntry
import kotlinx.coroutines.flow.Flow

@Dao
interface FieldEntryDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(entry: FieldEntry): Long

    @Update
    suspend fun update(entry: FieldEntry)

    @Delete
    suspend fun delete(entry: FieldEntry)

    @Query("DELETE FROM field_entries")
    suspend fun deleteAll()

    @Query("SELECT * FROM field_entries ORDER BY timestamp DESC")
    fun observeAll(): Flow<List<FieldEntry>>

    @Query("SELECT * FROM field_entries ORDER BY timestamp DESC")
    suspend fun getAll(): List<FieldEntry>

    @Query("SELECT * FROM field_entries WHERE id = :id LIMIT 1")
    suspend fun getById(id: Long): FieldEntry?

    @Query("SELECT * FROM field_entries ORDER BY timestamp DESC LIMIT 1")
    suspend fun getLatest(): FieldEntry?

    @Query("SELECT COUNT(*) FROM field_entries")
    fun observeCount(): Flow<Int>

    @Query("""
        SELECT * FROM field_entries
        WHERE note LIKE '%' || :query || '%'
           OR tag  LIKE '%' || :query || '%'
           OR placeName LIKE '%' || :query || '%'
           OR missionId LIKE '%' || :query || '%'
        ORDER BY timestamp DESC
    """)
    fun search(query: String): Flow<List<FieldEntry>>

    @Query("SELECT * FROM field_entries WHERE isFavorite = 1 ORDER BY timestamp DESC")
    fun observeFavorites(): Flow<List<FieldEntry>>

    @Query("SELECT * FROM field_entries WHERE tag = :tag ORDER BY timestamp DESC")
    fun observeByTag(tag: String): Flow<List<FieldEntry>>

    @Query("SELECT * FROM field_entries WHERE priority = :priority ORDER BY timestamp DESC")
    fun observeByPriority(priority: String): Flow<List<FieldEntry>>

    @Query("UPDATE field_entries SET isFavorite = :fav WHERE id = :id")
    suspend fun setFavorite(id: Long, fav: Boolean)
}
