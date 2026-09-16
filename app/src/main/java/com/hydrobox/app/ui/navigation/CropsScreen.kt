package com.hydrobox.app.ui.navigation

import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateDpAsState
import kotlinx.coroutines.launch
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.PageSize
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.DeviceThermostat
import androidx.compose.material.icons.filled.InvertColors
import androidx.compose.material.icons.filled.Opacity
import androidx.compose.material.icons.filled.Speed
import com.hydrobox.app.api.ApiSensorRange
import com.hydrobox.app.api.HydroDomainApi
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import coil.request.ImageRequest
import com.hydrobox.app.ui.model.Crop
import kotlin.math.absoluteValue
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.runtime.derivedStateOf
import java.util.Locale

private data class CropSpec(
    val cropKey: String,
    val name: String,
    val imageRes: Int,
    val totalDays: Int,
    val ranges: List<ApiSensorRange>
)

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun CropsScreen(
    paddingValues: PaddingValues,
    api: HydroDomainApi,
    canChangeCrop: Boolean
) {
    val scrollState = rememberScrollState()

    var cropsCatalog by remember { mutableStateOf<List<CropSpec>>(emptyList()) }
    var activeIndex by remember { mutableIntStateOf(-1) }
    var activeDaysElapsed by remember { mutableIntStateOf(1) }
    val pagerState = rememberPagerState(pageCount = { cropsCatalog.size.coerceAtLeast(1) })
    var confirmForIndex by remember { mutableStateOf<Int?>(null) }

    val scope = rememberCoroutineScope()
    var loading by remember { mutableStateOf(true) }
    var errorMsg by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(api) {
        try {
            val catalogSpecs = api.crops()
                .filter { it.active }
                .mapNotNull { crop ->
                    val local = Crop.fromKey(crop.cropKey) ?: return@mapNotNull null
                    CropSpec(
                        cropKey = crop.cropKey,
                        name = crop.name,
                        imageRes = local.imageRes,
                        totalDays = crop.defaultCycleDays ?: local.fallbackCycleDays,
                        ranges = api.sensorRanges(crop.cropKey)
                    )
                }
            val activeCycle = api.activeCycle()
            val activePlannedDays = activeCycle?.plannedDurationDays
            val specs = catalogSpecs.map { spec ->
                if (spec.cropKey == activeCycle?.cropKey && activePlannedDays != null) {
                    spec.copy(totalDays = activePlannedDays)
                } else {
                    spec
                }
            }
            cropsCatalog = specs
            activeIndex = specs.indexOfFirst { it.cropKey == activeCycle?.cropKey }
            activeDaysElapsed = activeCycle?.startedAt?.toEpochMilli()?.let(::computeDaysElapsed) ?: 1
            val initialPage = activeIndex.takeIf { it >= 0 } ?: 0
            if (specs.isNotEmpty()) pagerState.scrollToPage(initialPage)
            errorMsg = null
        } catch (_: Exception) {
            errorMsg = "No se pudo cargar el catálogo de cultivos."
        } finally {
            loading = false
        }
    }

    Column(
        Modifier
            .padding(paddingValues)
            .consumeWindowInsets(paddingValues)
            .verticalScroll(scrollState)
            .navigationBarsPadding()
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Text(
            "Gestión de hortalizas",
            style = MaterialTheme.typography.headlineSmall.copy(fontWeight = FontWeight.SemiBold),
            color = MaterialTheme.colorScheme.onBackground
        )

        if (loading) {
            Text(
                "Cargando hortaliza actual...",
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        if (errorMsg != null) {
            Text(
                errorMsg!!,
                color = MaterialTheme.colorScheme.error,
                style = MaterialTheme.typography.bodySmall
            )
        }

        if (cropsCatalog.isEmpty()) {
            if (!loading && errorMsg == null) {
                Text(
                    "No hay cultivos activos disponibles.",
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        } else {
        Box {
            HorizontalPager(
                state = pagerState,
                pageSpacing = 16.dp,
                contentPadding = PaddingValues(horizontal = 28.dp),
                pageSize = PageSize.Fill
            ) { page ->
                val crop = cropsCatalog[page]
                val rawOffset = (pagerState.currentPage - page) +
                        pagerState.currentPageOffsetFraction
                val absOffset = rawOffset.absoluteValue
                val t = 1f - absOffset.coerceIn(0f, 1f)

                val scale = 0.95f + 0.05f * t
                val alpha = 0.85f + 0.15f * t
                val haloAlpha by animateFloatAsState(
                    if (page == pagerState.currentPage) 0.22f else 0f
                )

                val borderBrush = Brush.linearGradient(
                    listOf(
                        MaterialTheme.colorScheme.primary.copy(alpha = 0.55f),
                        MaterialTheme.colorScheme.primary.copy(alpha = 0.10f)
                    )
                )

                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(230.dp)
                        .graphicsLayer {
                            translationX = rawOffset * 60f
                            scaleX = scale
                            scaleY = scale
                            this.alpha = alpha
                        }
                        .shadow(
                            elevation = 10.dp,
                            shape = RoundedCornerShape(22.dp),
                            clip = false
                        )
                        .clip(RoundedCornerShape(22.dp))
                        .background(
                            Brush.radialGradient(
                                listOf(
                                    MaterialTheme.colorScheme.primary.copy(alpha = haloAlpha),
                                    MaterialTheme.colorScheme.primary.copy(alpha = 0f)
                                )
                            )
                        )
                        .borderIf(
                            page == pagerState.currentPage,
                            width = 1.2.dp,
                            brush = borderBrush,
                            shape = RoundedCornerShape(22.dp)
                        )
                        .noRippleClickable {
                            if (canChangeCrop && page != activeIndex) {
                                confirmForIndex = page
                            }
                        }
                ) {
                    val ctx = LocalContext.current
                    AsyncImage(
                        model = ImageRequest.Builder(ctx)
                            .data(crop.imageRes)
                            .crossfade(true)
                            .build(),
                        contentDescription = crop.name,
                        contentScale = ContentScale.Crop,
                        modifier = Modifier.matchParentSize()
                    )
                }
            }

            PagerFancyIndicator(
                count = cropsCatalog.size,
                current = pagerState.currentPage,
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .padding(bottom = 8.dp)
            )
        }

        AnimatedVisibility(visible = pagerState.currentPage != activeIndex) {
            AssistChip(
                onClick = {},
                enabled = false,
                label = {
                    Text(
                        if (canChangeCrop) "Toca la imagen para seleccionar"
                        else "Tu cuenta no puede cambiar el cultivo"
                    )
                },
                leadingIcon = {
                    Icon(
                        Icons.Filled.Opacity,
                        null,
                        modifier = Modifier.size(16.dp)
                    )
                }
            )
        }

        val currentCrop = cropsCatalog[pagerState.currentPage]

        val cards = remember(currentCrop) {
            currentCrop.ranges.map { range ->
                SensorCardData(
                    title = sensorTitle(range.sensorKey),
                    valueText = formatSensorRange(range),
                    subtitle = "Rango óptimo",
                    icon = { Icon(sensorIcon(range.sensorKey), null) }
                )
            }
        }

        Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
            TimeEstimateInfoCard(
                isActive = (pagerState.currentPage == activeIndex),
                daysElapsed = activeDaysElapsed,
                totalDays = currentCrop.totalDays
            )

            if (cards.isEmpty()) {
                Text(
                    "No hay rangos configurados para este cultivo.",
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            } else {
                SensorsCarouselClone(
                    cards = cards,
                    isCompact = pagerState.currentPage != activeIndex
                )
            }
        }
        }
    }

    val pendingIndex = confirmForIndex
    if (pendingIndex != null) {
        val pendingCrop = cropsCatalog[pendingIndex]
        val activeCrop = cropsCatalog.getOrNull(activeIndex)
        val remaining = activeCrop?.let { (it.totalDays - activeDaysElapsed).coerceAtLeast(0) }

        AlertDialog(
            onDismissRequest = { confirmForIndex = null },
            title = { Text("Cambiar a ${pendingCrop.name}?") },
            text = {
                Text(
                    if (activeCrop == null) {
                        "Se iniciará un nuevo ciclo con ${pendingCrop.name}."
                    } else {
                        "Actualmente tienes ${activeCrop.name} con $remaining días pendientes para completar su ciclo. " +
                                "Si cambias ahora, se dará por abandonado."
                    },
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        scope.launch {
                            try {
                                api.changeActiveCrop(pendingCrop.cropKey, pendingCrop.totalDays)
                                activeIndex = pendingIndex
                                activeDaysElapsed = 1
                                pagerState.scrollToPage(pendingIndex)
                                errorMsg = null
                            } catch (_: Exception) {
                                errorMsg = "No se pudo cambiar el cultivo activo."
                            } finally {
                                confirmForIndex = null
                            }
                        }
                    }
                ) {
                    Text("Abandonar y cambiar")
                }
            },
            dismissButton = {
                TextButton(onClick = { confirmForIndex = null }) {
                    Text("Cancelar")
                }
            }
        )
    }
}

@Composable
private fun TimeEstimateInfoCard(
    isActive: Boolean,
    daysElapsed: Int,
    totalDays: Int
) {
    val progress = if (isActive) {
        (daysElapsed.coerceIn(0, totalDays).toFloat() /
                totalDays.toFloat().coerceAtLeast(1f))
    } else 0f

    Card(
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        shape = RoundedCornerShape(20.dp),
        elevation = CardDefaults.cardElevation(defaultElevation = 3.dp),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(
            Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Text(
                "Tiempo estimado",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold
            )
            Text(
                "Esta hortaliza toma un total de $totalDays días en cultivar.",
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            if (isActive) {
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
                    "Día $daysElapsed de $totalDays",
                    style = MaterialTheme.typography.labelLarge
                )
            }
        }
    }
}

@Composable
private fun PagerFancyIndicator(
    count: Int,
    current: Int,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        repeat(count) { i ->
            val selected = i == current
            val w by animateDpAsState(if (selected) 22.dp else 8.dp)
            val alpha by animateFloatAsState(if (selected) 1f else 0.45f)
            val color by animateColorAsState(
                if (selected) MaterialTheme.colorScheme.primary
                else MaterialTheme.colorScheme.onSurfaceVariant
            )
            Box(
                Modifier
                    .height(6.dp)
                    .width(w)
                    .clip(CircleShape)
                    .background(color.copy(alpha = alpha))
            )
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun SensorsCarouselClone(
    cards: List<SensorCardData>,
    isCompact: Boolean
) {
    val expandedContent = 140.dp
    val compactContent  = 120.dp

    val animatedContentHeight by animateDpAsState(
        targetValue = if (isCompact) compactContent else expandedContent,
        label = "sensorContentHeight"
    )
    val cardHeight = animatedContentHeight + 32.dp

    Card(
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        shape = RoundedCornerShape(20.dp),
        elevation = CardDefaults.cardElevation(defaultElevation = 3.dp),
        modifier = Modifier
            .fillMaxWidth()
            .requiredHeight(cardHeight)
    ) {
        Column(
            Modifier
                .padding(16.dp)
                .fillMaxSize()
        ) {
            BoxWithConstraints(
                Modifier
                    .fillMaxWidth()
                    .height(animatedContentHeight)
            ) {
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
                    pageSize = PageSize.Fixed(baseWidth),
                    modifier = Modifier.fillMaxSize()
                ) { page ->
                    val pageOffset = ((pagerState.currentPage - page) +
                            pagerState.currentPageOffsetFraction).absoluteValue
                    val t = 1f - pageOffset.coerceIn(0f, 1f)
                    val scale = 0.96f + 0.04f * t
                    val alpha = 0.85f + 0.15f * t

                    SensorCardClone(
                        data = cards[page],
                        compact = isCompact,
                        modifier = Modifier
                            .fillMaxSize()
                            .graphicsLayer {
                                scaleX = scale
                                scaleY = scale
                                this.alpha = alpha
                            }
                    )
                }
            }
        }
    }
}

@Composable
private fun SensorCardClone(
    data: SensorCardData,
    compact: Boolean,
    modifier: Modifier = Modifier
) {
    val pad by animateDpAsState(if (compact) 12.dp else 14.dp, label = "pad")
    val spacing by animateDpAsState(if (compact) 4.dp else 6.dp, label = "spacing")
    val meterHeight by animateDpAsState(if (compact) 4.dp else 6.dp, label = "meterH")

    val titleStyle =
        if (compact) MaterialTheme.typography.titleSmall else MaterialTheme.typography.titleMedium
    val valueStyle =
        if (compact) MaterialTheme.typography.headlineMedium.copy(fontWeight = FontWeight.Black)
        else MaterialTheme.typography.displaySmall.copy(fontWeight = FontWeight.Black)

    Surface(
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f),
        shape = RoundedCornerShape(16.dp),
        tonalElevation = 1.dp,
        modifier = modifier
    ) {
        Column(
            Modifier.padding(pad),
            verticalArrangement = Arrangement.spacedBy(spacing)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                data.icon()
                Spacer(Modifier.width(8.dp))
                Text(
                    data.title,
                    style = titleStyle,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
            Text(
                data.valueText,
                style = valueStyle,
                maxLines = 1,
                overflow = TextOverflow.Clip
            )
            Text(
                data.subtitle,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Spacer(Modifier.height(spacing))
            Box(Modifier.fillMaxWidth().height(meterHeight))
        }
    }
}

@Composable
private fun Modifier.noRippleClickable(onClick: () -> Unit): Modifier {
    val interaction = remember { MutableInteractionSource() }
    return this.then(
        Modifier.clickable(
            interactionSource = interaction,
            indication = null,
            onClick = onClick
        )
    )
}

private fun Modifier.borderIf(
    condition: Boolean,
    width: Dp,
    brush: Brush,
    shape: Shape
): Modifier =
    if (condition) this.then(Modifier.border(width, brush, shape)) else this

private fun computeDaysElapsed(startEpoch: Long, now: Long = System.currentTimeMillis()): Int {
    val millisPerDay = 86_400_000L
    val diff = (now - startEpoch).coerceAtLeast(0L)
    val days = (diff / millisPerDay).toInt() + 1
    return days.coerceAtLeast(1)
}

private fun sensorTitle(sensorKey: String): String = when (sensorKey) {
    "air_temperature" -> "Temperatura del aire"
    "air_humidity" -> "Humedad del aire"
    "water_temperature" -> "Temperatura del agua"
    "ph" -> "pH del agua"
    "orp" -> "ORP"
    "water_level" -> "Nivel del agua"
    else -> sensorKey
}

private fun sensorIcon(sensorKey: String): ImageVector = when (sensorKey) {
    "air_temperature" -> Icons.Filled.DeviceThermostat
    "air_humidity", "water_level" -> Icons.Filled.Opacity
    "ph" -> Icons.Filled.InvertColors
    else -> Icons.Filled.Speed
}

private fun formatSensorRange(range: ApiSensorRange): String =
    "${formatRangeNumber(range.minValue)}–${formatRangeNumber(range.maxValue)} ${range.unitSymbol}"

private fun formatRangeNumber(value: Double): String =
    String.format(Locale.US, "%.2f", value).trimEnd('0').trimEnd('.')
