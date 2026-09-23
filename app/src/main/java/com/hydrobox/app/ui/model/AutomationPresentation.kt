package com.hydrobox.app.ui.model

import com.hydrobox.app.api.ApiAutomation
import com.hydrobox.app.api.ApiAutomationAction
import com.hydrobox.app.api.ApiAutomationDraft
import com.hydrobox.app.api.ApiAutomationExecution
import com.hydrobox.app.api.ApiAutomationSchedule
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter

enum class AutomationActionChoice {
    SET_STATE,
    RUN_FOR,
    NUTRIENT_DOSE
}

enum class AutomationScheduleChoice {
    ONCE,
    DAILY,
    WEEKDAYS
}

data class AutomationDraftInput(
    val name: String,
    val actionChoice: AutomationActionChoice,
    val actuatorKey: String?,
    val targetState: Boolean,
    val durationSeconds: String,
    val nutrientKey: String?,
    val nutrientActuatorKey: String?,
    val amountMl: String,
    val scheduleChoice: AutomationScheduleChoice,
    val date: String,
    val time: String,
    val isoWeekdays: Set<Int>
)

fun buildAutomationDraft(
    input: AutomationDraftInput,
    zoneId: ZoneId = ZoneId.systemDefault()
): ApiAutomationDraft {
    val name = input.name.trim()
    require(name.isNotEmpty()) { "Escribe un nombre para la automatización." }
    require(name.length <= 100) { "El nombre no puede superar 100 caracteres." }

    val action = when (input.actionChoice) {
        AutomationActionChoice.SET_STATE -> ApiAutomationAction.SetState(
            actuatorKey = input.actuatorKey.requireSelection("Selecciona un actuador."),
            targetState = input.targetState
        )
        AutomationActionChoice.RUN_FOR -> ApiAutomationAction.RunFor(
            actuatorKey = input.actuatorKey.requireSelection("Selecciona un actuador."),
            durationSeconds = input.durationSeconds.toPositiveInt(
                "La duración debe ser un número entero mayor que cero."
            )
        )
        AutomationActionChoice.NUTRIENT_DOSE -> ApiAutomationAction.NutrientDose(
            actuatorKey = input.nutrientActuatorKey.requireSelection(
                "El nutriente seleccionado no tiene actuador de dosificación."
            ),
            nutrientKey = input.nutrientKey.requireSelection("Selecciona un nutriente."),
            amountMl = input.amountMl.toPositiveDouble(
                "La cantidad debe ser un número mayor que cero."
            )
        )
    }

    val localTime = try {
        LocalTime.parse(input.time.trim(), DateTimeFormatter.ofPattern("H:mm"))
    } catch (_: Exception) {
        throw IllegalArgumentException("Escribe la hora en formato HH:mm.")
    }
    val canonicalTime = localTime.format(DateTimeFormatter.ofPattern("HH:mm:ss"))
    val graceSeconds = if (input.actionChoice == AutomationActionChoice.NUTRIENT_DOSE) 30 else 300
    val schedule = when (input.scheduleChoice) {
        AutomationScheduleChoice.ONCE -> {
            val localDate = try {
                LocalDate.parse(input.date.trim(), DateTimeFormatter.ISO_LOCAL_DATE)
            } catch (_: Exception) {
                throw IllegalArgumentException("Escribe la fecha en formato AAAA-MM-DD.")
            }
            ApiAutomationSchedule.Once(
                onceAt = LocalDateTime.of(localDate, localTime).atZone(zoneId).toInstant(),
                misfireGraceSeconds = graceSeconds
            )
        }
        AutomationScheduleChoice.DAILY -> ApiAutomationSchedule.Daily(
            timeOfDay = canonicalTime,
            timezoneName = zoneId.id,
            misfireGraceSeconds = graceSeconds
        )
        AutomationScheduleChoice.WEEKDAYS -> {
            require(input.isoWeekdays.isNotEmpty()) { "Selecciona al menos un día." }
            require(input.isoWeekdays.all { it in 1..7 }) { "Los días seleccionados no son válidos." }
            ApiAutomationSchedule.Weekdays(
                timeOfDay = canonicalTime,
                timezoneName = zoneId.id,
                isoWeekdays = input.isoWeekdays.sorted(),
                misfireGraceSeconds = graceSeconds
            )
        }
    }
    return ApiAutomationDraft(name = name, action = action, schedule = schedule)
}

