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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
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
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.hydrobox.app.api.ApiActuator
import com.hydrobox.app.api.ApiAutomation
import com.hydrobox.app.api.ApiAutomationDraft
import com.hydrobox.app.api.ApiNutrient
import com.hydrobox.app.api.DomainApiException
import com.hydrobox.app.api.HydroDomainApi
import com.hydrobox.app.ui.components.DomainDataStatusBanner
import com.hydrobox.app.ui.model.AutomationActionChoice
import com.hydrobox.app.ui.model.AutomationDraftInput
import com.hydrobox.app.ui.model.AutomationScheduleChoice
import com.hydrobox.app.ui.model.automationActionLabel
import com.hydrobox.app.ui.model.automationNextRunLabel
import com.hydrobox.app.ui.model.automationScheduleLabel
import com.hydrobox.app.ui.model.buildAutomationDraft
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch
import java.time.LocalDate

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AutomationScreen(
    paddingValues: PaddingValues,
    api: HydroDomainApi,
    canRead: Boolean,
    canWrite: Boolean,
    offlineMode: Boolean
) {
    var automations by remember { mutableStateOf<List<ApiAutomation>>(emptyList()) }
    var actuators by remember { mutableStateOf<List<ApiActuator>>(emptyList()) }
    var nutrients by remember { mutableStateOf<List<ApiNutrient>>(emptyList()) }
    var loading by remember { mutableStateOf(canRead) }
    var loadError by remember { mutableStateOf<String?>(null) }
    var operationMessage by remember { mutableStateOf<String?>(null) }
    var refresh by remember { mutableIntStateOf(0) }
    var busyRules by remember { mutableStateOf<Set<String>>(emptySet()) }
    var showCreate by remember { mutableStateOf(false) }
    var creating by remember { mutableStateOf(false) }
    var deleteTarget by remember { mutableStateOf<ApiAutomation?>(null) }
    val scope = rememberCoroutineScope()

    LaunchedEffect(api, canRead, refresh) {
        if (!canRead) {
            loading = false
            return@LaunchedEffect
        }
        loading = true
        loadError = null
        try {
            automations = api.automations(limit = 100).items.filter { it.deletedAt == null }
            actuators = api.actuators().filter(ApiActuator::active)
            nutrients = api.nutrients().filter(ApiNutrient::active)
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (error: Exception) {
            loadError = automationProblemMessage(error)
        } finally {
            loading = false
        }
    }

    fun mutate(rule: ApiAutomation, enabled: Boolean) {
        scope.launch {
            busyRules = busyRules + rule.ruleUuid
            operationMessage = null
            try {
                val updated = api.updateAutomation(
                    ruleUuid = rule.ruleUuid,
                    version = rule.version,
                    enabled = enabled
                )
                automations = automations.map { if (it.ruleUuid == updated.ruleUuid) updated else it }
                operationMessage = if (enabled) "Automatización activada." else "Automatización pausada."
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (error: Exception) {
                operationMessage = automationMutationMessage(error)
                if (error.isVersionConflict()) refresh += 1
            } finally {
                busyRules = busyRules - rule.ruleUuid
            }
        }
    }

    Box(
        Modifier
            .padding(paddingValues)
            .fillMaxSize()
    ) {
        when {
            !canRead -> AutomationUnavailableState(
                title = "Sin acceso a automatizaciones",
                detail = "Tu cuenta no tiene el permiso automation:read."
            )
            loading && automations.isEmpty() -> CircularProgressIndicator(Modifier.align(Alignment.Center))
            loadError != null && automations.isEmpty() -> AutomationErrorState(loadError!!) { refresh += 1 }
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
                                "Automatizaciones",
                                style = MaterialTheme.typography.headlineSmall.copy(
                                    fontWeight = FontWeight.SemiBold
                                )
                            )
                            Text(
                                "Calendarios deterministas del sitio",
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
                        AutomationNotice(
                            "Modo sin conexión: puedes consultar el cache, pero no modificar reglas.",
                            warning = true
                        )
                    }
                }
                loadError?.let { message ->
                    item { AutomationNotice("No se pudo actualizar: $message", warning = true) }
                }
                operationMessage?.let { message ->
                    item { AutomationNotice(message, warning = message.startsWith("No ") || message.startsWith("La regla")) }
                }
                item {
                    Button(
                        onClick = { showCreate = true },
                        enabled = canWrite && !offlineMode && !loading,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Icon(Icons.Filled.Add, contentDescription = null)
                        Spacer(Modifier.width(8.dp))
                        Text("Nueva automatización")
                    }
                }
                if (automations.isEmpty()) {
                    item {
                        AutomationUnavailableState(
                            title = "Aún no hay automatizaciones",
                            detail = if (canWrite && !offlineMode) {
                                "Crea una regla. Permanecerá pausada hasta que la actives explícitamente."
                            } else {
                                "No hay reglas guardadas disponibles para esta sesión."
                            }
                        )
                    }
                } else {
                    items(automations, key = ApiAutomation::ruleUuid) { rule ->
                        AutomationCard(
                            automation = rule,
                            actuatorNames = actuators.associate { it.actuatorKey to it.name },
                            nutrientNames = nutrients.associate { it.nutrientKey to it.name },
                            busy = rule.ruleUuid in busyRules,
                            canWrite = canWrite && !offlineMode,
                            onToggle = { mutate(rule, !rule.enabled) },
                            onDelete = { deleteTarget = rule }
                        )
                    }
                }
            }
        }
    }

    if (showCreate) {
        val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
        ModalBottomSheet(
            onDismissRequest = { if (!creating) showCreate = false },
            sheetState = sheetState
        ) {
            AutomationCreateForm(
                actuators = actuators,
                nutrients = nutrients,
                busy = creating,
                onClose = { showCreate = false },
                onSubmit = { draft ->
                    scope.launch {
                        creating = true
                        operationMessage = null
                        try {
                            val created = api.createAutomation(draft)
                            automations = listOf(created) + automations
                            operationMessage = "Automatización creada y pausada. Actívala cuando estés listo."
                            showCreate = false
                        } catch (cancelled: CancellationException) {
                            throw cancelled
                        } catch (error: Exception) {
                            operationMessage = automationMutationMessage(error)
                        } finally {
                            creating = false
                        }
                    }
                }
            )
        }
    }

    deleteTarget?.let { rule ->
        AlertDialog(
            onDismissRequest = { if (rule.ruleUuid !in busyRules) deleteTarget = null },
            title = { Text("Eliminar automatización") },
            text = { Text("Se eliminará «${rule.name}». Esta acción no ejecuta ni cancela comandos físicos.") },
            confirmButton = {
                TextButton(
                    enabled = rule.ruleUuid !in busyRules,
                    onClick = {
                        scope.launch {
                            busyRules = busyRules + rule.ruleUuid
                            operationMessage = null
                            try {
                                api.deleteAutomation(rule.ruleUuid, rule.version)
                                automations = automations.filterNot { it.ruleUuid == rule.ruleUuid }
                                operationMessage = "Automatización eliminada."
                                deleteTarget = null
                            } catch (cancelled: CancellationException) {
                                throw cancelled
                            } catch (error: Exception) {
                                operationMessage = automationMutationMessage(error)
                                deleteTarget = null
                                if (error.isVersionConflict()) refresh += 1
                            } finally {
                                busyRules = busyRules - rule.ruleUuid
                            }
                        }
                    }
                ) { Text("Eliminar") }
            },
            dismissButton = {
                TextButton(
                    enabled = rule.ruleUuid !in busyRules,
                    onClick = { deleteTarget = null }
                ) { Text("Cancelar") }
            }
        )
    }
}

