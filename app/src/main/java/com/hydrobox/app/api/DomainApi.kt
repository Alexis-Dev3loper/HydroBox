package com.hydrobox.app.api

import java.time.Instant

data class DomainApiContext(
    val accessToken: String,
    val siteKey: String
)

object DomainApiContextResolver {
    fun resolve(accessToken: String?, siteKeys: List<String>): DomainApiContext {
        if (accessToken.isNullOrBlank()) {
            throw DomainApiException(ApiProblem(401, "auth.session_required"))
        }
        val distinctSites = siteKeys.distinct()
        val siteKey = when (distinctSites.size) {
            1 -> distinctSites.single()
            0 -> throw DomainApiException(ApiProblem(403, "authorization.site_required"))
            else -> throw DomainApiException(ApiProblem(409, "mobile.site_selection_required"))
        }
        return DomainApiContext(accessToken, siteKey)
    }
}

data class ApiProblem(
    val status: Int?,
    val code: String,
    val detail: String? = null,
    val requestId: String? = null,
    val transient: Boolean = false
)

class DomainApiException(val problem: ApiProblem) :
    Exception("Domain API request failed: ${problem.code}")

data class ApiSensor(
    val sensorKey: String,
    val name: String,
    val unitSymbol: String,
    val description: String?,
    val displayOrder: Int,
    val active: Boolean
)

data class ApiActuatorState(
    val desiredState: Boolean,
    val reportedState: Boolean?,
    val availabilityKey: String,
    val desiredChangedAt: Instant?,
    val reportedAt: Instant?,
    val lastSeenAt: Instant?
)

data class ApiActuator(
    val actuatorKey: String,
    val name: String,
    val actuatorType: String,
    val safeState: Boolean,
    val active: Boolean,
    val state: ApiActuatorState
)

data class ApiNutrient(
    val nutrientKey: String,
    val name: String,
    val dosingActuatorKey: String,
    val description: String?,
    val active: Boolean
)

data class ApiCrop(
    val cropKey: String,
    val name: String,
    val description: String?,
    val defaultCycleDays: Int?,
    val active: Boolean
)

data class ApiSensorRange(
    val sensorKey: String,
    val unitSymbol: String,
    val minValue: Double,
    val maxValue: Double
)

data class ApiCycle(
    val cycleUuid: String,
    val cropKey: String,
    val startedAt: Instant?,
    val endedAt: Instant?,
    val plannedDurationDays: Int?,
    val active: Boolean,
    val source: String,
    val createdAt: Instant
)

data class ApiMeasurement(
    val eventUuid: String,
    val deviceKey: String,
    val capturedAt: Instant,
    val ingestedAt: Instant,
    val cropKey: String?,
    val cycleUuid: String?,
    val readings: Map<String, Double>
)

data class ApiPhysicalEvidence(
    val acceptedAt: Instant?,
    val completedAt: Instant?,
    val completionBasis: String?,
    val reportedState: Boolean?
)

data class ApiCommand(
    val commandUuid: String,
    val actuatorKey: String,
    val commandKey: String,
    val targetState: Boolean?,
    val durationMs: Long?,
    val statusKey: String,
    val requestedAt: Instant,
    val sentAt: Instant?,
    val acknowledgedAt: Instant?,
    val failedAt: Instant?,
    val expiresAt: Instant,
    val errorCode: String?,
    val errorMessage: String?,
    val physicalEvidence: ApiPhysicalEvidence
)

data class ApiDosingRequest(
    val requestUuid: String,
    val nutrientKey: String,
    val actuatorKey: String,
    val amountMl: Double,
    val statusKey: String,
    val commandUuid: String?,
    val applicationUuid: String?,
    val requestedAt: Instant,
    val expiresAt: Instant,
    val correlatedAt: Instant?,
    val completedAt: Instant?,
    val failedAt: Instant?,
    val errorMessage: String?,
    val updatedAt: Instant
)

data class ApiPage<T>(
    val items: List<T>,
    val hasMore: Boolean,
    val nextCursor: String?
)

interface HydroDomainApi {
    suspend fun sensors(): List<ApiSensor>
    suspend fun actuators(): List<ApiActuator>
    suspend fun nutrients(): List<ApiNutrient>
    suspend fun crops(): List<ApiCrop>
    suspend fun sensorRanges(cropKey: String): List<ApiSensorRange>
    suspend fun activeCycle(): ApiCycle?
    suspend fun changeActiveCrop(cropKey: String, plannedDurationDays: Int?): ApiCycle
    suspend fun measurements(
        limit: Int = 100,
        cursor: String? = null,
        capturedFrom: Instant? = null,
        capturedBefore: Instant? = null
    ): ApiPage<ApiMeasurement>
    suspend fun commands(limit: Int = 100, cursor: String? = null): ApiPage<ApiCommand>
    suspend fun command(commandUuid: String): ApiCommand
    suspend fun createSetStateCommand(actuatorKey: String, targetState: Boolean): ApiCommand
    suspend fun dosingRequest(requestUuid: String): ApiDosingRequest
    suspend fun createDosingRequest(nutrientKey: String, amountMl: Double): ApiDosingRequest
}

object HydroApiContract {
    val sensorKeys: Set<String> = setOf(
        "ph",
        "water_temperature",
        "orp",
        "water_level",
        "air_temperature",
        "air_humidity"
    )

    val cropKeys: Set<String> = setOf(
        "lettuce",
        "spinach",
        "chard",
        "arugula",
        "basil",
        "mustard"
    )

    val actuatorKeys: Set<String> = setOf(
        "flora_grow_pump",
        "flora_micro_pump",
        "flora_bloom_pump",
        "water_pump",
        "led_lamp",
        "fan"
    )

    val nutrientKeys: Set<String> = setOf(
        "flora_grow",
        "flora_micro",
        "flora_bloom"
    )

    val availabilityKeys: Set<String> = setOf("unknown", "online", "offline")
    val commandKeys: Set<String> = setOf("set_state", "run_for")
    val commandStatusKeys: Set<String> = setOf("pending", "sent", "acknowledged", "failed", "expired")
    val dosingStatusKeys: Set<String> = setOf("pending", "command_created", "completed", "failed", "expired")
    val completionBases: Set<String> = setOf("output_applied", "feedback_verified", "safe_state_applied")
}
