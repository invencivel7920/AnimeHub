package com.animehub

import com.animehub.data.scraper.TtlCache
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class TtlCacheTest {
    @Test fun expiresAfterTtl() {
        var t = 0L
        val cache = TtlCache<String, Int>(maxSize = 10, ttlMs = 100, now = { t })
        cache.put("a", 1)
        t = 99
        assertEquals(1, cache.get("a"))
        t = 101
        assertNull(cache.get("a"))
    }

    @Test fun evictsLeastRecentlyUsed() {
        val cache = TtlCache<String, Int>(maxSize = 2, ttlMs = 1_000, now = { 0L })
        cache.put("a", 1)
        cache.put("b", 2)
        cache.get("a") // "b" passa a ser o menos usado
        cache.put("c", 3)
        assertEquals(1, cache.get("a"))
        assertNull(cache.get("b"))
        assertEquals(3, cache.get("c"))
    }

    @Test fun clearEmptiesCache() {
        val cache = TtlCache<String, Int>(maxSize = 2, ttlMs = 1_000, now = { 0L })
        cache.put("a", 1)
        cache.clear()
        assertNull(cache.get("a"))
    }
}
