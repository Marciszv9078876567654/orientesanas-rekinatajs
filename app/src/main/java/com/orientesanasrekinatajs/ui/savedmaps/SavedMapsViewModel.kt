package com.orientesanasrekinatajs.ui.savedmaps

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.orientesanasrekinatajs.data.local.SavedMap
import com.orientesanasrekinatajs.data.local.SavedMapRepository
import com.orientesanasrekinatajs.data.local.SavedMapDraft
import com.orientesanasrekinatajs.data.local.entity.ScannedMapEntity
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

enum class SavedMapsEvent {
    SAVED, DELETED, CLEARED, SAVE_FAILED, LOAD_FAILED, RENAME_FAILED, DELETE_FAILED, CLEAR_FAILED, COPY_FAILED,
}

data class SavedMapsUiState(
    val maps: List<ScannedMapEntity> = emptyList(),
    val isSaving: Boolean = false,
    val event: SavedMapsEvent? = null,
)

class SavedMapsViewModel(private val repository: SavedMapRepository) : ViewModel() {
    private val _uiState = MutableStateFlow(SavedMapsUiState())
    val uiState: StateFlow<SavedMapsUiState> = _uiState.asStateFlow()

    init {
        viewModelScope.launch {
            repository.maps.collect { maps -> _uiState.value = _uiState.value.copy(maps = maps) }
        }
    }

    fun save(draft: SavedMapDraft, onSaved: (ScannedMapEntity) -> Unit = {}) {
        if (_uiState.value.isSaving) return
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isSaving = true, event = null)
            val event = runCatching { repository.save(draft) }
                .fold(
                    onSuccess = { saved -> onSaved(saved); SavedMapsEvent.SAVED },
                    onFailure = { SavedMapsEvent.SAVE_FAILED },
                )
            _uiState.value = _uiState.value.copy(isSaving = false, event = event)
        }
    }

    fun load(id: String, onLoaded: (SavedMap) -> Unit) {
        viewModelScope.launch {
            runCatching { repository.load(id) }
                .onSuccess(onLoaded)
                .onFailure { _uiState.value = _uiState.value.copy(event = SavedMapsEvent.LOAD_FAILED) }
        }
    }

    fun rename(id: String, name: String, onRenamed: (ScannedMapEntity) -> Unit = {}) {
        viewModelScope.launch {
            runCatching { repository.rename(id, name) }
                .onSuccess(onRenamed)
                .onFailure { _uiState.value = _uiState.value.copy(event = SavedMapsEvent.RENAME_FAILED) }
        }
    }

    fun delete(id: String, onDeleted: () -> Unit = {}) {
        viewModelScope.launch {
            runCatching { repository.delete(id) }
                .onSuccess {
                    onDeleted()
                }
                .onFailure { _uiState.value = _uiState.value.copy(event = SavedMapsEvent.DELETE_FAILED) }
        }
    }

    fun clearAll() {
        viewModelScope.launch {
            runCatching { repository.clearAll() }
                .onSuccess { _uiState.value = _uiState.value.copy(event = SavedMapsEvent.CLEARED) }
                .onFailure { _uiState.value = _uiState.value.copy(event = SavedMapsEvent.CLEAR_FAILED) }
        }
    }

    fun copy(id: String, name: String) {
        viewModelScope.launch {
            runCatching { repository.copy(id, name) }
                .onFailure { _uiState.value = _uiState.value.copy(event = SavedMapsEvent.COPY_FAILED) }
        }
    }

    fun clearEvent() {
        _uiState.value = _uiState.value.copy(event = null)
    }

    companion object {
        fun factory(context: Context): ViewModelProvider.Factory = object : ViewModelProvider.Factory {
            @Suppress("UNCHECKED_CAST")
            override fun <T : ViewModel> create(modelClass: Class<T>): T =
                SavedMapsViewModel(SavedMapRepository.create(context.applicationContext)) as T
        }
    }
}
