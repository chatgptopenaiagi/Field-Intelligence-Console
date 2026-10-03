package com.fieldconsole.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.fieldconsole.network.*
import com.google.gson.JsonObject
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import javax.inject.Inject

@HiltViewModel
class NetworkViewModel @Inject constructor(private val repository: NetworkRepository) : ViewModel() {
    val entities = repository.observeEntities().stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())
    val reports = repository.observeReports().stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())
    private val _notice = MutableStateFlow<String?>(null)
    val notice = _notice.asStateFlow()
    private val _busy = MutableStateFlow(false)
    val busy = _busy.asStateFlow()
    private val _preview = MutableStateFlow<NetworkPreview?>(null)
    val preview = _preview.asStateFlow()

    fun readPreview(read: () -> ByteArray, format: String, mapping: Map<String, String> = emptyMap()) = action {
        _preview.value = withContext(Dispatchers.IO) { NetworkCodec.parse(read(), format, mapping) }
        _notice.value = "Preview only. No contacts saved or sent."
    }
    fun phonePreview(read: () -> NetworkPreview) = action {
        _preview.value = withContext(Dispatchers.IO) { read() }
        _notice.value = "Phone-contact preview only. Select records and confirm to save locally."
    }
    fun cancelPreview() { _preview.value = null; _notice.value = "Import cancelled. Store unchanged." }
    fun importSelected(ids: Set<String>) {
        val preview = _preview.value ?: return
        action {
            val counts = repository.importSelected(preview, ids)
            _preview.value = null
            _notice.value = "Imported ${counts.inserted}; duplicates retained: ${counts.duplicates}."
        }
    }
    fun save(entity: JsonObject) = action { repository.saveEntity(entity); _notice.value = "Contact saved locally." }
    fun delete(id: String) = action { repository.deleteEntity(id); _notice.value = "Contact and derived associations deleted locally." }
    fun saveReport(report: JsonObject) = action { repository.saveReport(report); _notice.value = "Report saved locally." }
    fun deleteReport(id: String) = action { repository.deleteReport(id); _notice.value = "Report deleted locally." }
    fun clearNotice() { _notice.value = null }
    fun notify(message: String) { _notice.value = message }
    private fun action(block: suspend () -> Unit) {
        if (_busy.value) return
        _busy.value = true
        viewModelScope.launch {
            try { block() }
            catch (e: CancellationException) { throw e }
            catch (_: Exception) { _notice.value = "QUERY FAILED — operation could not complete. No automatic reset or upload occurred." }
            finally { _busy.value = false }
        }
    }
}
