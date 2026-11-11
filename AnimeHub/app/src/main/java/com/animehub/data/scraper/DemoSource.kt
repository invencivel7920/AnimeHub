package com.animehub.data.scraper

import com.animehub.domain.model.Anime
import com.animehub.domain.model.Episode
import com.animehub.domain.model.VideoSource
import com.animehub.domain.source.AnimeSource

/**
 * Fonte de demonstração com curtas animados de código aberto da Blender Foundation
 * (licença Creative Commons Attribution). Serve para testar player, biblioteca e navegação.
 */
class DemoSource : AnimeSource {
    override val id = ID
    override val name = NAME

    private data class Item(val id: String, val title: String, val synopsis: String, val mp4: String, val hls: String? = null)

    private val items = listOf(
        Item(
            "bbb", "Big Buck Bunny",
            "Um coelho gigante e gentil enfrenta três roedores provocadores. © Blender Foundation, CC BY 3.0.",
            "https://commondatastorage.googleapis.com/gtv-videos-bucket/sample/BigBuckBunny.mp4",
            "https://test-streams.mux.dev/x36xhzz/x36xhzz.m3u8",
        ),
        Item(
            "sintel", "Sintel",
            "Uma jovem guerreira procura seu dragão. © Blender Foundation, CC BY 3.0.",
            "https://commondatastorage.googleapis.com/gtv-videos-bucket/sample/Sintel.mp4",
        ),
        Item(
            "tos", "Tears of Steel",
            "Ficção científica em Amsterdã com robôs e cientistas. © Blender Foundation, CC BY 3.0.",
            "https://commondatastorage.googleapis.com/gtv-videos-bucket/sample/TearsOfSteel.mp4",
        ),
    )

    private fun Item.toAnime() = Anime(sourceId = this@DemoSource.id, id = id, title = title, synopsis = synopsis)

    override suspend fun popular(page: Int): List<Anime> =
        if (page > 1) emptyList() else items.map { it.toAnime() }

    override suspend fun search(query: String, page: Int): List<Anime> =
        if (page > 1) emptyList()
        else items.filter { it.title.contains(query, ignoreCase = true) }.map { it.toAnime() }

    override suspend fun details(animeId: String): Anime =
        items.first { it.id == animeId }.toAnime()

    override suspend fun episodes(animeId: String): List<Episode> =
        listOf(Episode(id = "1", number = 1f, title = items.first { it.id == animeId }.title))

    override suspend fun videos(animeId: String, episodeId: String): List<VideoSource> {
        val item = items.first { it.id == animeId }
        return buildList {
            item.hls?.let { add(VideoSource(url = it, quality = "HLS (auto)")) }
            add(VideoSource(url = item.mp4, quality = "MP4"))
        }
    }

    companion object {
        const val ID = "demo"
        const val NAME = "Demo (Blender Open Movies)"
        const val URL = "https://studio.blender.org/films/"
    }
}
