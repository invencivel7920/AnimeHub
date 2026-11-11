package com.animehub

import com.animehub.domain.matching.TitleMatcher
import com.animehub.domain.model.Anime
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class TitleMatcherTest {
    @Test fun ignoresAccentsCaseAndLabels() {
        assertTrue(TitleMatcher.isSame("Naruto Shippūden Dublado", "naruto shippuden"))
        assertTrue(TitleMatcher.isSame("Boku no Hero Academia (2016)", "Boku no Hero Academia - Legendado"))
    }

    @Test fun neverMergesDifferentSeasonsOrFormats() {
        assertFalse(TitleMatcher.isSame("Mob Psycho 100", "Mob Psycho 100 II"))
        assertFalse(TitleMatcher.isSame("Attack on Titan", "Attack on Titan Movie"))
        assertFalse(TitleMatcher.isSame("Kaguya-sama 2", "Kaguya-sama 3"))
    }

    @Test fun toleratesSmallTypos() {
        assertTrue(TitleMatcher.isSame("Fullmetal Alchemist Brotherhood", "Fullmetal Alchemist Brotherhod"))
    }

    @Test fun groupsAcrossSourcesButNotWithinOne() {
        val items = listOf(
            Anime("a", "1", "Sintel"),
            Anime("b", "x", "Sintel Dublado"),
            Anime("a", "2", "Sintel"),
            Anime("b", "y", "Big Buck Bunny"),
        )
        val groups = TitleMatcher.group(items)
        assertEquals(3, groups.size)
        assertEquals(2, groups.first().entries.size)
        assertEquals(setOf("a", "b"), groups.first().entries.map { it.sourceId }.toSet())
    }
}
