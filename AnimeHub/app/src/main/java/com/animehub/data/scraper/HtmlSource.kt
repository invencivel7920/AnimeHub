package com.animehub.data.scraper

import com.animehub.domain.source.AnimeSource
import okhttp3.OkHttpClient
import org.jsoup.nodes.Document

/**
 * Base para fontes escritas à mão em código (OkHttp + Jsoup). Para a maioria dos casos
 * não é necessário: basta adicionar o site pela URL na aba Fontes (veja [GenericWebSource]).
 */
abstract class HtmlSource(client: OkHttpClient) : AnimeSource {
    protected val fetcher = PageFetcher(client)

    protected suspend fun document(
        url: String,
        headers: Map<String, String> = emptyMap(),
        useCache: Boolean = true,
    ): Document = fetcher.document(url, headers, useCache)
}
