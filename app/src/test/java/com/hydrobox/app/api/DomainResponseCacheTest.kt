package com.hydrobox.app.api

import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.nio.file.Files
import java.time.Instant

class DomainResponseCacheTest {
    @Test
    fun cacheSurvivesRestartAndKeepsScopesIsolated() = runBlocking {
        val directory = Files.createTempDirectory("hydrobox-mobile-cache").toFile()
        val file = directory.resolve("cache.json")
        try {
            JsonFileDomainResponseCache(file).write(
                "user-a:site-a",
                "catalogs/sensors",
                CachedDomainResponse("{\"data\":[]}", Instant.parse("2026-09-16T12:00:00Z"))
            )

            val restarted = JsonFileDomainResponseCache(file)
            assertEquals(
                "{\"data\":[]}",
                restarted.read("user-a:site-a", "catalogs/sensors")?.body
            )
            assertNull(restarted.read("user-b:site-a", "catalogs/sensors"))
            assertFalse(file.readText().contains("Bearer "))
            assertFalse(file.readText().contains("refresh_token"))
        } finally {
            directory.deleteRecursively()
        }
    }

    @Test
    fun corruptionIsDiscardedWithoutReturningInventedData() = runBlocking {
        val directory = Files.createTempDirectory("hydrobox-mobile-cache-corrupt").toFile()
        val file = directory.resolve("cache.json")
        try {
            file.writeText("not-json")
            val cache = JsonFileDomainResponseCache(file)

            assertNull(cache.read("user:site", "catalogs/sensors"))
            assertFalse(file.exists())
        } finally {
            directory.deleteRecursively()
        }
    }

    @Test
    fun entryLimitAndPrefixInvalidationAreDeterministic() = runBlocking {
        val directory = Files.createTempDirectory("hydrobox-mobile-cache-limit").toFile()
        val file = directory.resolve("cache.json")
        try {
            val cache = JsonFileDomainResponseCache(file, maximumEntries = 2)
            cache.write("user:site", "commands?limit=1", response("one", "2026-09-16T12:00:00Z"))
            cache.write("user:site", "commands?limit=2", response("two", "2026-09-16T12:00:01Z"))
            cache.write("user:site", "catalogs/sensors", response("three", "2026-09-16T12:00:02Z"))

            assertNull(cache.read("user:site", "commands?limit=1"))
            assertEquals("two", cache.read("user:site", "commands?limit=2")?.body)
            cache.removeByPrefix("user:site", setOf("commands"))
            assertNull(cache.read("user:site", "commands?limit=2"))
            assertTrue(cache.read("user:site", "catalogs/sensors") != null)
        } finally {
            directory.deleteRecursively()
        }
    }

    private fun response(body: String, storedAt: String) =
        CachedDomainResponse(body, Instant.parse(storedAt))
}
