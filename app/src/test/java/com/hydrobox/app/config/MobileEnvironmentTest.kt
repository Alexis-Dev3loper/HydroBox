package com.hydrobox.app.config

import org.junit.Assert.assertEquals
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
}
