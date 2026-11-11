package com.animehub.data.repository

import com.animehub.data.local.FavoriteEntity
import com.animehub.data.local.LibraryDao
import com.animehub.data.local.ProgressEntity
import com.animehub.data.local.toWork
import com.animehub.data.scraper.PageFetcher
import com.animehub.data.scraper.SourceRegistry
import com.animehub.domain.matching.TitleMatcher
import com.animehub.domain.model.Anime
import com.animehub.domain.model.ContinueItem
import com.animehub.domain.model.EntryRef
import com.animehub.domain.model.Episode
import com.animehub.domain.model.VideoSource
import com.animehub.domain.model.Work
import com.animehub.domain.repository.AnimeRepository
import com.animehub.domain.source.AnimeSource
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withTimeout
import java.io.IOException

class AnimeRepositoryImpl(
    private val registry: SourceRegistry,
    private val dao: LibraryDao,
) : AnimeRepository {

    private suspend fun <T> safe(block: suspend () -> T): Result<T> = try {
        Result.success(block())
    } catch (e: TimeoutCancellationException) {
        Result.failure(IOException("Tempo esgotado"))
    } catch (e: CancellationException) {
        throw e
    } catch (e: Exception) {
        Result.failure(e)
    }

    private suspend fun targets(sourceId: String?): List<AnimeSource> =
        registry.awaitSites()
            .filter { it.info.enabled && (sourceId == null || it.info.id == sourceId) }
            .map { it.source }

    /** Consulta as fontes em paralelo; falhas isoladas não derrubam o resultado dos outros sites. */
    private suspend fun gather(sourceId: String?, block: suspend (AnimeSource) -> List<Anime>): List<Work> {
        val sources = targets(sourceId)
        if (sources.isEmpty()) return emptyList()
        val results = coroutineScope {
            sources.map { s -> async { safe { withTimeout(25_000) { block(s) } } } }.awaitAll()
        }
        if (results.all { it.isFailure }) {
            throw results.first().exceptionOrNull() ?: IOException("Falha ao consultar as fontes")
        }
        return TitleMatcher.group(results.flatMap { it.getOrNull().orEmpty() })
    }

    override suspend fun popular(sourceId: String?): List<Work> = gather(sourceId) { it.popular(1) }

    override suspend fun search(sourceId: String?, query: String): List<Work> =
        gather(sourceId) { it.search(query, 1) }

    override suspend fun details(ref: EntryRef): Anime = registry.get(ref.sourceId).details(ref.animeId)

    override suspend fun episodes(ref: EntryRef): List<Episode> = registry.get(ref.sourceId).episodes(ref.animeId)

    override suspend fun videos(ref: EntryRef, episodeId: String): List<VideoSource> =
        registry.get(ref.sourceId).videos(ref.animeId, episodeId)

    override suspend fun findOtherEntries(title: String, excludeSourceIds: Set<String>): List<Anime> {
        val sites = registry.awaitSites().filter { it.info.enabled && it.info.id !in excludeSourceIds }
        return coroutineScope {
            sites.map { site ->
                async {
                    safe { withTimeout(20_000) { site.source.search(title, 1) } }
                        .getOrNull()
                        ?.firstOrNull { TitleMatcher.isSame(title, it.title) }
                }
            }.awaitAll().filterNotNull()
        }
    }

    override fun clearCache() = PageFetcher.clearCache()

    override fun sourceNames(): Flow<Map<String, String>> =
        registry.sites.filterNotNull().map { list -> list.associate { it.info.id to it.info.name } }

    override fun favorites(): Flow<List<Work>> = dao.favorites().map { list -> list.map { it.toWork() } }

    override fun isFavorite(workKey: String): Flow<Boolean> = dao.isFavoriteFlow(workKey).map { it > 0 }

    override suspend fun toggleFavorite(work: Work) {
        if (dao.favoriteCount(work.key) > 0) {
            dao.deleteFavorite(work.key)
        } else {
            dao.insertFavorite(FavoriteEntity(work.key, work.title, work.coverUrl, EntryRef.encodeList(work.refs)))
        }
    }

    override fun continueWatching(): Flow<List<ContinueItem>> = dao.recent().map { list ->
        list.map {
            ContinueItem(
                workKey = it.workKey, title = it.title, coverUrl = it.coverUrl,
                refs = EntryRef.decodeList(it.refs), episodeKey = it.episodeKey,
                positionMs = it.positionMs, durationMs = it.durationMs,
            )
        }
    }

    override suspend fun saveProgress(item: ContinueItem) {
        dao.upsertProgress(
            ProgressEntity(
                workKey = item.workKey, episodeKey = item.episodeKey, title = item.title,
                coverUrl = item.coverUrl, refs = EntryRef.encodeList(item.refs),
                positionMs = item.positionMs, durationMs = item.durationMs,
                updatedAt = System.currentTimeMillis(),
            )
        )
    }

    override suspend fun getProgress(workKey: String, episodeKey: String): Long =
        dao.progress(workKey, episodeKey) ?: 0L
}
