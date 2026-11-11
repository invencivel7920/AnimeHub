package com.animehub.domain.model

import java.net.URLDecoder
import java.net.URLEncoder

data class Anime(
    val sourceId: String,
    val id: String,
    val title: String,
    val coverUrl: String? = null,
    val synopsis: String? = null,
)

data class Episode(
    val id: String,
    val number: Float,
    val title: String,
)

data class Subtitle(
    val url: String,
    val language: String,
)

data class VideoSource(
    val url: String,
    val quality: String,
    val subtitles: List<Subtitle> = emptyList(),
    val headers: Map<String, String> = emptyMap(),
)

/** Referência a uma obra dentro de uma fonte específica. */
data class EntryRef(val sourceId: String, val animeId: String) {
    companion object {
        fun encodeList(refs: List<EntryRef>): String = refs.joinToString(";") {
            URLEncoder.encode(it.sourceId, "UTF-8") + "|" + URLEncoder.encode(it.animeId, "UTF-8")
        }

        fun decodeList(s: String): List<EntryRef> = s.split(";").mapNotNull { part ->
            val i = part.indexOf('|')
            if (i < 0) null
            else EntryRef(
                URLDecoder.decode(part.substring(0, i), "UTF-8"),
                URLDecoder.decode(part.substring(i + 1), "UTF-8"),
            )
        }
    }
}

/** Uma obra que pode existir em várias fontes (agrupada pelo título). */
data class Work(
    val key: String,
    val title: String,
    val coverUrl: String?,
    val entries: List<Anime>,
) {
    val refs: List<EntryRef> get() = entries.map { EntryRef(it.sourceId, it.id) }
}

data class SiteInfo(
    val id: String,
    val name: String,
    val baseUrl: String,
    val enabled: Boolean,
    val builtIn: Boolean,
    val searchUrl: String?,
    val itemSelector: String?,
    val episodeSelector: String?,
)

data class ContinueItem(
    val workKey: String,
    val title: String,
    val coverUrl: String?,
    val refs: List<EntryRef>,
    val episodeKey: String,
    val positionMs: Long,
    val durationMs: Long,
)

fun Float.episodeKey(): String = if (this % 1f == 0f) toInt().toString() else toString()
