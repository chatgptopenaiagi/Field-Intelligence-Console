package com.fieldconsole

import com.fieldconsole.data.local.dao.EmergencyServiceDao
import com.fieldconsole.data.local.entity.EmergencyService
import kotlinx.coroutines.flow.MutableStateFlow

class FakeEmergencyServiceDao : EmergencyServiceDao {
    val rows = MutableStateFlow<List<EmergencyService>>(emptyList())
    override fun observeNearby(lat: Double, lon: Double) = rows
    override suspend fun getNearby(lat: Double, lon: Double) = rows.value
    override suspend fun insertAll(services: List<EmergencyService>) { rows.value = services }
    override suspend fun deleteForLocation(lat: Double, lon: Double) { rows.value = emptyList() }
    override suspend fun evictOlderThan(olderThanMs: Long) = Unit
}
