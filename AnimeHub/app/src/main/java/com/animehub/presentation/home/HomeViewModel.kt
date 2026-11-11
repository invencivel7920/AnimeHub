package com.animehub.presentation.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.animehub.domain.model.ContinueItem
import com.animehub.domain.model.SiteInfo
import com.animehub.domain.model.Work
import com.animehub.domain.repository.AnimeRepository
import com.animehub.domain.repository.SiteRepository
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class HomeState(
    val sites: List<SiteInfo> = emptyList(),
    /** null = todas as fontes */
    val selected: String? = null,
    val query: String = "",
    val loading: Boolean = true,
    val noSites: Boolean = false,
    val items: List<Work> = emptyList(),
    val continueItems: List<ContinueItem> = emptyList(),
    val error: String? = null,
)

class HomeViewModel(
    private val repo: AnimeRepository,
    siteRepo: SiteRepository,
) : ViewModel() {
    private val _state = MutableStateFlow(HomeState())
    val state: StateFlow<HomeState> = _state.asStateFlow()

    private var job: Job? = null
    private var debounceJob: Job? = null
    private var lastIds: Set<String>? = null

    init {
        viewModelScope.launch {
            siteRepo.sites().collect { all ->
                val enabled = all.filter { it.enabled }
                val ids = enabled.map { it.id }.toSet()
                _state.update { st -> st.copy(sites = enabled, selected = st.selected?.takeIf { it in ids }) }
                if (ids != lastIds) {
                    lastIds = ids
                    load()
                }
            }
        }
        viewModelScope.launch {
            repo.continueWatching().collect { c -> _state.update { it.copy(continueItems = c) } }
        }
    }

    fun select(sourceId: String?) {
        _state.update { it.copy(selected = sourceId) }
        load()
    }

    /** Busca enquanto digita, com atraso para não disparar uma requisição por letra. */
    fun onQuery(q: String) {
        _state.update { it.copy(query = q) }
        debounceJob?.cancel()
        debounceJob = viewModelScope.launch {
            delay(600)
            load()
        }
    }

    fun clearQuery() {
        debounceJob?.cancel()
        _state.update { it.copy(query = "") }
        load()
    }

    fun submit() {
        debounceJob?.cancel()
        load()
    }

    fun refresh() {
        debounceJob?.cancel()
        repo.clearCache()
        load()
    }

    private fun load() {
        job?.cancel()
        job = viewModelScope.launch {
            val s = _state.value
            if (s.sites.isEmpty()) {
                _state.update { it.copy(noSites = true, loading = false, items = emptyList(), error = null) }
                return@launch
            }
            _state.update { it.copy(noSites = false, loading = true, error = null) }
            try {
                val result = if (s.query.isBlank()) repo.popular(s.selected) else repo.search(s.selected, s.query.trim())
                _state.update { it.copy(items = result, loading = false) }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                _state.update { it.copy(loading = false, items = emptyList(), error = e.message ?: "Erro desconhecido") }
            }
        }
    }
}
