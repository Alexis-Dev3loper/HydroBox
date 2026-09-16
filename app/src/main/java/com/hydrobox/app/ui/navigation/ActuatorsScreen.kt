package com.hydrobox.app.ui.navigation

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.hydrobox.app.api.*
import com.hydrobox.app.ui.model.*
import com.hydrobox.app.ui.components.DomainDataStatusBanner
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ActuatorsScreen(
    paddingValues: PaddingValues,
    api: HydroDomainApi,
    scopes: Set<String>
) {
    var actuators by remember { mutableStateOf<List<ApiActuator>>(emptyList()) }
    var nutrientsByActuator by remember { mutableStateOf<Map<String, ApiNutrient>>(emptyMap()) }
    var latestCommands by remember { mutableStateOf<Map<String, ApiCommand>>(emptyMap()) }
    var loading by remember { mutableStateOf(true) }
    var loadError by remember { mutableStateOf<String?>(null) }
    var refresh by remember { mutableIntStateOf(0) }
    var commandInFlight by remember { mutableStateOf<Set<String>>(emptySet()) }
    var dosingNutrient by remember { mutableStateOf<ApiNutrient?>(null) }
    var dosingInFlight by remember { mutableStateOf(false) }
    var lastDosing by remember { mutableStateOf<ApiDosingRequest?>(null) }
    var operationMessage by remember { mutableStateOf<String?>(null) }
    val scope = rememberCoroutineScope()

    LaunchedEffect(api, refresh) {
        loading = true
        loadError = null
        try {
            val actuatorCatalog = api.actuators().filter(ApiActuator::active)
            val nutrientCatalog = api.nutrients().filter(ApiNutrient::active)
            val commands = api.commands(limit = 100).items
            actuators = actuatorCatalog
            nutrientsByActuator = nutrientCatalog.associateBy(ApiNutrient::dosingActuatorKey)
            latestCommands = commands.distinctBy(ApiCommand::actuatorKey)
                .associateBy(ApiCommand::actuatorKey)
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (error: Exception) {
            loadError = error.userFacingCode()
        } finally {
            loading = false
        }
    }

    Box(
        Modifier
            .padding(paddingValues)
            .fillMaxSize()
    ) {
        when {
            loading && actuators.isEmpty() -> CircularProgressIndicator(Modifier.align(Alignment.Center))
            loadError != null && actuators.isEmpty() -> ErrorState(loadError!!) { refresh += 1 }
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
                        Text(
                            "Actuadores",
                            style = MaterialTheme.typography.headlineSmall.copy(fontWeight = FontWeight.SemiBold)
                        )
                        OutlinedButton(onClick = { refresh += 1 }, enabled = !loading) {
                            Text("Actualizar")
                        }
                    }
                }
                item { DomainDataStatusBanner(api) }
                loadError?.let { code ->
                    item { StatusMessage("No se pudo actualizar: $code", MaterialTheme.colorScheme.error) }
                }
                operationMessage?.let { message ->
                    item { StatusMessage(message, MaterialTheme.colorScheme.primary) }
                }
                lastDosing?.let { request ->
                    item {
                        StatusMessage(
                            "${request.amountMl} ml · ${dosingLifecycleLabel(request)}",
                            MaterialTheme.colorScheme.secondary
                        )
                    }
                }
                items(actuators, key = ApiActuator::actuatorKey) { actuator ->
                    val nutrient = nutrientsByActuator[actuator.actuatorKey]
                    ActuatorCard(
                        actuator = actuator,
                        command = latestCommands[actuator.actuatorKey],
                        nutrient = nutrient,
                        busy = actuator.actuatorKey in commandInFlight ||
                            (dosingInFlight && dosingNutrient?.dosingActuatorKey == actuator.actuatorKey),
                        canCommand = "command:write" in scopes,
                        canDose = "dosing:write" in scopes,
                        onSetState = { target ->
                            scope.launch {
                                commandInFlight = commandInFlight + actuator.actuatorKey
                                operationMessage = null
                                try {
                                    val command = api.createSetStateCommand(actuator.actuatorKey, target)
                                    latestCommands = latestCommands + (actuator.actuatorKey to command)
                                    operationMessage = commandLifecycleLabel(command)
                                    refresh += 1
                                } catch (cancelled: CancellationException) {
                                    throw cancelled
                                } catch (error: Exception) {
                                    operationMessage = "No se registró el comando: ${error.userFacingCode()}"
                                } finally {
                                    commandInFlight = commandInFlight - actuator.actuatorKey
                                }
                            }
                        },
                        onDose = { dosingNutrient = nutrient }
                    )
                }
            }
        }
    }

    dosingNutrient?.let { nutrient ->
        val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
        ModalBottomSheet(
            onDismissRequest = { if (!dosingInFlight) dosingNutrient = null },
            sheetState = sheetState
        ) {
            DoseSheet(
                nutrient = nutrient,
                busy = dosingInFlight,
                onSubmit = { amountMl ->
                    scope.launch {
                        dosingInFlight = true
                        operationMessage = null
                        try {
                            val request = api.createDosingRequest(nutrient.nutrientKey, amountMl)
                            lastDosing = request
                            operationMessage = dosingLifecycleLabel(request)
                            dosingNutrient = null
                        } catch (cancelled: CancellationException) {
                            throw cancelled
                        } catch (error: Exception) {
                            operationMessage = "No se registró la dosis: ${error.userFacingCode()}"
                        } finally {
                            dosingInFlight = false
                        }
                    }
                },
                onClose = { dosingNutrient = null }
            )
        }
    }
}

