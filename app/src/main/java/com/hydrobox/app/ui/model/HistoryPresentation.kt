package com.hydrobox.app.ui.model

import com.hydrobox.app.api.ApiAutomationExecution
import com.hydrobox.app.api.ApiCommand
import java.time.Instant

enum class HistoryEventKind {
    COMMAND,
    AUTOMATION
}

data class HistoryEvent(
    val stableKey: String,
    val occurredAt: Instant,
    val title: String,
    val detail: String,
    val kind: HistoryEventKind,
    val failed: Boolean
)

fun buildHistoryEvents(
    commands: List<ApiCommand>,
    executions: List<ApiAutomationExecution>,
    actuatorNames: Map<String, String> = emptyMap(),
    automationNames: Map<String, String> = emptyMap(),
    limit: Int = 20
): List<HistoryEvent> {
    require(limit > 0) { "History limit must be positive" }
    val commandEvents = commands.map { command ->
        HistoryEvent(
            stableKey = "command:${command.commandUuid}",
            occurredAt = command.failedAt ?: command.acknowledgedAt ?: command.sentAt ?: command.requestedAt,
            title = "Comando · ${actuatorNames[command.actuatorKey] ?: command.actuatorKey}",
            detail = commandStatusLabel(command.statusKey),
            kind = HistoryEventKind.COMMAND,
            failed = command.statusKey in setOf("failed", "expired")
        )
    }
    val automationEvents = executions.map { execution ->
        HistoryEvent(
            stableKey = "automation:${execution.executionUuid}",
            occurredAt = execution.scheduledFor,
            title = automationNames[execution.ruleUuid]
                ?.let { "Automatización · $it" }
                ?: "Automatización · ${execution.ruleUuid.take(8)}",
            detail = automationExecutionStatusLabel(execution),
            kind = HistoryEventKind.AUTOMATION,
            failed = execution.statusKey == "failed"
        )
    }
    return (commandEvents + automationEvents)
        .sortedWith(compareByDescending<HistoryEvent> { it.occurredAt }.thenBy { it.stableKey })
        .take(limit)
}

fun commandStatusLabel(statusKey: String): String = when (statusKey) {
    "pending" -> "Pendiente de envío"
    "sent" -> "Enviado · ACK físico pendiente"
    "acknowledged" -> "ACK físico recibido"
    "failed" -> "Fallido"
    "expired" -> "Expirado"
    else -> "Estado no reconocido"
}
