package com.animehub.data.scraper

import com.animehub.data.network.UrlValidator
import com.animehub.domain.model.Anime
import com.animehub.domain.model.Episode
import com.animehub.domain.model.Subtitle
import com.animehub.domain.model.episodeKey
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import org.jsoup.nodes.Document
import org.jsoup.nodes.Element

/**
 * Heurísticas para extrair obras, episódios e mídia de páginas HTML arbitrárias.
 * São funções puras sobre um [Document], fáceis de testar. Seletores CSS opcionais
 * do usuário têm prioridade sobre a detecção automática.
 *
 * Limitações: não executa JavaScript, então players que montam o link do vídeo via JS
 * ofuscado, DRM e desafios anti-bot não são suportados.
 */
object ScrapeHeuristics {
    private val junkPath = Regex(
        """(?i)/(login|register|signin|signup|contact|about|dmca|privacy|terms|tag|tags|category|categories|genre|genres|page|feed|wp-admin|wp-login|user|account)(/|$)"""
    )
    private val epText = Regex("""(?i)(?:epis[oó]dios?|episodes?|\bep\.?)\s*[-_#:]?\s*(\d+(?:\.\d+)?)""")
    private val epHref = Regex("""(?i)(?:epis[oó]dios?|episodes?|[-_/]ep)[-_/]?(\d+(?:\.\d+)?)(?:/|$|\?|-)""")
    private val anyNumber = Regex("""\d+(?:\.\d+)?""")
    private val mediaRegex = Regex(
        """https?://[^\s"'<>\\]+?\.(?:m3u8|mp4|mpd)(?:\?[^\s"'<>\\]*)?""", RegexOption.IGNORE_CASE
    )
    private val subRegex = Regex(
        """https?://[^\s"'<>\\]+?\.(?:vtt|srt)(?:\?[^\s"'<>\\]*)?""", RegexOption.IGNORE_CASE
    )
    private val resolution = Regex("""(\d{3,4})p""", RegexOption.IGNORE_CASE)

    fun sameHost(a: String, b: String): Boolean {
        fun host(x: String) = x.toHttpUrlOrNull()?.host?.removePrefix("www.")
        val ha = host(a)
        return ha != null && ha == host(b)
    }

    private fun anchorsFor(doc: Document, selector: String?): List<Element> =
        if (!selector.isNullOrBlank()) {
            doc.select(selector).mapNotNull { el ->
                if (el.tagName() == "a" && el.hasAttr("href")) el else el.selectFirst("a[href]")
            }
        } else doc.select("a[href]")

    fun extractCards(doc: Document, sourceId: String, itemSelector: String?, limit: Int = 60): List<Anime> {
        val pageUrl = doc.location()
        val custom = !itemSelector.isNullOrBlank()
        val seen = LinkedHashSet<String>()
        val out = mutableListOf<Anime>()
        for (a in anchorsFor(doc, itemSelector)) {
            val clean = a.absUrl("href").substringBefore('#')
            if (clean.isBlank() || !UrlValidator.isSafeUrl(clean) || !sameHost(clean, pageUrl)) continue
            val path = clean.toHttpUrlOrNull()?.encodedPath ?: continue
            if (path == "/" || clean.trimEnd('/') == pageUrl.substringBefore('#').trimEnd('/')) continue
            if (!custom && junkPath.containsMatchIn(path)) continue
            val img = a.selectFirst("img") ?: if (custom) a.parent()?.selectFirst("img") else null
            if (img == null && !custom) continue
            val title = listOf(
                a.attr("title"),
                img?.attr("alt").orEmpty(),
                a.selectFirst("h1,h2,h3,h4,.title,[class*=title]")?.text().orEmpty(),
                a.text(),
            ).map { it.trim() }.firstOrNull { it.length >= 2 } ?: continue
            if (!seen.add(clean)) continue
            val cover = img?.let { i ->
                listOf("data-src", "data-lazy-src", "data-original", "src")
                    .map { k -> i.absUrl(k) }
                    .firstOrNull { u -> u.isNotBlank() && UrlValidator.isSafeUrl(u) }
            }
            out += Anime(sourceId, clean, title.take(120), cover)
            if (out.size >= limit) break
        }
        return out
    }

