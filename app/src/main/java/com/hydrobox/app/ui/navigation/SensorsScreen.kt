package com.hydrobox.app.ui.navigation

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.hydrobox.app.api.ApiSensor
import com.hydrobox.app.api.HydroDomainApi

@Composable
fun SensorsScreen(paddingValues: PaddingValues, api: HydroDomainApi) {
    var sensores by remember { mutableStateOf<List<ApiSensor>>(emptyList()) }
    var loading by remember { mutableStateOf(true) }
    var errorMsg by remember { mutableStateOf<String?>(null) }
    var detailSensor by remember { mutableStateOf<ApiSensor?>(null) }

    LaunchedEffect(api) {
        try {
            sensores = api.sensors().filter { it.active }.sortedBy { it.displayOrder }
            errorMsg = null
        } catch (e: Exception) {
            errorMsg = "No se pudieron cargar los sensores registrados."
        } finally {
            loading = false
        }
    }

    Column(
        Modifier
            .padding(paddingValues)
            .fillMaxSize()
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        Text(
            "Sensores Registrados",
            style = MaterialTheme.typography.headlineSmall.copy(fontWeight = FontWeight.SemiBold)
        )

        when {
            loading -> {
                Box(
                    Modifier
                        .fillMaxWidth()
                        .padding(top = 24.dp),
                    contentAlignment = Alignment.Center
                ) {
                    CircularProgressIndicator()
                }
            }
            errorMsg != null -> {
                Text(
                    errorMsg!!,
                    color = MaterialTheme.colorScheme.error,
                    style = MaterialTheme.typography.bodyMedium
                )
            }
            else -> {
                sensores.forEach { sensor ->
                    DeviceRowPill(
                        title = sensor.name,
                        onDetails = { detailSensor = sensor }
                    )
                }
            }
        }

        Spacer(Modifier.weight(1f))
    }

    detailSensor?.let { sensor ->
        SensorDetailsDialog(
            sensor = sensor,
            onDismiss = { detailSensor = null }
        )
    }
}

/** Degradado claro/oscuro para el fondo del pill */
@Composable
private fun pillBackgroundBrush(cs: ColorScheme): Brush {
    val isDark = cs.background.luminance() < 0.5f
    return if (isDark) {
        Brush.verticalGradient(
            colors = listOf(
                cs.primary.copy(alpha = 0.24f),
                cs.primaryContainer.copy(alpha = 0.40f),
                cs.surfaceVariant.copy(alpha = 0.18f)
            )
        )
    } else {
        Brush.verticalGradient(
            colors = listOf(
                cs.primaryContainer.copy(alpha = 0.70f),
                cs.primary.copy(alpha = 0.30f),
                cs.surface.copy(alpha = 0.55f)
            )
        )
    }
}

@Composable
private fun DeviceRowPill(
    title: String,
    onDetails: () -> Unit
) {
    val shape = RoundedCornerShape(28.dp)
    val cs    = MaterialTheme.colorScheme
    val neon  = cs.primary
    val bg    = pillBackgroundBrush(cs)

    Surface(
        color = Color.Transparent,
        shape = shape,
        tonalElevation = 1.dp,
        border = BorderStroke(2.dp, SolidColor(neon)),
        modifier = Modifier
            .fillMaxWidth()
            .height(64.dp)
            .clip(shape)
            .shadow(elevation = 2.dp, shape = shape)
    ) {
        Box(
            Modifier
                .fillMaxSize()
                .background(bg, shape)
                .border(
                    BorderStroke(1.dp, SolidColor(cs.onSurface.copy(alpha = 0.06f))),
                    shape
                )
                .padding(horizontal = 18.dp)
        ) {
            Row(
                Modifier.fillMaxSize(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    title,
                    style = MaterialTheme.typography.titleMedium,
                    modifier = Modifier.weight(1f)
                )
                AssistChip(
                    onClick = onDetails,
                    label = { Text("Características") },
                    colors = AssistChipDefaults.assistChipColors(
                        containerColor = cs.primaryContainer,
                        labelColor = cs.onPrimaryContainer
                    ),
                    shape = RoundedCornerShape(9999.dp)
                )
            }
        }
    }
}

@Composable
private fun SensorDetailsDialog(
    sensor: ApiSensor,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text("Cerrar")
            }
        },
        title = {
            Text(sensor.name)
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text("Clave lógica: ${sensor.sensorKey}")
                Text("Unidad de medida: ${sensor.unitSymbol}")
                sensor.description?.let { Text(it) }
                Text(if (sensor.active) "Estado: activo" else "Estado: inactivo")
            }
        }
    )
}
