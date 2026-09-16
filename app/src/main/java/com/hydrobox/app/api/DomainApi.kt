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

data class ApiPage<T>(
    val items: List<T>,
    val hasMore: Boolean,
    val nextCursor: String?
)

interface HydroDomainApi {
    suspend fun sensors(): List<ApiSensor>
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
}
