package com.hydrobox.app.ui.navigation

import androidx.compose.foundation.background
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.pager.PageSize
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.gestures.snapping.rememberSnapFlingBehavior
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.DeviceThermostat
import androidx.compose.material.icons.filled.InvertColors
import androidx.compose.material.icons.filled.Opacity
import androidx.compose.material.icons.filled.Speed
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.hydrobox.app.api.ApiMeasurement
import com.hydrobox.app.api.ApiSensor
import com.hydrobox.app.api.HydroDomainApi
import com.hydrobox.app.ui.model.Crop
import com.hydrobox.app.ui.model.formatSensorReading
import com.hydrobox.app.ui.model.readingSubtitle
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.math.absoluteValue
import kotlin.math.roundToInt

data class SensorCardData(
    val title: String,
    val valueText: String,
    val subtitle: String,
    val icon: @Composable () -> Unit,
    val percent: Float? = null
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ResumeScreen(paddingValues: PaddingValues, api: HydroDomainApi) {
    var currentCrop by remember { mutableStateOf<Crop?>(null) }
    var currentCropName by remember { mutableStateOf<String?>(null) }
    var totalDays by remember { mutableStateOf<Int?>(null) }
    var startEpoch by remember { mutableStateOf<Long?>(null) }
    var cropLoading by remember { mutableStateOf(true) }
    var cropError by remember { mutableStateOf<String?>(null) }

    // reloj para que el día se actualice solo
    val now by produceState(initialValue = System.currentTimeMillis(), startEpoch) {
        while (true) {
            value = System.currentTimeMillis()
            delay(60_000L)
        }
    }

    LaunchedEffect(api) {
        try {
            val catalog = api.crops().associateBy { it.cropKey }
            val cycle = api.activeCycle()
            val crop = cycle?.cropKey?.let(Crop::fromKey)
            val catalogCrop = cycle?.cropKey?.let(catalog::get)
            currentCrop = crop
            currentCropName = catalogCrop?.name ?: crop?.fallbackDisplayName
            totalDays = cycle?.plannedDurationDays
                ?: catalogCrop?.defaultCycleDays
            startEpoch = cycle?.startedAt?.toEpochMilli()
            cropError = null
        } catch (_: Exception) {
            cropError = "No se pudo cargar el ciclo de cultivo activo."
        } finally {
            cropLoading = false
        }
    }

    val autoDay = remember(startEpoch, now, totalDays) {
        val start = startEpoch
        val duration = totalDays
        if (start == null || duration == null) 1
        else {
            val diffMillis = (now - start).coerceAtLeast(0L)
            val days = (diffMillis / 86_400_000L).toInt() + 1
            days.coerceIn(1, duration)
        }
    }

    var currentDay by remember(autoDay) { mutableIntStateOf(autoDay) }

    LaunchedEffect(autoDay) {
        if (autoDay > currentDay) currentDay = autoDay
    }

    // --- sensores: último registro de la API cada 15s ---
    var latest by remember { mutableStateOf<ApiMeasurement?>(null) }
    var sensorCatalog by remember { mutableStateOf<List<ApiSensor>>(emptyList()) }
    var sensorsError by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(api) {
        try {
            sensorCatalog = api.sensors()
                .filter(ApiSensor::active)
                .sortedBy(ApiSensor::displayOrder)
        } catch (_: Exception) {
            sensorsError = "No se pudo cargar el catálogo de sensores."
            return@LaunchedEffect
        }
        while (true) {
            try {
                latest = api.measurements(limit = 1).items.firstOrNull()
                sensorsError = null
            } catch (e: Exception) {
                sensorsError = "No se pudieron cargar los datos de los sensores."
            }
            delay(15_000L)
        }
    }

    val sensors = remember(latest, sensorCatalog, now) {
        sensorCatalog.map { sensor ->
            val measurement = latest
            SensorCardData(
                title = sensor.name,
                valueText = formatSensorReading(sensor, measurement?.readings?.get(sensor.sensorKey)),
                subtitle = readingSubtitle(
                    measurement = measurement,
                    sensorKey = sensor.sensorKey,
                    now = java.time.Instant.ofEpochMilli(now)
                ),
                icon = { Icon(sensorSummaryIcon(sensor.sensorKey), contentDescription = null) }
            )
        }
    }

    Column(
        Modifier
            .padding(paddingValues)
            .fillMaxSize()
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        val crop = currentCrop
        if (cropLoading) {
            Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                CircularProgressIndicator()
            }
        } else if (crop == null) {
            Text(
                cropError ?: "No hay un ciclo de cultivo activo.",
                color = if (cropError == null) MaterialTheme.colorScheme.onSurfaceVariant
                else MaterialTheme.colorScheme.error
            )
        } else {
            CropHeaderCard(crop = crop, displayName = currentCropName ?: crop.fallbackDisplayName)

            val duration = totalDays
            if (duration == null) {
                Text(
                    "Este cultivo no tiene una duración planificada.",
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            } else {
                GrowthCard(
                    currentDay = currentDay,
                    totalDays = duration,
                    onDayChanged = { currentDay = it.coerceIn(1, duration) }
                )
            }
        }

        if (sensorsError != null) {
            Text(
                sensorsError!!,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.error
            )
        }

        if (sensors.isEmpty()) {
            if (sensorsError == null) Text("No hay sensores activos en el catálogo.")
        } else {
            SensorsCarousel(cards = sensors)
        }
    }
}

private fun sensorSummaryIcon(sensorKey: String) = when (sensorKey) {
    "air_temperature" -> Icons.Filled.DeviceThermostat
    "air_humidity", "water_level" -> Icons.Filled.Opacity
    "ph" -> Icons.Filled.InvertColors
    else -> Icons.Filled.Speed
}
@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun DaysScroller(
    totalDays: Int,
    selectedDay: Int,
    onDaySelected: (Int) -> Unit
) {
    if (totalDays <= 0) return

    val itemWidth = 56.dp
    val spacing   = 8.dp

    val startPad  = 0.dp
    val endPad    = 0.dp

    val state        = rememberLazyListState()
    val snapBehavior = rememberSnapFlingBehavior(lazyListState = state)
    val scope        = rememberCoroutineScope()

    val targetIndex = (selectedDay - 1).coerceIn(0, totalDays - 1)

    LaunchedEffect(totalDays) {
        state.scrollToItem(targetIndex)
    }

    LaunchedEffect(targetIndex, totalDays) {
        val near = state.layoutInfo.visibleItemsInfo
            .any { it.index == targetIndex && it.offset == 0 }
        if (!near) state.animateScrollToItem(targetIndex)
    }

    LazyRow(
        state = state,
        horizontalArrangement = Arrangement.spacedBy(spacing),
        contentPadding = PaddingValues(start = startPad, end = endPad),
        flingBehavior = snapBehavior,
        modifier = Modifier.fillMaxWidth()
    ) {
        items(count = totalDays, key = { it }) { index ->
            val day = index + 1
            FilterChip(
                selected = day == selectedDay,
                onClick = {
                    onDaySelected(day)
                    scope.launch { state.animateScrollToItem(index) }
                },
                label = { Text(day.toString()) },
                modifier = Modifier.width(itemWidth)
            )
        }
    }
}
@Composable
private fun CropHeaderCard(crop: Crop, displayName: String) {
    val surface = MaterialTheme.colorScheme.surface
    val overlay = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.10f)

    Card(
        colors = CardDefaults.cardColors(containerColor = surface),
        shape = RoundedCornerShape(20.dp),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(
            Modifier.padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    Modifier
                        .size(36.dp)
                        .clip(CircleShape)
                        .background(MaterialTheme.colorScheme.primaryContainer)
                )
                Spacer(Modifier.width(8.dp))
                Text(
                    "Hydrobox",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold
                )
                Spacer(Modifier.weight(1f))
                AssistChip(onClick = { }, label = { Text("Alerta") })
            }

            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(160.dp)
                    .clip(RoundedCornerShape(16.dp))
                    .background(
                        brush = Brush.verticalGradient(
                            listOf(surface, overlay)
                        )
                    )
            ) {
                AsyncImage(
                    model = crop.imageRes,
                    contentDescription = displayName,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.matchParentSize()
                )
                Text(
                    displayName,
                    style = MaterialTheme.typography.titleMedium,
                    modifier = Modifier
                        .align(Alignment.BottomEnd)
                        .padding(10.dp)
                        .clip(RoundedCornerShape(8.dp))
                        .background(MaterialTheme.colorScheme.surface.copy(alpha = 0.35f))
                        .padding(horizontal = 8.dp, vertical = 4.dp)
                )
            }
        }
    }
}

