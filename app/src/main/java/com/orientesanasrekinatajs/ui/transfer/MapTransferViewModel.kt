package com.orientesanasrekinatajs.ui.transfer

import android.content.Context
import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.orientesanasrekinatajs.data.local.SavedMap
import com.orientesanasrekinatajs.data.local.SavedMapDraft
import com.orientesanasrekinatajs.data.transfer.MapTransferRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

enum class MapTransferEvent { EXPORTED, EXPORT_FAILED, IMPORT_FAILED }

data class MapTransferUiState(
    val isWorking: Boolean = false,
    val event: MapTransferEvent? = null,
)

class MapTransferViewModel(
    private val repository: MapTransferRepository,
) : ViewModel() {
    private val _uiState = MutableStateFlow(MapTransferUiState())
    val uiState: StateFlow<MapTransferUiState> = _uiState.asStateFlow()

    fun export(uri: Uri, draft: SavedMapDraft, includeRoutes: Boolean) {
        if (_uiState.value.isWorking) return
        viewModelScope.launch {
            _uiState.value = MapTransferUiState(isWorking = true)
            _uiState.value = runCatching { repository.export(uri, draft, includeRoutes) }
                .fold(
                    onSuccess = { MapTransferUiState(event = MapTransferEvent.EXPORTED) },
                    onFailure = { MapTransferUiState(event = MapTransferEvent.EXPORT_FAILED) },
                )
        }
    }

    fun import(uri: Uri, onImported: (SavedMap) -> Unit) {
        if (_uiState.value.isWorking) return
        viewModelScope.launch {
            _uiState.value = MapTransferUiState(isWorking = true)
            _uiState.value = runCatching { repository.import(uri) }
                .fold(
                    onSuccess = { imported ->
                        onImported(imported)
                        MapTransferUiState()
                    },
                    onFailure = { MapTransferUiState(event = MapTransferEvent.IMPORT_FAILED) },
                )
        }
    }

    fun clearEvent() {
        _uiState.value = _uiState.value.copy(event = null)
    }

    companion object {
        fun factory(context: Context): ViewModelProvider.Factory = object : ViewModelProvider.Factory {
            @Suppress("UNCHECKED_CAST")
            override fun <T : ViewModel> create(modelClass: Class<T>): T =
                MapTransferViewModel(MapTransferRepository.create(context.applicationContext)) as T
        }
    }
}
