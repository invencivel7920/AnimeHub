package com.animehub.presentation.details

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.animehub.data.settings.SettingsRepository
import com.animehub.domain.matching.TitleMatcher
import com.animehub.domain.model.Anime
import com.animehub.domain.model.EntryRef
import com.animehub.domain.model.Episode
import com.animehub.domain.model.Work
import com.animehub.domain.repository.AnimeRepository
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class SourceEntry(val ref: EntryRef, val name: String, val episodeCount: Int? = null)

data class DetailsState(
    val loading: Boolean = true,
    val error: String? = null,
    val title: String = "",
    val coverUrl: String? = null,
    val synopsis: String? = null,
    val entries: List<SourceEntry> = emptyList(),
    val selected: EntryRef? = null,
    val episodes: List<Episode> = emptyList(),
    val loadingEpisodes: Boolean = false,
    val episodesError: String? = null,
    val searchingOthers: Boolean = false,
)

class DetailsViewModel(
    private val repo: AnimeRepository,
    private val settings: SettingsRepository,
    private val initial: List<EntryRef>,
) : ViewModel() {
    private val _state = MutableStateFlow(DetailsState())
    val state: StateFlow<DetailsState> = _state.asStateFlow()

    private var episodesJob: Job? = null

    @OptIn(ExperimentalCoroutinesApi::class)
    val favorite: StateFlow<Boolean> = _state
        .map { TitleMatcher.normalize(it.title) }
        .distinctUntilChanged()
        .flatMapLatest { key -> if (key.isBlank()) flowOf(false) else repo.isFavorite(key) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), false)

    init {
        viewModelScope.launch { load() }
    }

    private suspend fun load() {
        val names = repo.sourceNames().first()
        var anime: Anime? = null
        var chosen: EntryRef? = null
        var lastError: String? = null
        for (ref in initial) {
            try {
                anime = repo.details(ref)
                chosen = ref
                break
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                lastError = e.message
            }
        }
        if (anime == null || chosen == null) {
            _state.update { it.copy(loading = false, error = lastError ?: "Não foi possível abrir esta obra") }
            return
        }
        _state.update {
            it.copy(
                loading = false,
                title = anime.title,
                coverUrl = anime.coverUrl,
                synopsis = anime.synopsis,
                entries = initial.map { r -> SourceEntry(r, names[r.sourceId] ?: r.sourceId) },
            )
        }
        loadEpisodes(chosen)
        if (settings.settings.first().autoFindOtherSources) findOthers(anime.title, names)
    }

    private suspend fun findOthers(title: String, names: Map<String, String>) {
        _state.update { it.copy(searchingOthers = true) }
        try {
            val others = repo.findOtherEntries(title, initial.map { it.sourceId }.toSet())
            _state.update { st ->
                val known = st.entries.map { it.ref.sourceId }.toSet()
                val added = others.filter { it.sourceId !in known }.map {
                    SourceEntry(EntryRef(it.sourceId, it.id), names[it.sourceId] ?: it.sourceId)
                }
                st.copy(
                    entries = st.entries + added,
                    coverUrl = st.coverUrl ?: others.firstNotNullOfOrNull { it.coverUrl },
                    searchingOthers = false,
                )
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            _state.update { it.copy(searchingOthers = false) }
        }
    }

    fun select(ref: EntryRef) = loadEpisodes(ref)

    private fun loadEpisodes(ref: EntryRef) {
        episodesJob?.cancel()
        episodesJob = viewModelScope.launch {
            _state.update { it.copy(selected = ref, loadingEpisodes = true, episodes = emptyList(), episodesError = null) }
            try {
                val eps = repo.episodes(ref)
                _state.update { st ->
                    st.copy(
                        loadingEpisodes = false,
                        episodes = eps,
                        episodesError = if (eps.isEmpty()) "Nenhum episódio encontrado nesta fonte. Tente outra." else null,
                        entries = st.entries.map { if (it.ref == ref) it.copy(episodeCount = eps.size) else it },
                    )
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                _state.update {
                    it.copy(
                        loadingEpisodes = false,
                        episodesError = "Não foi possível carregar os episódios desta fonte: ${e.message ?: "erro"}",
                    )
                }
            }
        }
    }

    fun toggleFavorite() {
        val s = _state.value
        if (s.title.isBlank()) return
        val key = TitleMatcher.normalize(s.title).ifBlank { s.title.lowercase() }
        val work = Work(
            key = key,
            title = s.title,
            coverUrl = s.coverUrl,
            entries = s.entries.map { Anime(it.ref.sourceId, it.ref.animeId, s.title, s.coverUrl) },
        )
        viewModelScope.launch { repo.toggleFavorite(work) }
    }
}
