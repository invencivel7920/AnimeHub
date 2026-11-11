package com.animehub.presentation.details

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.animehub.data.download.Downloader
import com.animehub.data.settings.SettingsRepository
import com.animehub.domain.download.DownloadPlanner
import com.animehub.domain.download.DownloadQuality
import com.animehub.domain.matching.TitleMatcher
import com.animehub.domain.model.Anime
import com.animehub.domain.model.EntryRef
import com.animehub.domain.model.Episode
import com.animehub.domain.model.Work
import com.animehub.domain.model.episodeKey
import com.animehub.domain.repository.AnimeRepository
import com.animehub.presentation.common.languageMatches
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

data class DownloadUi(
    val visible: Boolean = false,
    val selected: Set<String> = emptySet(),
    val quality: DownloadQuality = DownloadQuality.BEST,
    val subtitleLanguage: String? = null,
    val includeSubtitles: Boolean = false,
    val languages: List<String> = emptyList(),
    val loadingOptions: Boolean = false,
    val running: Boolean = false,
    val finished: Int = 0,
    val total: Int = 0,
    val skipped: Int = 0,
    val message: String? = null,
)

class DetailsViewModel(
    private val repo: AnimeRepository,
    private val settings: SettingsRepository,
    private val initial: List<EntryRef>,
) : ViewModel() {
    private val _state = MutableStateFlow(DetailsState())
    val state: StateFlow<DetailsState> = _state.asStateFlow()

    private val _download = MutableStateFlow(DownloadUi())
    val download: StateFlow<DownloadUi> = _download.asStateFlow()

    private var episodesJob: Job? = null
    private var downloadJob: Job? = null

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
        downloadJob?.cancel()
        _download.value = DownloadUi()
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

    fun openDownload() {
        val s = _state.value
        if (s.episodes.isEmpty()) return
        val ref = s.selected ?: return
        if (_download.value.running) {
            _download.update { it.copy(visible = true) }
            return
        }
        viewModelScope.launch {
            val prefs = settings.settings.first()
            if (_state.value.selected != ref) return@launch
            _download.update {
                it.copy(
                    visible = true,
                    selected = s.episodes.map { ep -> ep.id }.toSet(),
                    quality = DownloadQuality.fromName(prefs.downloadQuality),
                    includeSubtitles = prefs.preferredSubtitleLanguage != null,
                    subtitleLanguage = prefs.preferredSubtitleLanguage,
                    languages = emptyList(),
                    loadingOptions = true,
                    running = false,
                    finished = 0,
                    total = 0,
                    skipped = 0,
                    message = null,
                )
            }
            loadDownloadOptions(s.episodes.minByOrNull { it.number } ?: s.episodes.first(), ref)
        }
    }

    private suspend fun loadDownloadOptions(sample: Episode, ref: EntryRef) {
        _download.update { it.copy(loadingOptions = true) }
        try {
            val videos = repo.videos(ref, sample.id)
            if (_state.value.selected != ref) return
            val languages = videos.flatMap { it.subtitles }
                .map { it.language }
                .filter { it.isNotBlank() && !it.equals("und", ignoreCase = true) }
                .distinct()
            _download.update { d ->
                val selectedLanguage = d.subtitleLanguage
                    ?.takeIf { lang -> languages.any { languageMatches(lang, it) } }
                    ?: languages.firstOrNull()
                d.copy(loadingOptions = false, languages = languages, subtitleLanguage = selectedLanguage)
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            _download.update { it.copy(loadingOptions = false) }
        }
    }

    fun closeDownload() {
        _download.update { it.copy(visible = false) }
    }

    fun toggleDownloadEpisode(episodeId: String) {
        _download.update { d ->
            val next = if (episodeId in d.selected) d.selected - episodeId else d.selected + episodeId
            d.copy(selected = next, message = null)
        }
    }

    fun setAllDownloadEpisodes(selected: Boolean) {
        val ids = if (selected) _state.value.episodes.map { it.id }.toSet() else emptySet()
        _download.update { it.copy(selected = ids, message = null) }
    }

    fun setDownloadQuality(quality: DownloadQuality) {
        _download.update { it.copy(quality = quality, message = null) }
        viewModelScope.launch { settings.setDownloadQuality(quality.name) }
    }

    fun setDownloadSubtitleLanguage(language: String?) {
        _download.update { it.copy(subtitleLanguage = language) }
    }

    fun setIncludeSubtitles(include: Boolean) {
        _download.update { it.copy(includeSubtitles = include) }
    }

    fun startDownload(context: Context) {
        if (_download.value.running) return
        val s = _state.value
        val ref = s.selected ?: return
        val d = _download.value
        val episodes = s.episodes.filter { it.id in d.selected }.sortedBy { it.number }
        if (episodes.isEmpty()) {
            _download.update { it.copy(message = "Selecione pelo menos um episódio.") }
            return
        }
        downloadJob?.cancel()
        downloadJob = viewModelScope.launch {
            val prefs = settings.settings.first()
            _download.update {
                it.copy(running = true, finished = 0, total = episodes.size, skipped = 0, message = null)
            }
            var queued = 0
            var skipped = 0
            for ((index, episode) in episodes.withIndex()) {
                try {
                    val videos = repo.videos(ref, episode.id)
                    val video = DownloadPlanner.choose(videos, d.quality)
                    if (video == null || !DownloadPlanner.isDirectFile(video.url)) {
                        skipped++
                    } else {
                        val episodeKey = episode.number.episodeKey()
                        val fileName = DownloadPlanner.fileName(
                            s.title, episodeKey, DownloadPlanner.extension(video.url),
                        )
                        val id = Downloader.enqueue(
                            context = context.applicationContext,
                            url = video.url,
                            headers = video.headers,
                            fileName = fileName,
                            title = "${s.title} · Ep $episodeKey",
                            description = "AnimeHub · ${video.quality}",
                            wifiOnly = prefs.downloadWifiOnly,
                        )
                        if (id != null) {
                            queued++
                            if (d.includeSubtitles) {
                                val subtitle = DownloadPlanner.chooseSubtitle(video.subtitles, d.subtitleLanguage)
                                if (subtitle != null) {
                                    val subName = DownloadPlanner.fileName(
                                        s.title, episodeKey, DownloadPlanner.extension(subtitle.url, "vtt"),
                                    )
                                    Downloader.enqueue(
                                        context = context.applicationContext,
                                        url = subtitle.url,
                                        headers = video.headers,
                                        fileName = subName,
                                        title = "Legenda · ${s.title} Ep $episodeKey",
                                        description = "AnimeHub · ${subtitle.language}",
                                        wifiOnly = prefs.downloadWifiOnly,
                                    )
                                }
                            }
                        } else {
                            skipped++
                        }
                    }
                } catch (e: CancellationException) {
                    throw e
                } catch (e: Exception) {
                    skipped++
                }
                _download.update { it.copy(finished = index + 1, skipped = skipped) }
            }
            val message = buildString {
                if (queued == 1) append("1 download iniciado. ")
                if (queued > 1) append("$queued downloads iniciados. ")
                if (skipped == 1) {
                    append("1 episódio usa streaming (HLS/DASH) ou link não suportado e foi ignorado. ")
                }
                if (skipped > 1) {
                    append("$skipped episódios usam streaming (HLS/DASH) ou links não suportados e foram ignorados. ")
                }
                if (queued == 0 && skipped == 0) append("Nada para baixar. ")
                append("Acompanhe pelo app Downloads ou pelas notificações.")
            }
            _download.update { it.copy(running = false, message = message) }
        }
    }

    fun cancelDownload() {
        downloadJob?.cancel()
        downloadJob = null
        _download.update { it.copy(running = false, message = "Fila de downloads cancelada.") }
    }
}
