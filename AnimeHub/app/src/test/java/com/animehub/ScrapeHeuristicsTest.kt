package com.animehub

import com.animehub.data.scraper.ScrapeHeuristics
import org.jsoup.Jsoup
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ScrapeHeuristicsTest {
    private val home = """
        <html><body>
          <nav><a href="/login">Entrar</a><a href="/category/acao"><img src="/i.png" alt="x"></a></nav>
          <div><a href="/anime/one-piece"><img data-src="/c/op.jpg" alt="One Piece"></a></div>
          <div><a href="/anime/naruto" title="Naruto"><img src="/c/n.jpg"></a></div>
          <a href="https://outro-site.com/anime/x"><img src="/z.jpg" alt="Externo"></a>
          <a href="http://example.com/anime/y"><img src="/z.jpg" alt="Inseguro"></a>
        </body></html>
    """.trimIndent()

    @Test fun extractsOnlySafeSameHostCards() {
        val doc = Jsoup.parse(home, "https://example.com/")
        val cards = ScrapeHeuristics.extractCards(doc, "s", null)
        assertEquals(listOf("One Piece", "Naruto"), cards.map { it.title })
        assertEquals("https://example.com/c/op.jpg", cards[0].coverUrl)
    }

    @Test fun customSelectorTakesPriority() {
        val doc = Jsoup.parse(home, "https://example.com/")
        val cards = ScrapeHeuristics.extractCards(doc, "s", "a[href*=naruto]")
        assertEquals(1, cards.size)
    }

    @Test fun extractsAndSortsEpisodes() {
        val html = """
            <ul>
              <li><a href="/ep/one-piece-episodio-3">Episódio 3</a></li>
              <li><a href="/ep/one-piece-episodio-1">Episódio 1</a></li>
              <li><a href="/ep/one-piece-episodio-2">Episódio 2</a></li>
              <li><a href="/ep/bleach-episodio-9">Episódio 9</a></li>
              <li><a href="/sobre">Sobre</a></li>
            </ul>
        """.trimIndent()
        val doc = Jsoup.parse(html, "https://example.com/anime/one-piece")
        val eps = ScrapeHeuristics.extractEpisodes(doc, "https://example.com/anime/one-piece", null)
        assertEquals(listOf(1f, 2f, 3f), eps.map { it.number })
    }

    @Test fun findsMediaAndSubtitlesAndDropsUnsafe() {
        val html = """
            <video><source src="https://cdn.example.com/v/720p.mp4"></video>
            <script>var u = "https:\/\/cdn.example.com\/hls\/master.m3u8?t=1"; var bad="http://x.com/a.mp4";
            var s = "https://cdn.example.com/s/pt.vtt";</script>
        """.trimIndent()
        val media = ScrapeHeuristics.extractMedia(Jsoup.parse(html, "https://example.com/ep/1"))
        assertTrue(media.urls.any { it.endsWith("master.m3u8?t=1") })
        assertTrue(media.urls.any { it.endsWith("720p.mp4") })
        assertTrue(media.urls.none { it.startsWith("http://") })
        assertEquals(1, media.subtitles.size)
    }

    @Test fun labelsQuality() {
        assertEquals("720p", ScrapeHeuristics.qualityLabel("https://a.com/720p.mp4"))
        assertEquals("HLS (auto)", ScrapeHeuristics.qualityLabel("https://a.com/master.m3u8"))
    }
}
