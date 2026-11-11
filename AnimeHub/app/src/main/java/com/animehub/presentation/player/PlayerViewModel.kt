package com.animehub.presentation.player

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.animehub.data.settings.SettingsRepository
import com.animehub.domain.matching.TitleMatcher
import com.animehub.domain.model.AppSettings
import com.animehub.domain.model.ContinueItem
import com.animehub.domain.model.EntryRef
import com.animehub.domain.model.Episode
import com.animehub.domain.model.VideoSource
import com.animehub.domain.model.episodeKey
import com.animehub.domain.repository.AnimeRepository
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class PlayerArgs(
    val refs: List<EntryRef>,
    val title: String,
    val coverUrl: String?,
    val startSourceId: String,
    val startEpisodeId: String,
    val startNumber: Float,
)

data class SourceChoice(val sourceId: String, val name: String)

data class PlayerState(
    val loading: Boolean = true,
    val sources: List<SourceChoice> = emptyList(),
    val sourceId: String? = null,
    val episodes: List<Episode> = emptyList(),
    val episode: Episode? = null,
    val videos: List<VideoSource> = emptyList(),
    val selected: VideoSource? = null,
    val startPositionMs: Long = 0L,
    val error: String? = null,
) {
    val index: Int get() = episodes.indexOfFirst { it.id == episode?.id }
    val hasPrev: Boolean get() = index > 0
    val hasNext: Boolean get() = index >= 0 && index < episodes.lastIndex
}

class PlayerViewModel(
    private val repo: AnimeRepository,
    private val settingsRepo: SettingsRepository,
    private val args: PlayerArgs,
) : ViewModel() {
    private val workKey = TitleMatcher.normalize(args.title).ifBlank { args.title.lowercase() }
    private val _state = MutableStateFlow(PlayerState())
    val state: StateFlow<PlayerState> = _state.asStateFlow()

    private var settings = AppSettings()
    private var job: Job? = null

    val title: String get() = args.title

    init {
        job = viewModelScope.launch {
            settings = settingsRepo.settings.first()
            val names = repo.sourceNames().first()
            _state.update {
                it.copy(sources = args.refs.map { r -> SourceChoice(r.sourceId, names[r.sourceId] ?: r.sourceId) })
            }
            guarded { openSource(args.startSourceId, args.startEpisodeId, args.startNumber, null) }
        }
    }

    private suspend fun guarded(block: suspend () -> Unit) {
        try {
            block()
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            _state.update { it.copy(loading = false, error = e.message ?: "Erro desconhecido") }
        }
    }

    private fun refFor(sourceId: String): EntryRef = args.refs.first { it.sourceId == sourceId }

    private suspend fun openSource(sourceId: String, episodeId: String?, number: Float, resumeMs: Long?) {
        _state.update { it.copy(loading = true, error = null, sourceId = sourceId, videos = emptyList(), selected = null) }
        val episodes = repo.episodes(refFor(sourceId))
        val episode = episodes.firstOrNull { it.id == episodeId } ?: episodes.firstOrNull { it.number == number }
        if (episode == null) {
            _state.update {
                it.copy(
                    loading = false, episodes = episodes, episode = null,
                    error = "Esta fonte não tem o episódio ${number.episodeKey()}. Tente outra.",
                )
            }
            return
        }
        _state.update { it.copy(episodes = episodes) }
        loadEpisode(sourceId, episode, resumeMs)
    }

    private suspend fun loadEpisode(sourceId: String, episode: Episode, resumeMs: Long?) {
        _state.update { it.copy(loading = true, episode = episode, error = null, videos = emptyList(), selected = null) }
        val videos = repo.videos(refFor(sourceId), episode.id).sortedByDescending {
            settings.preferHls && it.url.substringBefore('?').endsWith(".m3u8", ignoreCase = true)
        }
        val position = resumeMs ?: repo.getProgress(workKey, episode.number.episodeKey())
        _state.update {
            it.copy(
                loading = false, videos = videos, selected = videos.firstOrNull(), startPositionMs = position,
                error = if (videos.isEmpty()) {
                    "Nenhum vídeo encontrado nesta fonte. Muitos sites usam players protegidos ou scripts " +
                        "que o app não executa; tente outra fonte."
                } else null,
            )
        }
    }

    fun switchSource(sourceId: String, currentPositionMs: Long) {
        if (sourceId == _state.value.sourceId) return
        val number = _state.value.episode?.number ?: args.startNumber
        job?.cancel()
        job = viewModelScope.launch { guarded { openSource(sourceId, null, number, currentPositionMs) } }
    }

    fun goTo(episode: Episode) {
        val sourceId = _state.value.sourceId ?: return
        job?.cancel()
        job = viewModelScope.launch { guarded { loadEpisode(sourceId, episode, null) } }
    }

    fun next() {
        val s = _state.value
        if (s.hasNext) goTo(s.episodes[s.index + 1])
    }

    fun previous() {
        val s = _state.value
        if (s.hasPrev) goTo(s.episodes[s.index - 1])
    }

    fun select(video: VideoSource, currentPositionMs: Long) =
        _state.update { it.copy(selected = video, startPositionMs = currentPositionMs, error = null) }

    fun onEnded() {
        if (settings.autoPlayNext) next()
    }

    /** Tenta automaticamente a próxima opção de vídeo; se acabaram, mostra o erro. */
    fun onPlaybackError(code: String, positionMs: Long) {
        val s = _state.value
        val i = s.videos.indexOf(s.selected)
        if (i >= 0 && i + 1 < s.videos.size) {
            _state.update { it.copy(selected = s.videos[i + 1], startPositionMs = positionMs) }
        } else {
            _state.update { it.copy(error = "Falha ao reproduzir ($code). Tente outra qualidade ou outra fonte.") }
        }
    }

    fun saveProgress(positionMs: Long, durationMs: Long) {
        val episode = _state.value.episode ?: return
        if (positionMs < 0) return
        viewModelScope.launch(NonCancellable) {
            repo.saveProgress(
                ContinueItem(
                    workKey = workKey, title = args.title, coverUrl = args.coverUrl, refs = args.refs,
                    episodeKey = episode.number.episodeKey(),
                    positionMs = positionMs, durationMs = durationMs.coerceAtLeast(0),
                )
            )
        }
    }
}
