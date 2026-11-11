package com.animehub.data.scraper

import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import org.jsoup.nodes.Document
import kotlin.coroutines.cancellation.CancellationException

data class DetectedSite(
    val name: String,
    val baseUrl: String,
    val searchUrl: String?,
    val itemsFound: Int,
)

/** Analisa a página inicial de um site e descobre nome, URL de busca e se há obras extraíveis. */
class SiteDetector(private val fetcher: PageFetcher) {

    suspend fun detect(base: HttpUrl): DetectedSite {
        val doc = fetcher.document(base.toString())
        val finalUrl = doc.location().toHttpUrlOrNull() ?: base
        val cards = ScrapeHeuristics.extractCards(doc, "probe", null)

        val name = doc.selectFirst("meta[property=og:site_name]")?.attr("content")?.trim()
            ?.takeIf { it.isNotBlank() }
            ?: doc.title().split(" - ", " | ", " – ", " — ").firstOrNull()?.trim()?.takeIf { it.isNotBlank() }
            ?: finalUrl.host.removePrefix("www.")

        val search = fromForms(doc) ?: probeCommon(finalUrl, cards.map { it.id }.toSet())
        return DetectedSite(name.take(40), finalUrl.toString(), search, cards.size)
    }

    private fun fromForms(doc: Document): String? {
        val knownNames = setOf("s", "q", "search", "query", "keyword", "keywords", "k", "term", "busca")
        for (form in doc.select("form")) {
            if (form.attr("method").ifBlank { "get" }.lowercase() != "get") continue
            val input = form.select("input[name]").firstOrNull {
                it.attr("type").equals("search", true) || it.attr("name").lowercase() in knownNames
            } ?: continue
            val action = form.absUrl("action").ifBlank { doc.location() }
            val url = action.toHttpUrlOrNull() ?: continue
            if (url.scheme != "https" || !ScrapeHeuristics.sameHost(action, doc.location())) continue
            val builder = url.newBuilder()
            form.select("input[type=hidden][name]").forEach { builder.setQueryParameter(it.attr("name"), it.attr("value")) }
            builder.setQueryParameter(input.attr("name"), "{q}")
            return builder.build().toString().replace("%7Bq%7D", "{q}")
        }
        return null
    }

    private suspend fun probeCommon(base: HttpUrl, homeLinks: Set<String>): String? {
        val origin = "${base.scheme}://${base.host}"
        val candidates = listOf(
            "$origin/?s={q}", "$origin/search?q={q}", "$origin/search?keyword={q}",
            "$origin/busca?q={q}", "$origin/?search={q}",
        )
        for (template in candidates) {
            val links = try {
                ScrapeHeuristics.extractCards(fetcher.document(template.replace("{q}", "a")), "probe", null)
                    .map { it.id }.toSet()
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                emptySet()
            }
            // Se devolve exatamente a página inicial, o site ignorou o parâmetro.
            if (links.isNotEmpty() && links != homeLinks) return template
        }
        return null
    }
}
