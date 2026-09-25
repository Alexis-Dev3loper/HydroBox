package com.hydrobox.app.ui.navigation

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.NotificationsActive
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.WarningAmber
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.hydrobox.app.api.ApiAlert
import com.hydrobox.app.api.DomainApiException
import com.hydrobox.app.api.HydroDomainApi
import com.hydrobox.app.ui.components.DomainDataStatusBanner
import com.hydrobox.app.ui.model.alertSeverityLabel
import com.hydrobox.app.ui.model.alertStatusLabel
import com.hydrobox.app.ui.model.alertSubjectLabel
import com.hydrobox.app.ui.model.formatLocalTimestamp
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch

@Composable
fun NotificationScreen(
    paddingValues: PaddingValues,
    api: HydroDomainApi,
    canRead: Boolean,
    canAcknowledge: Boolean,
    offlineMode: Boolean
) {
    var alerts by remember { mutableStateOf<List<ApiAlert>>(emptyList()) }
    var loading by remember { mutableStateOf(canRead) }
    var loadError by remember { mutableStateOf<String?>(null) }
    var operationMessage by remember { mutableStateOf<String?>(null) }
    var refresh by remember { mutableIntStateOf(0) }
    var filter by remember { mutableStateOf<String?>(null) }
    var busyAlerts by remember { mutableStateOf<Set<String>>(emptySet()) }
    val scope = rememberCoroutineScope()

    LaunchedEffect(api, canRead, refresh) {
        if (!canRead) {
            loading = false
            return@LaunchedEffect
        }
        loading = true
        loadError = null
        try {
            alerts = loadAllAlerts(api)
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (error: Exception) {
            loadError = alertProblemMessage(error)
        } finally {
            loading = false
        }
    }

    val visibleAlerts = remember(alerts, filter) {
        filter?.let { selected -> alerts.filter { it.statusKey == selected } } ?: alerts
    }

    Box(
        Modifier
            .padding(paddingValues)
            .fillMaxSize()
    ) {
        when {
            !canRead -> AlertUnavailableState(
                title = "Sin acceso a alertas",
                detail = "Tu cuenta no tiene el permiso alert:read."
            )
            loading && alerts.isEmpty() -> CircularProgressIndicator(Modifier.align(Alignment.Center))
            loadError != null && alerts.isEmpty() -> AlertErrorState(loadError!!) { refresh += 1 }
            else -> LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                item {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Column(Modifier.weight(1f)) {
                            Text(
                                "Alertas",
                                style = MaterialTheme.typography.headlineSmall.copy(
                                    fontWeight = FontWeight.SemiBold
                                )
                            )
                            Text(
                                "Incidentes operativos confirmados por HydroBox",
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        IconButton(onClick = { refresh += 1 }, enabled = !loading) {
                            Icon(Icons.Filled.Refresh, contentDescription = "Actualizar")
                        }
                    }
                }
                item { DomainDataStatusBanner(api) }
                if (offlineMode) {
                    item {
                        AlertNotice(
                            "Modo sin conexión: puedes consultar alertas guardadas, pero no confirmarlas.",
                            warning = true
                        )
                    }
                }
                loadError?.let { message ->
                    item { AlertNotice("No se pudo actualizar: $message", warning = true) }
                }
                operationMessage?.let { message ->
                    item { AlertNotice(message, warning = message.startsWith("No ")) }
                }
                item { AlertFilters(selected = filter, onSelected = { filter = it }) }
                if (visibleAlerts.isEmpty()) {
                    item {
                        AlertUnavailableState(
                            title = if (alerts.isEmpty()) "No hay alertas registradas" else "Sin coincidencias",
                            detail = if (alerts.isEmpty()) {
                                "HydroBox no ha registrado incidentes operativos para este sitio."
                            } else {
                                "No hay alertas con el estado seleccionado."
                            }
                        )
                    }
                } else {
                    items(visibleAlerts, key = ApiAlert::alertUuid) { alert ->
                        AlertCard(
                            alert = alert,
                            busy = alert.alertUuid in busyAlerts,
                            canAcknowledge = canAcknowledge && !offlineMode,
                            onAcknowledge = {
                                scope.launch {
                                    busyAlerts = busyAlerts + alert.alertUuid
                                    operationMessage = null
                                    try {
                                        val updated = api.acknowledgeAlert(alert.alertUuid)
                                        alerts = alerts.map {
                                            if (it.alertUuid == updated.alertUuid) updated else it
                                        }
                                        operationMessage = "Alerta confirmada. El incidente permanece activo hasta su recuperación."
                                    } catch (cancelled: CancellationException) {
                                        throw cancelled
                                    } catch (error: Exception) {
                                        operationMessage = "No se pudo confirmar la alerta: ${alertProblemMessage(error)}"
                                        if (error is DomainApiException && error.problem.status == 409) {
                                            refresh += 1
                                        }
                                    } finally {
                                        busyAlerts = busyAlerts - alert.alertUuid
                                    }
                                }
                            }
                        )
                    }
                }
            }
        }
    }
}

private suspend fun loadAllAlerts(api: HydroDomainApi): List<ApiAlert> {
    val result = mutableListOf<ApiAlert>()
    var cursor: String? = null
    while (true) {
        val page = api.alerts(limit = 100, cursor = cursor)
        result += page.items
        if (!page.hasMore) break
        cursor = page.nextCursor ?: break
    }
    return result.distinctBy(ApiAlert::alertUuid)
}

@Composable
private fun AlertFilters(selected: String?, onSelected: (String?) -> Unit) {
    LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        items(
            listOf(
                null to "Todas",
                "open" to "Abiertas",
                "acknowledged" to "Confirmadas",
                "resolved" to "Resueltas"
            )
        ) { (key, label) ->
            FilterChip(
                selected = selected == key,
                onClick = { onSelected(key) },
                label = { Text(label) }
            )
        }
    }
}

