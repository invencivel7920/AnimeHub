package com.animehub.domain.matching

import com.animehub.domain.model.Anime
import com.animehub.domain.model.Work
import java.text.Normalizer

/**
 * Decide se dois títulos de fontes diferentes são a mesma obra.
 * Ignora acentos, pontuação, ano e rótulos como "dublado/legendado", mas nunca
 * junta temporadas/partes diferentes (números, algarismos romanos, filme, OVA...).
 */
object TitleMatcher {
    private val diacritics = Regex("""\p{Mn}+""")
    private val nonAlnum = Regex("""[^a-z0-9 ]""")
    private val year = Regex("""(19|20)\d\d""")

    private val noise = setOf(
        "dublado", "legendado", "dub", "sub", "todos", "episodios", "episodes", "online",
        "hd", "assistir", "completo", "anime", "animes", "gratis", "download",
    )
    private val markers = setOf(
        "ii", "iii", "iv", "v", "vi", "vii", "viii", "ix", "x",
        "movie", "filme", "ova", "oad", "special", "especial", "final", "part", "parte",
    )

    fun normalize(title: String): String {
        val folded = Normalizer.normalize(title.lowercase(), Normalizer.Form.NFD).replace(diacritics, "")
        return folded.replace(nonAlnum, " ")
            .split(' ')
            .filter { it.isNotBlank() && it !in noise && !year.matches(it) }
            .joinToString(" ")
    }

    fun isSame(a: String, b: String): Boolean {
        val na = normalize(a)
        val nb = normalize(b)
        if (na.isEmpty() || nb.isEmpty()) return false
        if (na == nb) return true
        if (distinguishing(na) != distinguishing(nb)) return false
        return similarity(na, nb) >= 0.88
    }

    /** Agrupa resultados de várias fontes; nunca coloca duas entradas da mesma fonte no mesmo grupo. */
    fun group(items: List<Anime>): List<Work> {
        class Group(val title: String, val entries: MutableList<Anime>)

        val groups = mutableListOf<Group>()
        for (anime in items) {
            val target = groups.firstOrNull { g ->
                g.entries.none { it.sourceId == anime.sourceId } && isSame(g.title, anime.title)
            }
            if (target != null) target.entries += anime else groups += Group(anime.title, mutableListOf(anime))
        }
        return groups
            .map { g ->
                Work(
                    key = normalize(g.title).ifBlank { g.title.lowercase() },
                    title = g.title,
                    coverUrl = g.entries.firstNotNullOfOrNull { it.coverUrl },
                    entries = g.entries.toList(),
                )
            }
            .sortedByDescending { it.entries.size }
    }

    private fun distinguishing(normalized: String): Set<String> =
        normalized.split(' ').filter { t -> t in markers || (t.isNotEmpty() && t.all { it.isDigit() }) }.toSet()

    private fun similarity(a: String, b: String): Double {
        val max = maxOf(a.length, b.length)
        if (max == 0) return 1.0
        return 1.0 - levenshtein(a, b).toDouble() / max
    }

    private fun levenshtein(a: String, b: String): Int {
        var prev = IntArray(b.length + 1) { it }
        var cur = IntArray(b.length + 1)
        for (i in 1..a.length) {
            cur[0] = i
            for (j in 1..b.length) {
                val cost = if (a[i - 1] == b[j - 1]) 0 else 1
                cur[j] = minOf(cur[j - 1] + 1, prev[j] + 1, prev[j - 1] + cost)
            }
            val t = prev; prev = cur; cur = t
        }
        return prev[b.length]
    }
}
