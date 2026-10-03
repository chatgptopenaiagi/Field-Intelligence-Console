package com.fieldconsole.data.repository

import com.fieldconsole.data.local.dao.FieldEntryDao
import com.fieldconsole.data.local.entity.FieldEntry
import kotlinx.coroutines.flow.Flow
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class FieldEntryRepository @Inject constructor(
    private val dao: FieldEntryDao
) {
    fun observeAll(): Flow<List<FieldEntry>> = dao.observeAll()
    fun observeCount(): Flow<Int>            = dao.observeCount()
    fun observeFavorites(): Flow<List<FieldEntry>> = dao.observeFavorites()
    fun observeByTag(tag: String): Flow<List<FieldEntry>> = dao.observeByTag(tag)
    fun search(query: String): Flow<List<FieldEntry>> = dao.search(query)

    suspend fun save(entry: FieldEntry): Long = dao.insert(entry)
    suspend fun update(entry: FieldEntry)     = dao.update(entry)
    suspend fun delete(entry: FieldEntry)     = dao.delete(entry)
    suspend fun deleteAll()                   = dao.deleteAll()
    suspend fun getById(id: Long): FieldEntry? = dao.getById(id)
    suspend fun getLatest(): FieldEntry?       = dao.getLatest()
    suspend fun setFavorite(id: Long, fav: Boolean) = dao.setFavorite(id, fav)
}
