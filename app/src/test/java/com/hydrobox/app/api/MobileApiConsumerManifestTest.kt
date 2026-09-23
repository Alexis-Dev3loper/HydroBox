package com.hydrobox.app.api

import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

class MobileApiConsumerManifestTest {
    private data class ExpectedOperation(
        val method: String,
        val path: String,
        val successStatus: Int,
        val scopes: List<String>,
        val sourceFile: String,
        val sourceAnchor: String
    )

    @Test
    fun manifestMatchesTheCompleteImplementedMobileApiSurface() {
        val root = File("").absoluteFile
        val manifest = JSONObject(manifestFile(root).readText())
        val actual = manifest.getJSONArray("operations").let { operations ->
            (0 until operations.length()).associate { index ->
                val operation = operations.getJSONObject(index)
                operation.getString("id") to operation
            }
        }

        assertEquals("hydrobox.mobile-api-consumer.v1", manifest.getString("schema"))
        assertEquals("1", manifest.getString("api_version"))
        assertEquals("/api/v1", manifest.getString("base_path"))
        assertEquals(EXPECTED.keys, actual.keys)

        EXPECTED.forEach { (id, expected) ->
            val operation = actual.getValue(id)
            assertEquals("$id method", expected.method, operation.getString("method"))
            assertEquals("$id path", expected.path, operation.getString("path"))
            assertEquals("$id status", expected.successStatus, operation.getInt("success_status"))
            assertEquals(
                "$id scopes",
                expected.scopes,
                operation.getJSONArray("scopes").let { scopes ->
                    (0 until scopes.length()).map(scopes::getString)
                }
            )

            val source = File(root, expected.sourceFile).readText()
            assertTrue("$id source anchor missing", source.contains(expected.sourceAnchor))
        }
    }

    @Test
    fun manifestDoesNotClaimDomainsThatAreStillUnavailable() {
        val text = manifestFile(File("").absoluteFile).readText()

        assertTrue("alerts must remain outside the implemented consumer manifest", !text.contains("alert."))
        assertTrue("physical health must remain outside the manifest", !text.contains("health."))
        assertTrue("camera must remain outside the manifest", !text.contains("camera."))
    }

    companion object {
        private fun manifestFile(moduleRoot: File) =
            File(moduleRoot, "../docs/api/v1/mobile_v1_operations.json").canonicalFile

        private const val AUTH_SOURCE =
            "src/main/java/com/hydrobox/app/auth/session/HttpHumanAuthApi.kt"
        private const val DOMAIN_SOURCE =
            "src/main/java/com/hydrobox/app/api/HttpHydroDomainApi.kt"

        private fun auth(
            method: String,
            path: String,
            status: Int,
            scopes: List<String>,
            anchor: String
        ) = ExpectedOperation(method, path, status, scopes, AUTH_SOURCE, anchor)

        private fun domain(
            method: String,
            path: String,
            status: Int,
            scopes: List<String>,
            anchor: String
        ) = ExpectedOperation(method, path, status, scopes, DOMAIN_SOURCE, anchor)

        private val EXPECTED = linkedMapOf(
            "auth.issue_token" to auth("POST", "/api/v1/auth/token", 200, emptyList(), "path = \"auth/token\""),
            "auth.refresh_token" to auth("POST", "/api/v1/auth/refresh", 200, emptyList(), "path = \"auth/refresh\""),
            "auth.logout" to auth("POST", "/api/v1/auth/logout", 204, listOf("profile:read"), "request(\"POST\", \"auth/logout\""),
            "auth.me" to auth("GET", "/api/v1/me", 200, listOf("profile:read"), "request(\"GET\", \"me\""),
            "catalog.sensors" to domain("GET", "/api/v1/sites/{site_key}/catalogs/sensors", 200, listOf("catalog:read"), "list(\"catalogs/sensors\")"),
            "catalog.actuators" to domain("GET", "/api/v1/sites/{site_key}/catalogs/actuators", 200, listOf("catalog:read"), "list(\"catalogs/actuators\")"),
            "catalog.nutrients" to domain("GET", "/api/v1/sites/{site_key}/catalogs/nutrients", 200, listOf("catalog:read"), "list(\"catalogs/nutrients\")"),
            "catalog.crops" to domain("GET", "/api/v1/sites/{site_key}/catalogs/crops", 200, listOf("catalog:read"), "list(\"catalogs/crops\")"),
            "catalog.crop_sensor_ranges" to domain("GET", "/api/v1/sites/{site_key}/catalogs/crops/{crop_key}/sensor-ranges", 200, listOf("catalog:read"), "catalogs/crops/\$cropKey/sensor-ranges"),
            "cycle.list" to domain("GET", "/api/v1/sites/{site_key}/cycles", 200, listOf("cycle:read"), "cycles?active=true&limit=2"),
            "cycle.create" to domain("POST", "/api/v1/sites/{site_key}/cycles", 201, listOf("cycle:write"), "path = \"cycles\""),
            "cycle.update" to domain("PATCH", "/api/v1/sites/{site_key}/cycles/{cycle_uuid}", 200, listOf("cycle:write"), "path = \"cycles/\${current.cycleUuid}\""),
            "telemetry.measurements" to domain("GET", "/api/v1/sites/{site_key}/telemetry/measurements", 200, listOf("telemetry:read"), "telemetry/measurements?\$query"),
            "command.list" to domain("GET", "/api/v1/sites/{site_key}/commands", 200, listOf("command:read"), "commands?\$query"),
            "command.show" to domain("GET", "/api/v1/sites/{site_key}/commands/{command_uuid}", 200, listOf("command:read"), "commands/\${requireUuid(commandUuid)}"),
            "command.create" to domain("POST", "/api/v1/sites/{site_key}/commands", 202, listOf("command:write"), "path = \"commands\""),
            "dosing.show" to domain("GET", "/api/v1/sites/{site_key}/dosing-requests/{request_uuid}", 200, listOf("dosing:read"), "dosing-requests/\${requireUuid(requestUuid)}"),
            "dosing.create" to domain("POST", "/api/v1/sites/{site_key}/dosing-requests", 202, listOf("dosing:write"), "path = \"dosing-requests\""),
            "automation.list" to domain("GET", "/api/v1/sites/{site_key}/automations", 200, listOf("automation:read"), "pagedPath(\"automations\", limit, cursor)"),
            "automation.show" to domain("GET", "/api/v1/sites/{site_key}/automations/{rule_uuid}", 200, listOf("automation:read"), "override suspend fun automation(ruleUuid"),
            "automation.create" to domain("POST", "/api/v1/sites/{site_key}/automations", 201, listOf("automation:write"), "override suspend fun createAutomation("),
            "automation.update" to domain("PATCH", "/api/v1/sites/{site_key}/automations/{rule_uuid}", 200, listOf("automation:write"), "override suspend fun updateAutomation("),
            "automation.delete" to domain("DELETE", "/api/v1/sites/{site_key}/automations/{rule_uuid}", 204, listOf("automation:write"), "override suspend fun deleteAutomation("),
            "automation.executions" to domain("GET", "/api/v1/sites/{site_key}/automation-executions", 200, listOf("automation:read"), "pagedPath(\"automation-executions\", limit, cursor)")
        )
    }
}
