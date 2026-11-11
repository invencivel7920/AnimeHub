package com.animehub.domain.repository

import com.animehub.domain.model.Anime
import com.animehub.domain.model.ContinueItem
import com.animehub.domain.model.EntryRef
import com.animehub.domain.model.Episode
import com.animehub.domain.model.VideoSource
import com.animehub.domain.model.Work
import kotlinx.coroutines.flow.Flow

interface AnimeRepository {
    /** [sourceId] = null consulta todos os sites ativos e agrupa a mesma obra. */
    suspend fun popular(sourceId: String?): List<Work>
    suspend fun search(sourceId: String?, query: String): List<Work>

    suspend fun details(ref: EntryRef): Anime
    suspend fun episodes(ref: EntryRef): List<Episode>
    suspend fun videos(ref: EntryRef, episodeId: String): List<VideoSource>

    /** Procura a mesma obra nos outros sites ativos. */
    suspend fun findOtherEntries(title: String, excludeSourceIds: Set<String>): List<Anime>

    /** Descarta páginas em cache (usado ao puxar para atualizar). */
    fun clearCache()

    fun sourceNames(): Flow<Map<String, String>>

    fun favorites(): Flow<List<Work>>
    fun isFavorite(workKey: String): Flow<Boolean>
    suspend fun toggleFavorite(work: Work)

    fun continueWatching(): Flow<List<ContinueItem>>
    suspend fun saveProgress(item: ContinueItem)
    suspend fun getProgress(workKey: String, episodeKey: String): Long
}
