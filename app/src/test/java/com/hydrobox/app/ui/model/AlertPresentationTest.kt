package com.hydrobox.app.ui.model

import org.junit.Assert.assertEquals
import org.junit.Test

class AlertPresentationTest {
    @Test
    fun mapsDurableSeverityAndLifecycleWithoutCallingAckResolution() {
        assertEquals("Crítica", alertSeverityLabel("critical"))
        assertEquals("Abierta", alertStatusLabel("open"))
        assertEquals("Confirmada", alertStatusLabel("acknowledged"))
        assertEquals("Resuelta", alertStatusLabel("resolved"))
    }

    @Test
    fun identifiesTheCanonicalAlertSubject() {
        assertEquals("Dispositivo: edge_main", alertSubjectLabel("device", "edge_main"))
        assertEquals("Sensor: ph", alertSubjectLabel("sensor", "ph"))
    }
}
