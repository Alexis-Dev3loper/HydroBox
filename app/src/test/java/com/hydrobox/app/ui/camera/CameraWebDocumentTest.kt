package com.hydrobox.app.ui.camera

import com.hydrobox.app.api.ApiCameraSession
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Instant

class CameraWebDocumentTest {
    private val session = ApiCameraSession(
        sessionUuid = "bbbbbbbb-bbbb-4bbb-8bbb-bbbbbbbbbbbb",
        siteKey = "university-lab",
        playbackUrl = "https://media.example.test:8443/whep/sites/university-lab/session",
        accessToken = "synthetic-ephemeral-camera-bearer-token",
        expiresAt = Instant.parse("2026-09-26T12:05:00Z")
    )

    @Test
    fun rendersAnIsolatedWhepReceiverWithoutPersistentBrowserStorage() {
        val document = CameraWebDocument.render(session)

        assertEquals("https://media.example.test:8443/mobile-camera/", CameraWebDocument.relayOrigin(session.playbackUrl))
        assertTrue(document.contains("RTCPeerConnection"))
        assertTrue(document.contains("method:'DELETE'"))
        assertTrue(document.contains("cache:'no-store'"))
        assertTrue(document.contains("connect-src https://media.example.test:8443;"))
        assertFalse(document.contains("connect-src https:;"))
        assertFalse(document.contains("localStorage"))
        assertFalse(document.contains("sessionStorage"))
        assertFalse(document.contains("document.cookie"))
        assertFalse(document.contains("console."))
    }

    @Test
    fun rejectsNonHttpsRelayOrigins() {
        assertThrows(IllegalArgumentException::class.java) {
            CameraWebDocument.relayOrigin("http://media.example.test/whep/session")
        }
    }
}
