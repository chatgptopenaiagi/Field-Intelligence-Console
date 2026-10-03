package com.fieldconsole.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.fieldconsole.data.local.entity.FieldEntry
import com.fieldconsole.data.repository.FieldEntryRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class HistoryViewModel @Inject constructor(
    private val repository: FieldEntryRepository
) : ViewModel() {

    val allEntries: StateFlow<List<FieldEntry>> = repository.observeAll()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val entryCount: StateFlow<Int> = repository.observeCount()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), 0)

    val favorites: StateFlow<List<FieldEntry>> = repository.observeFavorites()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    @OptIn(ExperimentalCoroutinesApi::class)
    fun searchResults(query: String): Flow<List<FieldEntry>> =
        if (query.isBlank()) repository.observeAll()
        else repository.search(query)

    fun toggleFavorite(entry: FieldEntry) {
        viewModelScope.launch {
            repository.setFavorite(entry.id, !entry.isFavorite)
        }
    }

    fun deleteEntry(entry: FieldEntry) {
        viewModelScope.launch { repository.delete(entry) }
    }

    fun deleteAll() {
        viewModelScope.launch { repository.deleteAll() }
    }
}
