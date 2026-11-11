package com.animehub.data.scraper

import com.animehub.data.network.UrlValidator
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runInterruptible
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.ResponseBody
import org.jsoup.Jsoup
import org.jsoup.nodes.Document
import java.io.IOException

/** O site exige uma verificação anti-bot que o app não contorna. */
class SiteBlockedException(message: String) : IOException(message)

/**
 * Único ponto de acesso HTTP para páginas HTML: valida a URL, limita o tamanho
 * da resposta, guarda um cache curto em memória e identifica desafios anti-bot
 * (sem tentar burlá-los).
 */
class PageFetcher(private val client: OkHttpClient) {

    private class CachedPage(val html: String, val finalUrl: String)

    /**
     * @param useCache false para páginas com links temporários (ex.: página do episódio).
     * Requisições com cabeçalhos extras nunca usam o cache.
     */
    suspend fun document(
        url: String,
        headers: Map<String, String> = emptyMap(),
        useCache: Boolean = true,
    ): Document = runInterruptible(Dispatchers.IO) {
        if (!UrlValidator.isSafeUrl(url)) throw IOException("Endereço bloqueado por segurança")
        val cacheable = useCache && headers.isEmpty()
        if (cacheable) {
            cache.get(url)?.let { return@runInterruptible Jsoup.parse(it.html, it.finalUrl) }
        }
        val request = Request.Builder()
            .url(url)
            .header("Accept", "text/html,application/xhtml+xml,*/*;q=0.8")
            .apply { headers.forEach { (k, v) -> header(k, v) } }
            .build()
        client.newCall(request).execute().use { resp ->
            val text = resp.body?.let { readLimited(it) }.orEmpty()
            if (!resp.isSuccessful) {
                if (isChallenge(resp.code, resp.header("Server"), text)) {
                    throw SiteBlockedException(
                        "O site exige verificação anti-bot (ex.: Cloudflare), que o app não contorna."
                    )
                }
                throw IOException("HTTP ${resp.code}")
            }
            val finalUrl = resp.request.url.toString()
            if (cacheable) cache.put(url, CachedPage(text, finalUrl))
            Jsoup.parse(text, finalUrl)
        }
    }

    private fun readLimited(body: ResponseBody): String {
        val source = body.source()
        if (source.request(MAX_BYTES + 1)) throw IOException("Página grande demais")
        return source.readString(body.contentType()?.charset(Charsets.UTF_8) ?: Charsets.UTF_8)
    }

    private fun isChallenge(code: Int, server: String?, body: String): Boolean =
        code in setOf(403, 429, 503) &&
            (server?.contains("cloudflare", ignoreCase = true) == true ||
                body.contains("cf-chl") || body.contains("Just a moment", ignoreCase = true))

    companion object {
        private const val MAX_BYTES = 5L * 1024 * 1024
        private val cache = TtlCache<String, CachedPage>(maxSize = 40, ttlMs = 5 * 60_000L)

        fun clearCache() = cache.clear()
    }
}