fun automationDraftInput(automation: ApiAutomation): AutomationDraftInput {
    val (actionChoice, actuatorKey, targetState, durationSeconds, nutrientKey,
        nutrientActuatorKey, amountMl) = when (val action = automation.action) {
        is ApiAutomationAction.SetState -> ActionInput(
            AutomationActionChoice.SET_STATE,
            action.actuatorKey,
            action.targetState,
            "60",
            null,
            null,
            "10"
        )
        is ApiAutomationAction.RunFor -> ActionInput(
            AutomationActionChoice.RUN_FOR,
            action.actuatorKey,
            true,
            action.durationSeconds.toString(),
            null,
            null,
            "10"
        )
        is ApiAutomationAction.NutrientDose -> ActionInput(
            AutomationActionChoice.NUTRIENT_DOSE,
            null,
            true,
            "60",
            action.nutrientKey,
            action.actuatorKey,
            formatAutomationNumber(action.amountMl)
        )
    }
    val (scheduleChoice, date, time, weekdays) = when (val schedule = automation.schedule) {
        is ApiAutomationSchedule.Once -> {
            val local = schedule.onceAt.atZone(ZoneId.systemDefault())
            ScheduleInput(
                AutomationScheduleChoice.ONCE,
                local.toLocalDate().toString(),
                local.toLocalTime().format(DateTimeFormatter.ofPattern("HH:mm")),
                emptySet()
            )
        }
        is ApiAutomationSchedule.Daily -> ScheduleInput(
            AutomationScheduleChoice.DAILY,
            LocalDate.now().plusDays(1).toString(),
            schedule.timeOfDay.take(5),
            emptySet()
        )
        is ApiAutomationSchedule.Weekdays -> ScheduleInput(
            AutomationScheduleChoice.WEEKDAYS,
            LocalDate.now().plusDays(1).toString(),
            schedule.timeOfDay.take(5),
            schedule.isoWeekdays.toSet()
        )
    }
    return AutomationDraftInput(
        name = automation.name,
        actionChoice = actionChoice,
        actuatorKey = actuatorKey,
        targetState = targetState,
        durationSeconds = durationSeconds,
        nutrientKey = nutrientKey,
        nutrientActuatorKey = nutrientActuatorKey,
        amountMl = amountMl,
        scheduleChoice = scheduleChoice,
        date = date,
        time = time,
        isoWeekdays = weekdays
    )
}

fun automationActionLabel(
    action: ApiAutomationAction,
    actuatorNames: Map<String, String> = emptyMap(),
    nutrientNames: Map<String, String> = emptyMap()
): String = when (action) {
    is ApiAutomationAction.SetState -> {
        val actuator = actuatorNames[action.actuatorKey] ?: action.actuatorKey
        "$actuator · ${if (action.targetState) "encender" else "apagar"}"
    }
    is ApiAutomationAction.RunFor -> {
        val actuator = actuatorNames[action.actuatorKey] ?: action.actuatorKey
        "$actuator · ${action.durationSeconds} s"
    }
    is ApiAutomationAction.NutrientDose -> {
        val nutrient = nutrientNames[action.nutrientKey] ?: action.nutrientKey
        "$nutrient · ${formatAutomationNumber(action.amountMl)} ml"
    }
}

fun automationScheduleLabel(schedule: ApiAutomationSchedule): String = when (schedule) {
    is ApiAutomationSchedule.Once ->
        "Una vez · ${schedule.onceAt.atZone(ZoneId.systemDefault()).format(DISPLAY_DATE_TIME)}"
    is ApiAutomationSchedule.Daily ->
        "Diario · ${schedule.timeOfDay.take(5)}"
    is ApiAutomationSchedule.Weekdays -> {
        val days = schedule.isoWeekdays.joinToString(", ") { ISO_DAY_LABELS.getValue(it) }
        "$days · ${schedule.timeOfDay.take(5)}"
    }
}

fun automationNextRunLabel(automation: ApiAutomation): String =
    automation.nextRunAt?.atZone(ZoneId.systemDefault())?.format(DISPLAY_DATE_TIME)
        ?.let { "Próxima ejecución: $it" }
        ?: "Sin próxima ejecución calculada"

fun automationExecutionStatusLabel(execution: ApiAutomationExecution): String =
    when (execution.statusKey) {
        "running" -> "En curso"
        "dispatched" -> "Comando registrado · ACK físico pendiente"
        "skipped" -> "Omitida"
        "failed" -> "Fallida"
        else -> "Estado no reconocido"
    }

private fun String?.requireSelection(message: String): String =
    this?.trim()?.takeIf(String::isNotEmpty) ?: throw IllegalArgumentException(message)

private fun String.toPositiveInt(message: String): Int =
    trim().toIntOrNull()?.takeIf { it > 0 } ?: throw IllegalArgumentException(message)

private fun String.toPositiveDouble(message: String): Double =
    trim().replace(',', '.').toDoubleOrNull()
        ?.takeIf { it > 0.0 && it <= 9_999_999.999 && it.isFinite() }
        ?: throw IllegalArgumentException(message)

private fun formatAutomationNumber(value: Double): String =
    String.format(java.util.Locale.US, "%.3f", value).trimEnd('0').trimEnd('.')

private val DISPLAY_DATE_TIME: DateTimeFormatter = DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm")
private val ISO_DAY_LABELS = mapOf(
    1 to "Lun",
    2 to "Mar",
    3 to "Mié",
    4 to "Jue",
    5 to "Vie",
    6 to "Sáb",
    7 to "Dom"
)

private data class ActionInput(
    val actionChoice: AutomationActionChoice,
    val actuatorKey: String?,
    val targetState: Boolean,
    val durationSeconds: String,
    val nutrientKey: String?,
    val nutrientActuatorKey: String?,
    val amountMl: String
)

private data class ScheduleInput(
    val scheduleChoice: AutomationScheduleChoice,
    val date: String,
    val time: String,
    val weekdays: Set<Int>
)