@Composable
private fun GrowthCard(
    currentDay: Int,
    totalDays: Int,
    onDayChanged: (Int) -> Unit
) {
    Card(
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        shape = RoundedCornerShape(20.dp),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(
            Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {

            DaysScroller(
                totalDays = totalDays,
                selectedDay = currentDay,
                onDaySelected = onDayChanged
            )

            val progress = if (totalDays > 0) {
                currentDay.coerceIn(1, totalDays).toFloat() / totalDays.toFloat()
            } else 0f

            Text("Día $currentDay de $totalDays", style = MaterialTheme.typography.labelLarge)
            LinearProgressIndicator(
                progress = { progress },
                trackColor = MaterialTheme.colorScheme.surfaceVariant,
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(8.dp)
                    .clip(RoundedCornerShape(6.dp))
            )
            Text(
                "Hoy tus cultivos comienzan a mostrar señales de madurez.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun SensorsCarousel(cards: List<SensorCardData>) {
    Card(
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        shape = RoundedCornerShape(20.dp),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(
            Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {

            BoxWithConstraints(Modifier.fillMaxWidth()) {
                val peek = 20.dp
                val pageSpacing = 8.dp
                val pagerState = rememberPagerState(pageCount = { cards.size })
                val last = cards.lastIndex
                val current by remember { derivedStateOf { pagerState.currentPage } }

                val baseWidth = maxWidth - peek * 2

                val startPad = if (current == 0) 0.dp else peek
                val endPad   = if (current == last) 0.dp else peek

                HorizontalPager(
                    state = pagerState,
                    pageSpacing = pageSpacing,
                    contentPadding = PaddingValues(start = startPad, end = endPad),
                    pageSize = PageSize.Fixed(baseWidth)
                ) { page ->
                    val pageOffset = ((pagerState.currentPage - page) +
                            pagerState.currentPageOffsetFraction).absoluteValue
                    val t = 1f - pageOffset.coerceIn(0f, 1f)
                    val scale = 0.96f + 0.04f * t
                    val alpha = 0.85f + 0.15f * t

                    SensorCard(
                        data = cards[page],
                        modifier = Modifier
                            .fillMaxWidth()
                            .graphicsLayer {
                                scaleX = scale
                                scaleY = scale
                                this.alpha = alpha
                            }
                    )
                }

                Column(
                    Modifier
                        .align(Alignment.BottomCenter)
                        .padding(bottom = 0.dp)
                ) { }
            }
        }
    }
}

@Composable
private fun SensorCard(
    data: SensorCardData,
    modifier: Modifier = Modifier
) {
    val meterHeight = 6.dp

    Surface(
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f),
        shape = RoundedCornerShape(16.dp),
        tonalElevation = 1.dp,
        modifier = modifier.requiredHeight(140.dp)
    ) {
        Column(
            Modifier.padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                data.icon()
                Spacer(Modifier.width(8.dp))
                Text(
                    data.title,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1
                )
            }
            Text(
                data.valueText,
                style = MaterialTheme.typography.displaySmall.copy(fontWeight = FontWeight.Black),
                maxLines = 1
            )
            Text(
                data.subtitle,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1
            )

            Spacer(Modifier.height(6.dp))

            if (data.percent != null) {
                LinearProgressIndicator(
                    progress = { data.percent.coerceIn(0f, 1f) },
                    trackColor = MaterialTheme.colorScheme.surface,
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(meterHeight)
                        .clip(RoundedCornerShape(6.dp))
                )
                Text(
                    "${(data.percent * 100).roundToInt()} %",
                    style = MaterialTheme.typography.labelMedium
                )
            } else {
                Box(Modifier.fillMaxWidth().height(meterHeight))
            }
        }
    }
}
