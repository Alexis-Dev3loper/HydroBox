package com.hydrobox.app.ui.model

import com.hydrobox.app.api.ApiAutomationExecution
import com.hydrobox.app.api.ApiCommand
import com.hydrobox.app.api.ApiPhysicalEvidence
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Instant

class HistoryPresentationTest {
    @Test
    fun mergesOnlyRealCommandsAndExecutionsInDescendingOrder() {
        val events = buildHistoryEvents(
            commands = listOf(command("sent", "2026-09-22T10:00:00Z")),
            executions = listOf(execution("failed", "2026-09-22T11:00:00Z")),
            actuatorNames = mapOf("fan" to "Ventilador"),
            automationNames = mapOf(RULE_UUID to "Ventilación diaria")
        )

        assertEquals(2, events.size)
        assertEquals("Automatización · Ventilación diaria", events[0].title)
        assertTrue(events[0].failed)
        assertEquals("Comando · Ventilador", events[1].title)
        assertFalse(events[1].failed)
    }

    @Test
    fun sentCommandDoesNotClaimAcknowledgement() {
        assertEquals("Enviado · ACK físico pendiente", commandStatusLabel("sent"))
        assertEquals("ACK físico recibido", commandStatusLabel("acknowledged"))
    }

    private fun command(status: String, requestedAt: String) = ApiCommand(
        commandUuid = "11111111-1111-4111-8111-111111111111",
        actuatorKey = "fan",
        commandKey = "set_state",
        targetState = true,
        durationMs = null,
        statusKey = status,
        requestedAt = Instant.parse(requestedAt),
        sentAt = if (status == "sent") Instant.parse(requestedAt).plusSeconds(1) else null,
        acknowledgedAt = null,
        failedAt = null,
        expiresAt = Instant.parse(requestedAt).plusSeconds(300),
        errorCode = null,
        errorMessage = null,
        physicalEvidence = ApiPhysicalEvidence(null, null, null, null)
    )

    private fun execution(status: String, scheduledFor: String) = ApiAutomationExecution(
        executionUuid = "22222222-2222-4222-8222-222222222222",
        ruleUuid = RULE_UUID,
        scheduledFor = Instant.parse(scheduledFor),
        statusKey = status,
        commandUuid = null,
        errorMessage = if (status == "failed") "failure" else null
    )

    private companion object {
        const val RULE_UUID = "33333333-3333-4333-8333-333333333333"
    }
}