    fun extractDetails(doc: Document, sourceId: String, url: String): Anime {
        fun meta(p: String) = doc.selectFirst("meta[property=$p], meta[name=$p]")
            ?.attr("content")?.trim()?.takeIf { it.isNotEmpty() }

        val siteName = meta("og:site_name")
        var title = meta("og:title") ?: doc.selectFirst("h1")?.text()?.trim() ?: doc.title()
        if (siteName != null) title = title.replace(siteName, "")
        title = title.split(" - ", " | ", " – ", " — ")
            .map { it.trim(' ', '-', '|', '–', '—') }
            .firstOrNull { it.isNotBlank() } ?: doc.title()
        title = title.replace(Regex("""(?i)^assistir\s+"""), "").trim()

        val cover = doc.selectFirst("meta[property=og:image]")?.absUrl("content")
            ?.takeIf { it.isNotBlank() && UrlValidator.isSafeUrl(it) }
        val synopsis = meta("og:description") ?: meta("description")
        return Anime(sourceId, url, title.ifBlank { url }, cover, synopsis)
    }

    fun extractEpisodes(doc: Document, animeUrl: String, selector: String?): List<Episode> {
        val pageUrl = doc.location()
        val custom = !selector.isNullOrBlank()
        val found = LinkedHashMap<String, Episode>()
        var index = 0
        for (a in anchorsFor(doc, selector)) {
            val href = a.absUrl("href").substringBefore('#')
            if (href.isBlank() || !UrlValidator.isSafeUrl(href) || !sameHost(href, pageUrl)) continue
            if (href.trimEnd('/') == animeUrl.trimEnd('/')) continue
            val text = a.text().trim()
            index++
            val number = epText.find(text)?.groupValues?.get(1)?.toFloatOrNull()
                ?: epHref.find(href)?.groupValues?.get(1)?.toFloatOrNull()
                ?: if (custom) anyNumber.find(text)?.value?.toFloatOrNull() ?: index.toFloat() else null
            if (number == null) continue
            found.putIfAbsent(href, Episode(href, number, text.ifBlank { "Episódio ${number.episodeKey()}" }.take(100)))
        }
        var candidates = found.values.toList()
        // Evita misturar episódios de "relacionados" quando a URL da obra aparece no link do episódio.
        val key = animeUrl.toHttpUrlOrNull()?.pathSegments?.lastOrNull { it.isNotBlank() }?.lowercase().orEmpty()
        if (key.length >= 3) {
            val matched = candidates.filter { it.id.lowercase().contains(key) }
            if (matched.isNotEmpty() && matched.size >= candidates.size / 2) candidates = matched
        }
        val result = candidates.sortedBy { it.number }.distinctBy { it.number }
        if (result.isEmpty() && doc.select("video, iframe").isNotEmpty()) {
            return listOf(Episode(animeUrl, 1f, "Assistir"))
        }
        return result
    }

    data class Media(val urls: List<String>, val subtitles: List<Subtitle>)

    fun extractMedia(doc: Document): Media {
        val html = doc.html().replace("\\/", "/").replace("&amp;", "&")
        val urls = LinkedHashSet<String>()
        doc.select("video[src], video source[src], source[src]").forEach { urls += it.absUrl("src") }
        mediaRegex.findAll(html).forEach { urls += it.value }

        val subs = LinkedHashMap<String, Subtitle>()
        doc.select("track[src]").forEach {
            subs.putIfAbsent(it.absUrl("src"), Subtitle(it.absUrl("src"), it.attr("srclang").ifBlank { "und" }))
        }
        subRegex.findAll(html).forEach { subs.putIfAbsent(it.value, Subtitle(it.value, "und")) }

        return Media(
            urls = urls.filter { it.isNotBlank() && UrlValidator.isSafeUrl(it) },
            subtitles = subs.values.filter { UrlValidator.isSafeUrl(it.url) },
        )
    }

    fun qualityLabel(url: String): String {
        resolution.find(url)?.let { return it.groupValues[1] + "p" }
        val lower = url.substringBefore('?').lowercase()
        return when {
            lower.endsWith(".m3u8") -> "HLS (auto)"
            lower.endsWith(".mpd") -> "DASH (auto)"
            else -> "MP4"
        }
    }
}
