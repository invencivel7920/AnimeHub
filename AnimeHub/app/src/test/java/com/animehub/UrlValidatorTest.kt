package com.animehub

import com.animehub.data.network.UrlValidator
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class UrlValidatorTest {
    @Test fun addsHttpsWhenMissing() {
        assertEquals("https://example.com/", UrlValidator.normalizeBase("example.com")?.toString())
    }

    @Test fun rejectsHttp() {
        assertFalse(UrlValidator.isSafeUrl("http://example.com/"))
        assertNull(UrlValidator.normalizeBase("http://example.com"))
    }

    @Test fun rejectsLocalAndPrivateTargets() {
        listOf(
            "https://localhost/", "https://127.0.0.1/", "https://192.168.0.1/", "https://10.0.0.5/",
            "https://172.16.3.4/", "https://169.254.169.254/", "https://[::1]/", "https://router.local/",
            "https://intranet/", "https://100.64.1.1/",
        ).forEach { assertFalse("deveria bloquear $it", UrlValidator.isSafeUrl(it)) }
    }

    @Test fun rejectsEmbeddedCredentials() {
        assertFalse(UrlValidator.isSafeUrl("https://user:pass@example.com/"))
    }

    @Test fun acceptsPublicHttps() {
        assertTrue(UrlValidator.isSafeUrl("https://example.com/path?q=1"))
        assertNotNull(UrlValidator.normalizeBase("  https://sub.example.org  "))
    }
}
