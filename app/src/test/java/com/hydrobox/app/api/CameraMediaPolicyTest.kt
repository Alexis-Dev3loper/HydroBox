package com.hydrobox.app.api

import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test

class CameraMediaPolicyTest {
    @Test
    fun canonicalizesHttpsOriginsWithoutChangingTheirEffectivePort() {
        assertEquals("https://media.example.test", CameraMediaPolicy.origin("HTTPS://MEDIA.EXAMPLE.TEST:443/whep/1"))
        assertEquals("https://media.example.test:8443", CameraMediaPolicy.origin("https://media.example.test:8443/whep/1"))
        assertEquals("https://[::1]:8443", CameraMediaPolicy.origin("https://[::1]:8443/whep/1"))
    }

    @Test
    fun rejectsUnsafeSessionUrlsBeforeRenderingAnyToken() {
        listOf(
            "http://media.example.test/whep", "//media.example.test/whep",
            "https://user:pass@media.example.test/whep", "https://media.example.test?token=x",
            "https://media.example.test?", "https://media.example.test#", "https://media.example.test#fragment",
            "https://media.example.test:0/whep", "https://media.example.test:65536/whep",
            "https://media.example.test\\@evil.invalid/whep", "https://media.example.test\n",
            "https://media.example.test/" + "x".repeat(2048)
        ).forEach { url ->
            assertThrows(IllegalArgumentException::class.java) { CameraMediaPolicy.origin(url) }
        }
    }

    @Test
    fun rejectsUnboundedOrHeaderInjectedTokens() {
        listOf("short", "x".repeat(8193), "x".repeat(40) + "\r\nInjected: value", "x".repeat(40) + " ",
            "x".repeat(40) + "é").forEach { token ->
            assertThrows(IllegalArgumentException::class.java) { CameraMediaPolicy.validateToken(token) }
        }
        CameraMediaPolicy.validateToken("synthetic-media-token-for-unit-tests")
    }
}
