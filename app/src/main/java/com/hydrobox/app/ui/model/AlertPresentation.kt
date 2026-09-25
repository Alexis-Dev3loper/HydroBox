package com.hydrobox.app.ui.model

fun alertSeverityLabel(severity: String): String = when (severity) {
    "info" -> "Informativa"
    "warning" -> "Advertencia"
    "critical" -> "Crítica"
    else -> "Desconocida"
}

fun alertStatusLabel(status: String): String = when (status) {
    "open" -> "Abierta"
    "acknowledged" -> "Confirmada"
    "resolved" -> "Resuelta"
    else -> "Desconocida"
}

fun alertSubjectLabel(subjectType: String, subjectKey: String): String {
    val type = when (subjectType) {
        "sensor" -> "Sensor"
        "device" -> "Dispositivo"
        "actuator" -> "Actuador"
        "command" -> "Comando"
        "dosing" -> "Dosificación"
        "automation" -> "Automatización"
        else -> "Origen"
    }
    return "$type: $subjectKey"
}