@Composable
private fun AutomationCard(
    automation: ApiAutomation,
    actuatorNames: Map<String, String>,
    nutrientNames: Map<String, String>,
    busy: Boolean,
    canWrite: Boolean,
    onToggle: () -> Unit,
    onDelete: () -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Column(
            Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text(
                        automation.name,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold
                    )
                    Text(
                        if (automation.enabled) "Activa" else "Pausada",
                        color = if (automation.enabled) {
                            MaterialTheme.colorScheme.primary
                        } else {
                            MaterialTheme.colorScheme.onSurfaceVariant
                        }
                    )
                }
                AssistChip(
                    onClick = {},
                    enabled = false,
                    label = { Text("v${automation.version}") }
                )
            }
            HorizontalDivider()
            Text(
                automationActionLabel(automation.action, actuatorNames, nutrientNames),
                style = MaterialTheme.typography.bodyLarge
            )
            Text(
                automationScheduleLabel(automation.schedule),
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Text(
                automationNextRunLabel(automation),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                FilledTonalButton(
                    onClick = onToggle,
                    enabled = canWrite && !busy,
                    modifier = Modifier.weight(1f)
                ) {
                    Icon(
                        if (automation.enabled) Icons.Filled.Pause else Icons.Filled.PlayArrow,
                        contentDescription = null
                    )
                    Spacer(Modifier.width(6.dp))
                    Text(if (automation.enabled) "Pausar" else "Activar")
                }
                OutlinedButton(onClick = onDelete, enabled = canWrite && !busy) {
                    Icon(Icons.Filled.Delete, contentDescription = "Eliminar")
                }
            }
            if (!canWrite) {
                Text(
                    "Solo lectura",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

@Composable
private fun AutomationCreateForm(
    actuators: List<ApiActuator>,
    nutrients: List<ApiNutrient>,
    busy: Boolean,
    onClose: () -> Unit,
    onSubmit: (ApiAutomationDraft) -> Unit
) {
    var name by remember { mutableStateOf("") }
    var actionChoice by remember { mutableStateOf(AutomationActionChoice.SET_STATE) }
    var actuatorKey by remember(actuators) { mutableStateOf(actuators.firstOrNull()?.actuatorKey) }
    var targetState by remember { mutableStateOf(true) }
    var durationSeconds by remember { mutableStateOf("60") }
    var nutrientKey by remember(nutrients) { mutableStateOf(nutrients.firstOrNull()?.nutrientKey) }
    var amountMl by remember { mutableStateOf("10") }
    var scheduleChoice by remember { mutableStateOf(AutomationScheduleChoice.DAILY) }
    var date by remember { mutableStateOf(LocalDate.now().plusDays(1).toString()) }
    var time by remember { mutableStateOf("08:00") }
    var weekdays by remember { mutableStateOf(setOf(1, 2, 3, 4, 5)) }
    var validationError by remember { mutableStateOf<String?>(null) }
    val selectedNutrient = nutrients.firstOrNull { it.nutrientKey == nutrientKey }

    Column(
        Modifier
            .fillMaxWidth()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 20.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        Text(
            "Nueva automatización",
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.SemiBold
        )
        Text(
            "La regla se crea pausada. Activarla es una acción posterior y no equivale a un ACK físico.",
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        OutlinedTextField(
            value = name,
            onValueChange = { if (it.length <= 100) name = it },
            modifier = Modifier.fillMaxWidth(),
            label = { Text("Nombre") },
            singleLine = true,
            enabled = !busy
        )
        FormSectionTitle("Acción")
        ChoiceRow(
            choices = listOf(
                AutomationActionChoice.SET_STATE to "Encender/apagar",
                AutomationActionChoice.RUN_FOR to "Ejecutar por tiempo",
                AutomationActionChoice.NUTRIENT_DOSE to "Dosificar"
            ),
            selected = actionChoice,
            enabled = !busy,
            onSelected = { actionChoice = it }
        )
        if (actionChoice == AutomationActionChoice.NUTRIENT_DOSE) {
            FormSectionTitle("Nutriente")
            CatalogChoiceRow(
                values = nutrients.map { it.nutrientKey to it.name },
                selected = nutrientKey,
                enabled = !busy,
                emptyMessage = "No hay nutrientes activos.",
                onSelected = { nutrientKey = it }
            )
            OutlinedTextField(
                value = amountMl,
                onValueChange = { amountMl = it.take(12) },
                modifier = Modifier.fillMaxWidth(),
                label = { Text("Cantidad (ml)") },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                singleLine = true,
                enabled = !busy
            )
        } else {
            FormSectionTitle("Actuador")
            CatalogChoiceRow(
                values = actuators.map { it.actuatorKey to it.name },
                selected = actuatorKey,
                enabled = !busy,
                emptyMessage = "No hay actuadores activos.",
                onSelected = { actuatorKey = it }
            )
            if (actionChoice == AutomationActionChoice.SET_STATE) {
                ChoiceRow(
                    choices = listOf(true to "Encender", false to "Apagar"),
                    selected = targetState,
                    enabled = !busy,
                    onSelected = { targetState = it }
                )
            } else {
                OutlinedTextField(
                    value = durationSeconds,
                    onValueChange = { if (it.all(Char::isDigit)) durationSeconds = it.take(9) },
                    modifier = Modifier.fillMaxWidth(),
                    label = { Text("Duración (segundos)") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    singleLine = true,
                    enabled = !busy
                )
            }
        }
        FormSectionTitle("Calendario")
        ChoiceRow(
            choices = listOf(
                AutomationScheduleChoice.ONCE to "Una vez",
                AutomationScheduleChoice.DAILY to "Diario",
                AutomationScheduleChoice.WEEKDAYS to "Días"
            ),
            selected = scheduleChoice,
            enabled = !busy,
            onSelected = { scheduleChoice = it }
        )
        if (scheduleChoice == AutomationScheduleChoice.ONCE) {
            OutlinedTextField(
                value = date,
                onValueChange = { date = it.take(10) },
                modifier = Modifier.fillMaxWidth(),
                label = { Text("Fecha (AAAA-MM-DD)") },
                singleLine = true,
                enabled = !busy
            )
        }
        OutlinedTextField(
            value = time,
            onValueChange = { time = it.take(5) },
            modifier = Modifier.fillMaxWidth(),
            label = { Text("Hora (HH:mm)") },
            singleLine = true,
            enabled = !busy
        )
        if (scheduleChoice == AutomationScheduleChoice.WEEKDAYS) {
            LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                items((1..7).toList()) { day ->
                    val label = listOf("Lun", "Mar", "Mié", "Jue", "Vie", "Sáb", "Dom")[day - 1]
                    FilterChip(
                        selected = day in weekdays,
                        onClick = {
                            weekdays = if (day in weekdays) weekdays - day else weekdays + day
                        },
                        enabled = !busy,
                        label = { Text(label) }
                    )
                }
            }
        }
        validationError?.let {
            Text(it, color = MaterialTheme.colorScheme.error)
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Button(
                enabled = !busy,
                onClick = {
                    try {
                        validationError = null
                        onSubmit(
                            buildAutomationDraft(
                                AutomationDraftInput(
                                    name = name,
                                    actionChoice = actionChoice,
                                    actuatorKey = actuatorKey,
                                    targetState = targetState,
                                    durationSeconds = durationSeconds,
                                    nutrientKey = nutrientKey,
                                    nutrientActuatorKey = selectedNutrient?.dosingActuatorKey,
                                    amountMl = amountMl,
                                    scheduleChoice = scheduleChoice,
                                    date = date,
                                    time = time,
                                    isoWeekdays = weekdays
                                )
                            )
                        )
                    } catch (error: IllegalArgumentException) {
                        validationError = error.message ?: "Revisa los datos ingresados."
                    }
                }
            ) { Text(if (busy) "Creando…" else "Crear pausada") }
            OutlinedButton(onClick = onClose, enabled = !busy) { Text("Cancelar") }
        }
        Spacer(Modifier.height(20.dp))
    }
}

@Composable
private fun <T> ChoiceRow(
    choices: List<Pair<T, String>>,
    selected: T,
    enabled: Boolean,
    onSelected: (T) -> Unit
) {
    LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        items(choices) { (value, label) ->
            FilterChip(
                selected = value == selected,
                onClick = { onSelected(value) },
                enabled = enabled,
                label = { Text(label) }
            )
        }
    }
}

@Composable
private fun CatalogChoiceRow(
    values: List<Pair<String, String>>,
    selected: String?,
    enabled: Boolean,
    emptyMessage: String,
    onSelected: (String) -> Unit
) {
    if (values.isEmpty()) {
        Text(emptyMessage, color = MaterialTheme.colorScheme.error)
    } else {
        LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            items(values, key = { it.first }) { (key, label) ->
                FilterChip(
                    selected = key == selected,
                    onClick = { onSelected(key) },
                    enabled = enabled,
                    label = { Text(label) }
                )
            }
        }
    }
}

@Composable
private fun FormSectionTitle(text: String) {
    Text(text, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
}

@Composable
private fun AutomationNotice(message: String, warning: Boolean) {
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
private fun AutomationUnavailableState(title: String, detail: String) {
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
private fun AutomationErrorState(message: String, onRetry: () -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Text("No se pudieron cargar las automatizaciones", fontWeight = FontWeight.SemiBold)
        Spacer(Modifier.height(8.dp))
        Text(message, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Spacer(Modifier.height(12.dp))
        Button(onClick = onRetry) { Text("Reintentar") }
    }
}

private fun automationProblemMessage(error: Exception): String =
    (error as? DomainApiException)?.problem?.code ?: "error de conexión"

private fun automationMutationMessage(error: Exception): String = when {
    error.isVersionConflict() -> "La regla cambió en otro cliente. Se recargó su versión actual."
    error is DomainApiException && error.problem.status == 403 ->
        "No tienes permiso para modificar automatizaciones."
    else -> "No se pudo completar la operación: ${automationProblemMessage(error)}"
}

private fun Exception.isVersionConflict(): Boolean =
    this is DomainApiException && problem.status == 412