@Composable
private fun AlertCard(
    alert: ApiAlert,
    busy: Boolean,
    canAcknowledge: Boolean,
    onAcknowledge: () -> Unit
) {
    val severityColor = when (alert.severity) {
        "critical" -> MaterialTheme.colorScheme.error
        "warning" -> MaterialTheme.colorScheme.tertiary
        else -> MaterialTheme.colorScheme.primary
    }
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Column(
            Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(9.dp)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    if (alert.statusKey == "resolved") Icons.Filled.CheckCircle else Icons.Filled.WarningAmber,
                    contentDescription = null,
                    tint = severityColor
                )
                Spacer(Modifier.width(10.dp))
                Column(Modifier.weight(1f)) {
                    Text(alert.summary, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                    Text(
                        "${alertSeverityLabel(alert.severity)} · ${alertStatusLabel(alert.statusKey)}",
                        color = severityColor,
                        style = MaterialTheme.typography.labelLarge
                    )
                }
            }
            HorizontalDivider()
            Text(alert.detail, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Text(
                alertSubjectLabel(alert.subjectType, alert.subjectKey),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Text(
                "Última detección: ${formatLocalTimestamp(alert.lastOccurredAt, includeDate = true)}",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            if (alert.occurrenceCount > 1) {
                Text(
                    "Detectada ${alert.occurrenceCount} veces",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            if (alert.statusKey == "open") {
                Button(
                    onClick = onAcknowledge,
                    enabled = canAcknowledge && !busy,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Icon(Icons.Filled.NotificationsActive, contentDescription = null)
                    Spacer(Modifier.width(8.dp))
                    Text(if (busy) "Confirmando…" else "Confirmar recepción")
                }
                Text(
                    if (canAcknowledge) {
                        "Confirmar recepción no resuelve el incidente ni equivale a un ACK físico."
                    } else {
                        "Solo lectura: se requiere alert:acknowledge y conexión para confirmar."
                    },
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

@Composable
private fun AlertNotice(message: String, warning: Boolean) {
    val color = if (warning) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp),
        color = color.copy(alpha = 0.12f)
    ) {
        Text(message, Modifier.padding(12.dp), color = color)
    }
}

@Composable
private fun AlertUnavailableState(title: String, detail: String) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Text(title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
        Text(detail, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
private fun AlertErrorState(message: String, onRetry: () -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Text("No se pudieron cargar las alertas", fontWeight = FontWeight.SemiBold)
        Spacer(Modifier.height(8.dp))
        Text(message, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Spacer(Modifier.height(12.dp))
        OutlinedButton(onClick = onRetry) { Text("Reintentar") }
    }
}

private fun alertProblemMessage(error: Exception): String =
    (error as? DomainApiException)?.problem?.code ?: "error de conexión"
