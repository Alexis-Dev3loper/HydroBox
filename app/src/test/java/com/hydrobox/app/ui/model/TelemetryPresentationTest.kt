package com.hydrobox.app.ui.model

import com.hydrobox.app.api.ApiMeasurement
import com.hydrobox.app.api.ApiSensor
import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.Duration
import java.time.Instant
import java.time.ZoneId
import java.util.Locale

class TelemetryPresentationTest {
    private val waterLevel = ApiSensor(
        sensorKey = "water_level",
        name = "Nivel del agua",
        unitSymbol = "cm",
        description = null,
        displayOrder = 1,
        active = true
    )

    @Test
    fun usesCatalogUnitAndKeepsZeroDifferentFromMissing() {
        assertEquals("0.0 cm", formatSensorReading(waterLevel, 0.0, Locale.US))
        assertEquals("Sin lectura", formatSensorReading(waterLevel, null, Locale.US))
        assertEquals(2, sensorDisplayPrecision("ph"))
        assertEquals(0, sensorDisplayPrecision("orp"))
    }

    @Test
    fun classifiesFreshStaleMissingAndClockSkewFromCapturedAt() {
        val now = Instant.parse("2026-09-16T12:10:00Z")
        val fresh = measurement("a", "2026-09-16T12:09:00Z", mapOf("water_level" to 0.0))
        val stale = measurement("b", "2026-09-16T12:00:00Z", mapOf("water_level" to 4.0))
        val missing = measurement("c", "2026-09-16T12:09:00Z", emptyMap())
        val future = measurement("d", "2026-09-16T12:11:00Z", mapOf("water_level" to 4.0))

        assertEquals(ReadingFreshness.FRESH, readingFreshness(fresh, "water_level", now))
        assertEquals(ReadingFreshness.STALE, readingFreshness(stale, "water_level", now))
        assertEquals(ReadingFreshness.MISSING, readingFreshness(missing, "water_level", now))
        assertEquals(ReadingFreshness.CLOCK_SKEW, readingFreshness(future, "water_level", now))
    }

    @Test
    fun formatsUtcInstantInRequestedLocalZoneWithoutChangingTheInstant() {
        val instant = Instant.parse("2026-09-16T12:30:00Z")

        assertEquals(
            "16/09/2026 06:30",
            formatLocalTimestamp(
                instant,
                ZoneId.of("America/Mexico_City"),
                Locale.US
            )
        )
    }

    @Test
    fun ordersSeriesByCapturedAtAndOmitsOnlyMissingValues() {
        val records = listOf(
            measurement("b", "2026-09-16T12:02:00Z", mapOf("ph" to 6.2)),
            measurement("c", "2026-09-16T12:01:00Z", emptyMap()),
            measurement("a", "2026-09-16T12:00:00Z", mapOf("ph" to 0.0))
        )

        val series = telemetrySeries(records, "ph")

        assertEquals(listOf(0.0, 6.2), series.map(TimedReading::value))
        assertEquals(listOf("a", "b"), series.map(TimedReading::eventUuid))
    }

    @Test(expected = IllegalArgumentException::class)
    fun rejectsNonPositiveFreshnessThreshold() {
        readingFreshness(
            measurement("a", "2026-09-16T12:00:00Z", mapOf("ph" to 6.0)),
            "ph",
            Instant.parse("2026-09-16T12:00:01Z"),
            Duration.ZERO
        )
    }

    private fun measurement(
        eventUuid: String,
        capturedAt: String,
        readings: Map<String, Double>
    ) = ApiMeasurement(
        eventUuid = eventUuid,
        deviceKey = "edge_primary",
        capturedAt = Instant.parse(capturedAt),
        ingestedAt = Instant.parse(capturedAt),
        cropKey = null,
        cycleUuid = null,
        readings = readings
    )
}