@Composable
private fun ActuatorCard(
    actuator: ApiActuator,
    command: ApiCommand?,
    nutrient: ApiNutrient?,
    busy: Boolean,
    canCommand: Boolean,
    canDose: Boolean,
    onSetState: (Boolean) -> Unit,
    onDose: () -> Unit
) {
    var menuOpen by remember { mutableStateOf(false) }
    val colors = MaterialTheme.colorScheme
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(22.dp),
        border = BorderStroke(1.dp, colors.primary.copy(alpha = 0.65f)),
        tonalElevation = 2.dp
    ) {
        Column(
            modifier = Modifier.padding(horizontal = 18.dp, vertical = 14.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    actuator.name,
                    style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.SemiBold),
                    modifier = Modifier.weight(1f)
                )
                Box {
                    FilledTonalIconButton(onClick = { menuOpen = true }, enabled = !busy) {
                        Icon(Icons.Filled.MoreVert, contentDescription = "Acciones")
                    }
                    DropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }) {
                        if (nutrient == null) {
                            val target = !actuator.state.desiredState
                            DropdownMenuItem(
                                text = { Text(if (target) "Solicitar encendido" else "Solicitar apagado") },
                                enabled = canCommand,
                                onClick = { menuOpen = false; onSetState(target) }
                            )
                        } else {
                            DropdownMenuItem(
                                text = { Text("Dosificar ${nutrient.name}") },
                                enabled = canDose,
                                onClick = { menuOpen = false; onDose() }
                            )
                        }
                    }
                }
            }
            Text(desiredStateLabel(actuator.state.desiredState), color = colors.onSurfaceVariant)
            Text(reportedStateLabel(actuator.state.reportedState), color = colors.onSurfaceVariant)
            Text(availabilityLabel(actuator.state.availabilityKey), color = colors.onSurfaceVariant)
            Text(commandLifecycleLabel(command), color = colors.primary)
            if (busy) Text("Registrando intención…", color = colors.secondary)
            if (nutrient == null && !canCommand) {
                Text("Sin permiso para enviar comandos", color = colors.error)
            }
            if (nutrient != null && !canDose) {
                Text("Sin permiso para solicitar dosis", color = colors.error)
            }
        }
    }
}

@Composable
private fun DoseSheet(
    nutrient: ApiNutrient,
    busy: Boolean,
    onSubmit: (Double) -> Unit,
    onClose: () -> Unit
) {
    var amountText by remember { mutableStateOf("10") }
    val amount = amountText.toDoubleOrNull()
    Column(
        Modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp, vertical = 16.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        Text(
            "Dosificar ${nutrient.name}",
            style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.SemiBold)
        )
        Text(
            "Se registrará una intención en mililitros. Edge aplicará la calibración; aceptar no significa ejecución.",
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        OutlinedTextField(
            value = amountText,
            onValueChange = { candidate ->
                if (candidate.length <= 11 && candidate.count { it == '.' } <= 1 &&
                    candidate.all { it.isDigit() || it == '.' }
                ) amountText = candidate
            },
            enabled = !busy,
            label = { Text("Mililitros (ml)") },
            singleLine = true
        )
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Button(
                onClick = { onSubmit(amount!!) },
                enabled = !busy && amount != null && amount > 0.0 && amount <= 9_999_999.999
            ) { Text(if (busy) "Registrando…" else "Registrar dosis") }
            OutlinedButton(onClick = onClose, enabled = !busy) { Text("Cancelar") }
        }
        Spacer(Modifier.height(8.dp))
    }
}

@Composable
private fun ErrorState(code: String, onRetry: () -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(24.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text("No se pudieron cargar los actuadores")
        Spacer(Modifier.height(8.dp))
        Text(code, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Spacer(Modifier.height(12.dp))
        Button(onClick = onRetry) { Text("Reintentar") }
    }
}

@Composable
private fun StatusMessage(message: String, color: Color) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(14.dp),
        color = color.copy(alpha = 0.12f)
    ) {
        Text(message, modifier = Modifier.padding(12.dp), color = color)
    }
}

private fun Exception.userFacingCode(): String =
    (this as? DomainApiException)?.problem?.code ?: "mobile.unexpected_error"
