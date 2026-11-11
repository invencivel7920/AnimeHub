package com.animehub.data.scraper

import com.animehub.data.network.UrlValidator
import com.animehub.domain.matching.TitleMatcher
import com.animehub.domain.model.Anime
import com.animehub.domain.model.Episode
import com.animehub.domain.model.SiteInfo
import com.animehub.domain.model.VideoSource
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import okhttp3.OkHttpClient
import java.io.IOException
import java.net.URLEncoder

/**
 * Fonte genérica guiada por [SiteInfo]: funciona com qualquer site HTML comum usando
 * heurísticas (e seletores CSS opcionais configurados pelo usuário).
 * Os IDs de obra e episódio são as próprias URLs das páginas.
 */
class GenericWebSource(private val site: SiteInfo, client: OkHttpClient) : HtmlSource(client) {
    override val id: String = site.id
    override val name: String = site.name

    override suspend fun popular(page: Int): List<Anime> {
        if (page > 1) return emptyList()
        return ScrapeHeuristics.extractCards(document(site.baseUrl), id, site.itemSelector)
    }

    override suspend fun search(query: String, page: Int): List<Anime> {
        if (page > 1) return emptyList()
        val template = site.searchUrl
        if (template.isNullOrBlank()) {
            val needle = TitleMatcher.normalize(query)
            return popular(1).filter { TitleMatcher.normalize(it.title).contains(needle) }
        }
        val url = template.replace("{q}", URLEncoder.encode(query, "UTF-8"))
        if (!UrlValidator.isSafeUrl(url)) throw IOException("URL de busca inválida")
        return ScrapeHeuristics.extractCards(document(url), id, site.itemSelector)
    }

    override suspend fun details(animeId: String): Anime =
        ScrapeHeuristics.extractDetails(document(animeId), id, animeId)

    override suspend fun episodes(animeId: String): List<Episode> =
        ScrapeHeuristics.extractEpisodes(document(animeId), animeId, site.episodeSelector)

    override suspend fun videos(animeId: String, episodeId: String): List<VideoSource> {
        val page = document(episodeId, useCache = false) // links de vídeo costumam expirar
        val sources = mutableListOf<Pair<ScrapeHeuristics.Media, String>>()
        sources += ScrapeHeuristics.extractMedia(page) to episodeId

        val frames = page.select("iframe[src], iframe[data-src]")
            .map { it.absUrl("src").ifBlank { it.absUrl("data-src") } }
            .filter { UrlValidator.isSafeUrl(it) }
            .distinct()
            .take(4)
        val nested = coroutineScope {
            frames.map { frame ->
                async {
                    try {
                        ScrapeHeuristics.extractMedia(document(frame, mapOf("Referer" to episodeId))) to frame
                    } catch (e: CancellationException) {
                        throw e
                    } catch (e: Exception) {
                        null
                    }
                }
            }.map { it.await() }
        }
        sources += nested.filterNotNull()

        val seen = HashSet<String>()
        val videos = mutableListOf<VideoSource>()
        for ((media, referer) in sources) {
            for (url in media.urls) {
                if (!seen.add(url)) continue
                videos += VideoSource(
                    url = url,
                    quality = ScrapeHeuristics.qualityLabel(url),
                    subtitles = media.subtitles,
                    headers = mapOf("Referer" to referer),
                )
            }
        }
        return videos.sortedByDescending { it.url.substringBefore('?').endsWith(".m3u8", ignoreCase = true) }
    }
}
