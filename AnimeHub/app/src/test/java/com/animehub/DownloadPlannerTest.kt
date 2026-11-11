package com.animehub

import com.animehub.domain.download.DownloadPlanner
import com.animehub.domain.download.DownloadQuality
import com.animehub.domain.model.Subtitle
import com.animehub.domain.model.VideoSource
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class DownloadPlannerTest {
    @Test
    fun onlyDirectHttpsFilesAreDownloadable() {
        assertTrue(DownloadPlanner.isDirectFile("https://cdn.example.com/ep1.mp4?token=1"))
        assertTrue(DownloadPlanner.isDirectFile("https://cdn.example.com/ep1.MKV"))
        assertFalse(DownloadPlanner.isDirectFile("https://cdn.example.com/index.m3u8"))
        assertFalse(DownloadPlanner.isDirectFile("http://cdn.example.com/ep1.mp4"))
    }

    @Test
    fun bestQualityPrefersHighestDirectFile() {
        val videos = listOf(
            VideoSource("https://x/360p.mp4", "360p"),
            VideoSource("https://x/1080p.mp4", "1080p"),
            VideoSource("https://x/index.m3u8", "HLS (auto)"),
        )
        assertEquals("https://x/1080p.mp4", DownloadPlanner.choose(videos, DownloadQuality.BEST)?.url)
    }

    @Test
    fun cappedQualityPicksClosestBelowTarget() {
        val videos = listOf(
            VideoSource("https://x/360p.mp4", "360p"),
            VideoSource("https://x/720p.mp4", "720p"),
            VideoSource("https://x/1080p.mp4", "1080p"),
        )
        assertEquals("https://x/720p.mp4", DownloadPlanner.choose(videos, DownloadQuality.P720)?.url)
    }

    @Test
    fun hlsIsUsedOnlyWhenNoDirectFileExists() {
        val hlsOnly = listOf(VideoSource("https://x/index.m3u8", "HLS (auto)"))
        assertEquals("https://x/index.m3u8", DownloadPlanner.choose(hlsOnly, DownloadQuality.BEST)?.url)
    }

    @Test
    fun subtitleMatchesLanguagePrefix() {
        val subs = listOf(Subtitle("https://x/pt.vtt", "pt-BR"), Subtitle("https://x/en.vtt", "en"))
        assertEquals("https://x/pt.vtt", DownloadPlanner.chooseSubtitle(subs, "pt")?.url)
        assertEquals("https://x/pt.vtt", DownloadPlanner.chooseSubtitle(subs, "pt-BR")?.url)
        assertEquals(
            "https://x/pt.vtt",
            DownloadPlanner.chooseSubtitle(listOf(Subtitle("https://x/pt.vtt", "pt")), "pt-BR")?.url,
        )
        assertNull(DownloadPlanner.chooseSubtitle(subs, null))
    }

    @Test
    fun fileNamesAreSafe() {
        assertEquals("Obra_Segura", DownloadPlanner.safeName("Obra Segura"))
        assertEquals("Acao", DownloadPlanner.safeName("Ação"))
        assertTrue(DownloadPlanner.safeName("日本語").startsWith("video_"))
        assertEquals("AnimeHub_Obra_E1_5.mp4", DownloadPlanner.fileName("Obra", "1.5", "mp4"))
    }

    @Test
    fun extensionUsesUrlOrFallback() {
        assertEquals("mp4", DownloadPlanner.extension("https://x/ep1.MP4?token=1"))
        assertEquals("vtt", DownloadPlanner.extension("https://x/legenda?id=1", "vtt"))
        assertEquals("mp4", DownloadPlanner.extension("https://x/video?id=1"))
    }
}
