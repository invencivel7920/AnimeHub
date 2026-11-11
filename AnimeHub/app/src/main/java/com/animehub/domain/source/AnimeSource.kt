package com.animehub.domain.source

import com.animehub.domain.model.Anime
import com.animehub.domain.model.Episode
import com.animehub.domain.model.VideoSource

/**
 * Contrato que toda fonte de conteúdo deve implementar.
 * O app principal só conhece esta interface, nunca uma fonte específica.
 */
interface AnimeSource {
    val id: String
    val name: String

    suspend fun popular(page: Int): List<Anime>
    suspend fun search(query: String, page: Int): List<Anime>
    suspend fun details(animeId: String): Anime
    suspend fun episodes(animeId: String): List<Episode>
    suspend fun videos(animeId: String, episodeId: String): List<VideoSource>
}
