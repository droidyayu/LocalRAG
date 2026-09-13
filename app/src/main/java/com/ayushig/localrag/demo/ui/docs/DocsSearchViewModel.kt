package com.ayushig.localrag.demo.ui.docs

import androidx.compose.runtime.Immutable
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.ayushig.localrag.android.LocalRag
import com.ayushig.localrag.android.LocalRagState
import com.ayushig.localrag.android.Passage
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

@Immutable
data class DocsSearchUiState(
    val engine: LocalRagState = LocalRagState.Idle,
    val query: String = "",
    val passages: List<Passage> = emptyList(),
    val searching: Boolean = false,
    val searched: Boolean = false,
    val lastQueryMs: Long = 0,
)

@HiltViewModel
class DocsSearchViewModel @Inject constructor(
    private val localRag: LocalRag,
) : ViewModel() {

    private val _uiState = MutableStateFlow(DocsSearchUiState())
    val uiState: StateFlow<DocsSearchUiState> = _uiState.asStateFlow()

    private var searchJob: Job? = null

    init {
        viewModelScope.launch {
            localRag.state.collect { state -> _uiState.value = _uiState.value.copy(engine = state) }
        }
        viewModelScope.launch { localRag.initialize() }
    }

    fun onQueryChange(value: String) {
        _uiState.value = _uiState.value.copy(query = value)
    }

    fun onSearch() {
        val query = _uiState.value.query.trim()
        if (query.isEmpty()) return

        searchJob?.cancel()
        _uiState.value = _uiState.value.copy(searching = true)
        searchJob = viewModelScope.launch {
            val start = System.currentTimeMillis()
            val passages = localRag.retrieveOnly(query)
            _uiState.value = _uiState.value.copy(
                passages = passages,
                searching = false,
                searched = true,
                lastQueryMs = System.currentTimeMillis() - start,
            )
        }
    }
}
