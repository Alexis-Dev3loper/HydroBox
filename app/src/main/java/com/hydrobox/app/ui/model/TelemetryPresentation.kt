package com.hydrobox.app.ui.model

import com.hydrobox.app.api.ApiMeasurement
import com.hydrobox.app.api.ApiSensor
import java.time.Duration
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

enum class ReadingFreshness {
    MISSING,
    FRESH,
    STALE,
    CLOCK_SKEW
}

data class TimedReading(
    val eventUuid: String,
    val capturedAt: Instant,
    val value: Double
)

val mobileStaleAfter: Duration = Duration.ofMinutes(5)

fun sensorDisplayPrecision(sensorKey: String): Int = when (sensorKey) {
    "ph" -> 2
    "orp" -> 0
    else -> 1
}

fun formatSensorReading(
    sensor: ApiSensor,
    value: Double?,
    locale: Locale = Locale.getDefault()
): String {
    if (value == null) return "Sin lectura"
    val number = String.format(locale, "%.${sensorDisplayPrecision(sensor.sensorKey)}f", value)
    return if (sensor.unitSymbol.isBlank()) number else "$number ${sensor.unitSymbol}"
}

fun readingFreshness(
    measurement: ApiMeasurement?,
    sensorKey: String,
    now: Instant = Instant.now(),
    staleAfter: Duration = mobileStaleAfter
): ReadingFreshness {
    require(!staleAfter.isNegative && !staleAfter.isZero)
    if (measurement == null || !measurement.readings.containsKey(sensorKey)) {
        return ReadingFreshness.MISSING
    }
    val age = Duration.between(measurement.capturedAt, now)
    return when {
        age.isNegative -> ReadingFreshness.CLOCK_SKEW
        age > staleAfter -> ReadingFreshness.STALE
        else -> ReadingFreshness.FRESH
    }
}

fun formatLocalTimestamp(
    instant: Instant,
    zoneId: ZoneId = ZoneId.systemDefault(),
    locale: Locale = Locale.getDefault(),
    includeDate: Boolean = true
): String {
    val pattern = if (includeDate) "dd/MM/yyyy HH:mm" else "HH:mm"
    return DateTimeFormatter.ofPattern(pattern, locale)
        .withZone(zoneId)
        .format(instant)
}

fun readingSubtitle(
    measurement: ApiMeasurement?,
    sensorKey: String,
    now: Instant = Instant.now(),
    zoneId: ZoneId = ZoneId.systemDefault(),
    locale: Locale = Locale.getDefault(),
    staleAfter: Duration = mobileStaleAfter
): String {
    val capturedAt = measurement?.capturedAt ?: return "Sin datos"
    val timestamp = formatLocalTimestamp(capturedAt, zoneId, locale)
    return when (readingFreshness(measurement, sensorKey, now, staleAfter)) {
        ReadingFreshness.MISSING -> "Sin lectura en la última muestra · $timestamp"
        ReadingFreshness.FRESH -> "Lectura reciente · $timestamp"
        ReadingFreshness.STALE -> "Dato antiguo · $timestamp"
        ReadingFreshness.CLOCK_SKEW -> "Hora de captura por delante · $timestamp"
    }
}

fun telemetrySeries(
    records: List<ApiMeasurement>,
    sensorKey: String,
    capturedFrom: Instant? = null
): List<TimedReading> = records.asSequence()
    .filter { capturedFrom == null || !it.capturedAt.isBefore(capturedFrom) }
    .mapNotNull { measurement ->
        measurement.readings[sensorKey]?.let { value ->
            TimedReading(measurement.eventUuid, measurement.capturedAt, value)
        }
    }
    .sortedWith(compareBy(TimedReading::capturedAt, TimedReading::eventUuid))
    .toList()
