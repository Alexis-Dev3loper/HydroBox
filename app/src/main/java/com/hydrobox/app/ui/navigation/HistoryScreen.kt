package com.hydrobox.app.ui.navigation

import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.drawscope.withTransform
import androidx.compose.ui.geometry.CornerRadius
import com.hydrobox.app.api.ApiMeasurement
import com.hydrobox.app.api.ApiSensor
import com.hydrobox.app.api.ApiSensorRange
import com.hydrobox.app.api.HydroDomainApi
import java.time.Duration
import java.time.Instant
import java.util.Locale
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.unit.sp
import androidx.compose.ui.platform.LocalDensity
import android.graphics.Paint
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Bolt
import androidx.compose.material.icons.filled.Science
import androidx.compose.material.icons.filled.Speed
import androidx.compose.material.icons.filled.WaterDrop
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.*
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.hydrobox.app.ui.components.HydroCard
import com.hydrobox.app.ui.components.SegmentedChip
import com.hydrobox.app.ui.components.SegmentedVariant
import com.hydrobox.app.ui.theme.BrandPrimary
import com.hydrobox.app.ui.theme.Error
import com.hydrobox.app.ui.model.formatLocalTimestamp
import com.hydrobox.app.ui.model.formatSensorReading
import com.hydrobox.app.ui.model.readingSubtitle
import com.hydrobox.app.ui.model.telemetrySeries
import kotlin.math.max
import kotlin.math.min
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.ExperimentalLayoutApi

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun HistoryScreen(paddingValues: PaddingValues, api: HydroDomainApi) {
    var range by remember { mutableStateOf(TimeRange.Last7d) }
    var metric by remember { mutableStateOf(Metric.PH) }

    var loading by remember { mutableStateOf(true) }
    var error by remember { mutableStateOf<String?>(null) }
    var records by remember { mutableStateOf<List<ApiMeasurement>>(emptyList()) }
    var sensorsByKey by remember { mutableStateOf<Map<String, ApiSensor>>(emptyMap()) }
    var rangesByKey by remember { mutableStateOf<Map<String, ApiSensorRange>>(emptyMap()) }

    LaunchedEffect(api) {
        try {
            sensorsByKey = api.sensors()
                .filter(ApiSensor::active)
                .associateBy(ApiSensor::sensorKey)
            val activeCycle = api.activeCycle()
            rangesByKey = activeCycle?.cropKey
                ?.let { api.sensorRanges(it).associateBy(ApiSensorRange::sensorKey) }
                .orEmpty()
            records = api.measurements(limit = 100).items
        } catch (e: Exception) {
            error = "No se pudieron cargar las mediciones"
        } finally {
            loading = false
        }
    }

    val availableMetrics = remember(sensorsByKey) {
        Metric.entries.filter { it.sensorKey in sensorsByKey }
    }
    LaunchedEffect(availableMetrics) {
        if (availableMetrics.isNotEmpty() && metric !in availableMetrics) {
            metric = availableMetrics.first()
        }
    }
    val sensor = sensorsByKey[metric.sensorKey]
    val optimum = rangesByKey[metric.sensorKey]
        ?.let { it.minValue.toFloat()..it.maxValue.toFloat() }
    val points by remember(records, range, metric, sensor) {
        mutableStateOf(
            if (sensor == null) emptyList()
            else telemetrySeries(
                records = records,
                sensorKey = metric.sensorKey,
                capturedFrom = range.duration?.let { Instant.now().minus(it) }
            )
        )
    }
    val series = points.map { it.value.toFloat() }
    val metricLabel = sensor?.name ?: metric.fallbackLabel

    Column(
        Modifier
            .padding(paddingValues)
            .fillMaxSize()
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Text(
            "Historial",
            style = MaterialTheme.typography.headlineSmall.copy(fontWeight = FontWeight.SemiBold),
            color = MaterialTheme.colorScheme.onBackground
        )
        Text(
            "Tendencias y eventos del cultivo",
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )

        if (loading) {
            Text(
                "Cargando mediciones...",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        if (error != null) {
            Text(
                error!!,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.error
            )
        }

        // ===================== Filtros =====================
        SectionTitle("Rango")
        LazyRow(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            contentPadding = PaddingValues(horizontal = 2.dp)
        ) {
            items(TimeRange.entries.toList()) { item ->
                SegmentedChip(
                    text = item.label,
                    selected = item == range,
                    onClick = { range = item },
                    variant = SegmentedVariant.Primary
                )
            }
        }

        SectionTitle("Métrica")
        if (availableMetrics.isEmpty() && !loading) {
            Text(
                "No hay sensores activos en el catálogo.",
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        } else {
            FlowRow(
                maxItemsInEachRow = 3,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                availableMetrics.forEach { m ->
                    SegmentedChip(
                        text = sensorsByKey[m.sensorKey]?.name ?: m.fallbackLabel,
                        selected = m == metric,
                        onClick = { metric = m },
                        variant = SegmentedVariant.Tonal
                    )
                }
            }
        }

        HydroCard(
            title = "Tendencia",
            subtitle = "$metricLabel — ${range.label}",
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(12.dp)) {

                val currentMetric = metric
                val yAxisLabel = sensor?.let { "${it.name} (${it.unitSymbol})" }
                    ?: currentMetric.fallbackLabel

                val xLabels = remember(range, points) {
                    points.map {
                        formatLocalTimestamp(
                            instant = it.capturedAt,
                            includeDate = range != TimeRange.Today
                        )
                    }
                }

                if (series.isEmpty()) {
                    Text(
                        "Sin datos para esta métrica.",
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                } else {
                    TrendChart(
                        series   = series,
                        optimum  = optimum,
                        lineColor = BrandPrimary,
                        xLabels  = xLabels,
                        yLabel   = yAxisLabel,
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(220.dp)
                            .clip(RoundedCornerShape(16.dp))
                    )
                }

                if (series.isNotEmpty()) {
                    StatsRow(series = series, sensor = sensor!!)
                    val latest = records
                        .filter { it.readings.containsKey(metric.sensorKey) }
                        .maxByOrNull(ApiMeasurement::capturedAt)
                    latest?.let {
                        Text(
                            readingSubtitle(it, metric.sensorKey),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
        }

        // ===================== Eventos =====================
        HydroCard(
            title = "Eventos",
            subtitle = "Alarmas, cambios de actuadores y calibraciones",
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(14.dp)) {
                TimelineEventRow(
                    icon = Icons.Filled.Bolt,
                    title = "Bomba de agua — ON",
                    subtitle = "Programado | 10:00",
                    accent = BrandPrimary
                )
                TimelineEventRow(
                    icon = Icons.Filled.Science,
                    title = "pH fuera de rango",
                    subtitle = "Lectura = 5.5 | Óptimo: 5.8–6.2",
                    accent = Error
                )
                TimelineEventRow(
                    icon = Icons.Filled.Speed,
                    title = "Extractor — OFF",
                    subtitle = "Manual | 12:43",
                    accent = MaterialTheme.colorScheme.onSurfaceVariant
                )
                TimelineEventRow(
                    icon = Icons.Filled.WaterDrop,
                    title = "Dosificación Micro — 8 ml",
                    subtitle = "Rutina semanal | 10:02",
                    accent = BrandPrimary
                )
            }
        }
    }
}

/* ===================== UI helpers ===================== */

@Composable
private fun SectionTitle(text: String) {
    Text(
        text,
        style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Medium),
        color = MaterialTheme.colorScheme.onSurface
    )
}

@Composable
private fun StatsRow(series: List<Float>, sensor: ApiSensor) {
    val minV = series.minOrNull() ?: 0f
    val maxV = series.maxOrNull() ?: 0f
    val avgV = if (series.isNotEmpty()) series.average().toFloat() else 0f

    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        StatPill("Mín", formatSensorReading(sensor, minV.toDouble()))
        StatPill("Prom", formatSensorReading(sensor, avgV.toDouble()))
        StatPill("Máx", formatSensorReading(sensor, maxV.toDouble()))
    }
}

@Composable
private fun StatPill(label: String, value: String) {
    Surface(
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = .5f),
        shape = RoundedCornerShape(999.dp)
    ) {
        Row(
            Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(label, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Spacer(Modifier.width(6.dp))
            Text(value, style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.SemiBold))
        }
    }
}

@Composable
private fun TimelineEventRow(
    icon: ImageVector,
    title: String,
    subtitle: String,
    accent: Color
) {
    Row(
        Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Box(
                Modifier
                    .width(2.dp)
                    .height(6.dp)
                    .background(MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = .25f))
            )
            Box(
                Modifier
                    .size(22.dp)
                    .clip(CircleShape)
                    .background(accent.copy(alpha = .15f)),
                contentAlignment = Alignment.Center
            ) {
                Icon(icon, null, tint = accent, modifier = Modifier.size(14.dp))
            }
            Box(
                Modifier
                    .width(2.dp)
                    .height(24.dp)
                    .background(MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = .25f))
            )
        }
        Spacer(Modifier.width(12.dp))
        Column {
            Text(
                title,
                style = MaterialTheme.typography.bodyLarge.copy(fontWeight = FontWeight.SemiBold),
                color = MaterialTheme.colorScheme.onSurface
            )
            Text(
                subtitle,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        Spacer(Modifier.weight(1f))
    }
}

/* ===================== Chart ===================== */

@Composable
private fun TrendChart(
    series: List<Float>,
    optimum: ClosedFloatingPointRange<Float>?,
    lineColor: Color,
    xLabels: List<String> = emptyList(),
    yLabel: String? = null,
    modifier: Modifier = Modifier
) {
    val progress by animateFloatAsState(
        targetValue = 1f,
        animationSpec = tween(durationMillis = 850),
        label = "reveal"
    )

    val axisColor   = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = .45f)
    val gridColor   = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = .15f)
    val textColor   = MaterialTheme.colorScheme.onSurface.copy(alpha = .9f).toArgb()
    val density     = LocalDensity.current
    val fontPxSmall = with(density) { 11.sp.toPx() }
    val fontPxAxis  = with(density) { 12.sp.toPx() }

    val labelPaint = remember(textColor, fontPxSmall) {
        Paint().apply { isAntiAlias = true; color = textColor; textSize = fontPxSmall }
    }
    val axisPaint = remember(textColor, fontPxAxis) {
        Paint().apply { isAntiAlias = true; color = textColor; textSize = fontPxAxis }
    }

    Canvas(modifier.padding(12.dp)) {
        if (series.size < 2) return@Canvas

        val leftPad   = 32.dp.toPx()
        val rightPad  = 10.dp.toPx()
        val topPad    = 8.dp.toPx()
        val bottomPad = 24.dp.toPx()

        val w = size.width  - leftPad - rightPad
        val h = size.height - topPad  - bottomPad

        val sMin = series.minOrNull()!!
        val sMax = series.maxOrNull()!!
        val yMin = min(optimum?.start ?: sMin, sMin)
        val yMax = max(optimum?.endInclusive ?: sMax, sMax)
        val ySpan = (yMax - yMin).takeIf { it > 0f } ?: 1f

        fun mapX(i: Int): Float = leftPad + (i.toFloat() / (series.size - 1)) * w
        fun mapY(v: Float): Float = topPad + (1f - (v - yMin) / ySpan) * h

        val rows = 4
        repeat(rows - 1) { r ->
            val y = topPad + (h / (rows - 1)) * (r + 1)
            drawLine(gridColor, Offset(leftPad, y), Offset(leftPad + w, y), strokeWidth = 1f)
        }

        optimum?.let { r ->
            val yTop = mapY(r.endInclusive)
            val yBot = mapY(r.start)
            drawRoundRect(
                color = lineColor.copy(alpha = .15f),
                topLeft = Offset(leftPad, yTop),
                size = Size(w, yBot - yTop),
                cornerRadius = CornerRadius(8f, 8f)
            )
        }

        val path = Path().apply {
            moveTo(mapX(0), mapY(series.first()))
            for (i in 1 until series.size) lineTo(mapX(i), mapY(series[i]))
        }
        val area = Path().apply {
            addPath(path)
            lineTo(mapX(series.lastIndex), topPad + h)
            lineTo(mapX(0), topPad + h)
            close()
        }

        val x0 = leftPad
        val y0 = topPad + h
        drawLine(axisColor, Offset(x0, y0), Offset(leftPad + w, y0), strokeWidth = 2f)
        drawLine(axisColor, Offset(x0, y0), Offset(x0, topPad),    strokeWidth = 2f)

        val yTicks = 4
        for (i in 0..yTicks) {
            val t = i / yTicks.toFloat()
            val y = topPad + h * (1f - t)
            drawLine(axisColor, Offset(x0 - 6f, y), Offset(x0, y), strokeWidth = 2f)
            val value = yMin + ySpan * t
            drawContext.canvas.nativeCanvas.drawText(
                String.format(Locale.US, "%.1f", value),
                x0 - 8.dp.toPx(),
                y + labelPaint.textSize / 3f,
                labelPaint.apply { textAlign = Paint.Align.RIGHT }
            )
        }

        val xTickCount = min(5, series.lastIndex.coerceAtLeast(1))
        for (j in 0..xTickCount) {
            val idx = ((series.lastIndex) * j / xTickCount)
            val x = mapX(idx)
            drawLine(axisColor, Offset(x, y0), Offset(x, y0 + 6f), strokeWidth = 2f)
            val label = xLabels.getOrNull(idx) ?: idx.toString()
            drawContext.canvas.nativeCanvas.drawText(
                label,
                x, y0 + 16.dp.toPx(),
                labelPaint.apply { textAlign = Paint.Align.CENTER }
            )
        }

        val revealW = leftPad + w * progress
        withTransform({
            clipRect(left = 0f, top = 0f, right = revealW, bottom = size.height)
        }) {
            drawPath(
                path = area,
                brush = Brush.verticalGradient(
                    listOf(lineColor.copy(alpha = .28f), lineColor.copy(alpha = .05f)),
                    startY = topPad, endY = topPad + h
                )
            )
            drawPath(path = path, color = lineColor, style = Stroke(width = 4f, cap = StrokeCap.Round))
        }

        val last = Offset(mapX(series.lastIndex), mapY(series.last()))
        drawCircle(lineColor.copy(alpha = .25f), radius = 14f, center = last)
        drawCircle(lineColor, radius = 6f, center = last)

        yLabel?.let {
            drawContext.canvas.nativeCanvas.drawText(
                it, x0 - 22.dp.toPx(), topPad - 6.dp.toPx(),
                axisPaint.apply { textAlign = Paint.Align.LEFT }
            )
        }
        if (xLabels.isNotEmpty()) {
            drawContext.canvas.nativeCanvas.drawText(
                "Tiempo", leftPad + w / 2f, size.height - 4.dp.toPx(),
                axisPaint.apply { textAlign = Paint.Align.CENTER }
            )
        }
    }
}

/* ===================== Datos/formatos ===================== */

/* ===================== Enums ===================== */

private enum class TimeRange(val label: String, val duration: Duration?) {
    Today("Hoy", Duration.ofDays(1)),
    Last7d("7 días", Duration.ofDays(7)),
    Last30d("30 días", Duration.ofDays(30)),
    Last90d("90 días", Duration.ofDays(90)),
    All("Todo", null)
}

private enum class Metric(val fallbackLabel: String, val sensorKey: String) {
    PH("pH", "ph"), ORP("ORP", "orp"), WaterTemp("Temp Agua", "water_temperature"),
    AirTemp("Temp Aire", "air_temperature"), Humidity("Humedad", "air_humidity"),
    Level("Nivel Agua", "water_level")
}
