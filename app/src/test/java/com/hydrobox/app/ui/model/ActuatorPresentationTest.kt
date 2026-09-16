package com.hydrobox.app.ui.model

import com.hydrobox.app.api.ApiCommand
import com.hydrobox.app.api.ApiPhysicalEvidence
import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.Instant

class ActuatorPresentationTest {
    @Test
    fun keepsDesiredReportedAndAvailabilitySeparate() {
        assertEquals("Solicitado: encendido", desiredStateLabel(true))
        assertEquals("Reportado: sin confirmar", reportedStateLabel(null))
        assertEquals("Sin conexión", availabilityLabel("offline"))
    }

    @Test
    fun sentDoesNotRenderAsAckAndAcknowledgedRequiresItsOwnLabel() {
        assertEquals("Enviada al Edge; sin ACK", commandLifecycleLabel(command("sent", null)))
        assertEquals(
            "Ejecución confirmada",
            commandLifecycleLabel(command("acknowledged", Instant.parse("2026-09-15T12:00:03Z")))
        )
    }

    private fun command(status: String, completedAt: Instant?) = ApiCommand(
        commandUuid = "44444444-4444-4444-8444-444444444444",
        actuatorKey = "fan",
        commandKey = "set_state",
        targetState = true,
        durationMs = null,
        statusKey = status,
        requestedAt = Instant.parse("2026-09-15T12:00:00Z"),
        sentAt = if (status == "sent") Instant.parse("2026-09-15T12:00:01Z") else null,
        acknowledgedAt = completedAt,
        failedAt = null,
        expiresAt = Instant.parse("2026-09-15T12:05:00Z"),
        errorCode = null,
        errorMessage = null,
        physicalEvidence = ApiPhysicalEvidence(null, completedAt, "output_applied", true)
    )
}
