package com.hydrobox.app.config

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class MobileEnvironmentTest {
    @Test
    fun apiEnvironmentAcceptsAbsoluteHttpsUrl() {
        val environment = ApiEnvironment("https://api.example.invalid/api/v1")

        assertEquals("https://api.example.invalid/api/v1", environment.baseUrl)
    }

    @Test(expected = IllegalArgumentException::class)
    fun apiEnvironmentRejectsCleartextUrl() {
        ApiEnvironment("http://api.example.invalid/api/v1")
    }

    @Test(expected = IllegalArgumentException::class)
    fun apiEnvironmentRejectsRelativeUrl() {
        ApiEnvironment("/api/v1")
    }

    @Test
    fun disabledLegacyMqttAllowsInertPlaceholder() {
        val environment = LegacyMqttEnvironment(
            enabled = false,
            host = "",
            port = 1883,
            username = null,
            password = null
        )

        assertEquals(false, environment.enabled)
        assertNull(environment.password)
    }

    @Test(expected = IllegalArgumentException::class)
    fun enabledLegacyMqttRequiresHost() {
        LegacyMqttEnvironment(
            enabled = true,
            host = "",
            port = 1883,
            username = null,
            password = null
        )
    }

    @Test(expected = IllegalArgumentException::class)
    fun legacyMqttRejectsInvalidPort() {
        LegacyMqttEnvironment(
            enabled = false,
            host = "",
            port = 0,
            username = null,
            password = null
        )
    }
}
