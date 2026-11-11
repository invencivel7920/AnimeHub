package com.animehub.domain.download

import com.animehub.domain.model.Subtitle
import com.animehub.domain.model.VideoSource

/** Preferência de qualidade usada nos downloads em lote. */
enum class DownloadQuality(val label: String, val maxHeight: Int?) {
    BEST("Melhor", null),
    P1080("1080p", 1080),
    P720("720p", 720),
    P480("480p", 480),
    P360("360p", 360),
    ;

    companion object {
        fun fromName(name: String?): DownloadQuality =
            values().firstOrNull { it.name.equals(name, ignoreCase = true) } ?: BEST
    }
}

/**
 * Regras puras para escolher qual vídeo/legenda baixar. Ficam fora da camada Android
 * para poderem ser testadas sem emulador.
 */
object DownloadPlanner {
    private val resolution = Regex("""(\d{3,4})p""", RegexOption.IGNORE_CASE)
    private val videoExtensions = listOf("mp4", "mkv", "webm", "m4v", "mov", "avi")
    private val subtitleExtensions = listOf("vtt", "srt")

    /** O DownloadManager só consegue salvar um arquivo direto; HLS/DASH ficam de fora. */
    fun isDirectFile(url: String): Boolean {
        val clean = url.substringBefore('?').substringBefore('#').lowercase()
        if (!clean.startsWith("https://")) return false
        return videoExtensions.any { clean.endsWith(".$it") }
    }

    fun heightOf(quality: String): Int? =
        resolution.find(quality)?.groupValues?.get(1)?.toIntOrNull()

    fun choose(videos: List<VideoSource>, quality: DownloadQuality): VideoSource? {
        if (videos.isEmpty()) return null
        val direct = videos.filter { isDirectFile(it.url) }
        val pool = direct.ifEmpty { videos }
        val target = quality.maxHeight
        if (target == null) {
            return pool.sortedWith(
                compareByDescending<VideoSource> { isDirectFile(it.url) }
                    .thenByDescending { heightOf(it.quality) ?: 0 }
            ).firstOrNull()
        }
        val atMost = pool.filter { (heightOf(it.quality) ?: Int.MAX_VALUE) <= target }
        return atMost.maxByOrNull { heightOf(it.quality) ?: 0 }
            ?: pool.minByOrNull { heightOf(it.quality) ?: Int.MAX_VALUE }
            ?: pool.firstOrNull()
    }

    fun chooseSubtitle(subtitles: List<Subtitle>, language: String?): Subtitle? {
        if (language.isNullOrBlank()) return null
        val wanted = language.substringBefore('-')
        return subtitles.firstOrNull { it.language.equals(language, ignoreCase = true) }
            ?: subtitles.firstOrNull {
                it.language.substringBefore('-').equals(wanted, ignoreCase = true)
            }
    }

    fun extension(url: String, fallback: String = "mp4"): String {
        val clean = url.substringBefore('?').substringBefore('#').lowercase()
        return (videoExtensions + subtitleExtensions).firstOrNull { clean.endsWith(".$it") } ?: fallback
    }

    fun safeName(raw: String, max: Int = 45): String {
        val normalized = java.text.Normalizer.normalize(raw, java.text.Normalizer.Form.NFD)
            .replace(Regex("\\p{M}+"), "")
        val cleaned = normalized.replace(Regex("[^A-Za-z0-9_-]"), "_").trim('_').take(max)
        return cleaned.ifBlank { "video_" + Integer.toHexString(raw.hashCode()) }
    }

    fun fileName(title: String, episodeKey: String, ext: String): String =
        "AnimeHub_${safeName(title)}_E${episodeKey.replace('.', '_')}.$ext"
}
