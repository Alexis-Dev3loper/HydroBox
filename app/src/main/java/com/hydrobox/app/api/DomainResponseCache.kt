package com.hydrobox.app.api

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.io.IOException
import java.time.Duration
import java.time.Instant

data class CachedDomainResponse(
    val body: String,
    val storedAt: Instant
)

interface DomainResponseCache {
    suspend fun read(scope: String, key: String): CachedDomainResponse?
    suspend fun write(scope: String, key: String, response: CachedDomainResponse)
    suspend fun remove(scope: String, key: String)
    suspend fun removeByPrefix(scope: String, prefixes: Set<String>)
    suspend fun clearScope(scope: String)
}

object NoOpDomainResponseCache : DomainResponseCache {
    override suspend fun read(scope: String, key: String): CachedDomainResponse? = null
    override suspend fun write(scope: String, key: String, response: CachedDomainResponse) = Unit
    override suspend fun remove(scope: String, key: String) = Unit
    override suspend fun removeByPrefix(scope: String, prefixes: Set<String>) = Unit
    override suspend fun clearScope(scope: String) = Unit
}

data class DomainCachePolicy(
    val freshFor: Duration,
    val maximumOfflineAge: Duration
)

object DomainCachePolicies {
    private val catalog = DomainCachePolicy(Duration.ofHours(24), Duration.ofDays(7))
    private val telemetry = DomainCachePolicy(Duration.ofMinutes(5), Duration.ofHours(24))
    private val dynamic = DomainCachePolicy(Duration.ofSeconds(30), Duration.ofHours(24))

    fun forPath(path: String): DomainCachePolicy = when {
        path.startsWith("catalogs/") -> catalog
        path.startsWith("telemetry/") -> telemetry
        else -> dynamic
    }
}

class JsonFileDomainResponseCache(
    private val file: File,
    private val maximumEntries: Int = 128
) : DomainResponseCache {
    private val mutex = Mutex()

    init {
        require(maximumEntries > 0) { "maximumEntries must be positive" }
    }

    override suspend fun read(scope: String, key: String): CachedDomainResponse? = ioLocked {
        load()[entryId(scope, key)]?.response
    }

    override suspend fun write(
        scope: String,
        key: String,
        response: CachedDomainResponse
    ) = ioLocked {
        val entries = load()
        entries[entryId(scope, key)] = StoredEntry(scope, key, response)
        val retained = entries.values
            .sortedByDescending { it.response.storedAt }
            .take(maximumEntries)
        persist(retained)
    }

    override suspend fun remove(scope: String, key: String) = ioLocked {
        val entries = load()
        if (entries.remove(entryId(scope, key)) != null) persist(entries.values)
    }

    override suspend fun removeByPrefix(scope: String, prefixes: Set<String>) = ioLocked {
        if (prefixes.isEmpty()) return@ioLocked
        val entries = load()
        val changed = entries.entries.removeAll { (_, value) ->
            value.scope == scope && prefixes.any { prefix -> value.key.startsWith(prefix) }
        }
        if (changed) persist(entries.values)
    }

    override suspend fun clearScope(scope: String) = ioLocked {
        val entries = load()
        val changed = entries.entries.removeAll { (_, value) -> value.scope == scope }
        if (changed) persist(entries.values)
    }

    private suspend fun <T> ioLocked(block: () -> T): T = withContext(Dispatchers.IO) {
        mutex.withLock { block() }
    }

    private fun load(): MutableMap<String, StoredEntry> {
        if (!file.isFile) return linkedMapOf()
        return try {
            val root = JSONObject(file.readText(Charsets.UTF_8))
            if (root.getInt("version") != FORMAT_VERSION) throw IOException("Unsupported cache format")
            val entries = linkedMapOf<String, StoredEntry>()
            val values = root.getJSONArray("entries")
            repeat(values.length()) { index ->
                val value = values.getJSONObject(index)
                val scope = value.getString("scope").requireCachePart()
                val key = value.getString("key").requireCachePart()
                val response = CachedDomainResponse(
                    body = value.getString("body"),
                    storedAt = Instant.parse(value.getString("stored_at"))
                )
                entries[entryId(scope, key)] = StoredEntry(scope, key, response)
            }
            entries
        } catch (_: Exception) {
            file.delete()
            linkedMapOf()
        }
    }

    private fun persist(entries: Collection<StoredEntry>) {
        file.parentFile?.mkdirs()
        if (entries.isEmpty()) {
            file.delete()
            return
        }
        val values = JSONArray()
        entries.forEach { entry ->
            values.put(JSONObject().apply {
                put("scope", entry.scope)
                put("key", entry.key)
                put("stored_at", entry.response.storedAt.toString())
                put("body", entry.response.body)
            })
        }
        val temp = File(file.parentFile, "${file.name}.tmp")
        temp.writeText(
            JSONObject().apply {
                put("version", FORMAT_VERSION)
                put("entries", values)
            }.toString(),
            Charsets.UTF_8
        )
        if (file.exists() && !file.delete()) {
            temp.delete()
            throw IOException("Unable to replace domain cache")
        }
        if (!temp.renameTo(file)) {
            temp.delete()
            throw IOException("Unable to publish domain cache")
        }
    }

    private fun entryId(scope: String, key: String): String =
        "${scope.requireCachePart()}\u0000${key.requireCachePart()}"

    private fun String.requireCachePart(): String = apply {
        require(isNotBlank() && !contains('\u0000')) { "Invalid cache key" }
    }

    private data class StoredEntry(
        val scope: String,
        val key: String,
        val response: CachedDomainResponse
    )

    private companion object {
        const val FORMAT_VERSION = 1
    }
}
