package com.fieldconsole.network

import androidx.room.withTransaction
import com.fieldconsole.data.local.database.AppDatabase
import com.fieldconsole.data.local.entity.NetworkEntity
import com.fieldconsole.data.local.entity.ExchangeReport
import com.google.gson.JsonObject
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class NetworkRepository @Inject constructor(private val db: AppDatabase) {
    private val dao get() = db.networkDao()
    fun observeEntities() = dao.observeEntities().map { rows -> rows.map { NetworkCodec.parseJson(it.json) } }
    fun observeReports() = dao.observeReports().map { rows -> rows.map { NetworkCodec.parseJson(it.json) } }
    private fun row(e: JsonObject) = NetworkEntity(e.text("id"), e.text("display_name"),
        listOf(e.text("display_name"), e.text("organization"), e.text("notes"), e["phones"].toString(), e["emails"].toString()).joinToString(" ").lowercase(), e.toString())
    suspend fun importSelected(preview: NetworkPreview, selectedIds: Set<String>): ImportCounts = db.withTransaction {
        require(selectedIds.all { id -> preview.entities.any { it.text("id") == id } }) { "Selection does not belong to preview." }
        val selected = preview.entities.filter { it.text("id") in selectedIds }
        selected.forEach { NetworkCodec.validateEntity(it) }
        val existing = dao.entities().map { NetworkCodec.parseJson(it.json) }
        val insert = NetworkCodec.freshRecords(existing,selected)
        val results = dao.insertEntities(insert.map { row(it) })
        val inserted = results.count { it != -1L }
        ImportCounts(inserted, selected.size - inserted)
    }
    suspend fun saveEntity(entity: JsonObject) {
        NetworkCodec.validateEntity(entity)
        dao.saveEntity(row(entity))
    }
    suspend fun deleteEntity(id: String) = dao.deleteEntity(id)
    suspend fun saveReport(report: JsonObject) {
        NetworkCodec.validateReport(report)
        dao.saveReport(ExchangeReport(report.text("id"), report.getAsJsonObject("observation").text("note").lowercase(), report.toString()))
    }
    suspend fun deleteReport(id: String) = dao.deleteReport(id)
}
