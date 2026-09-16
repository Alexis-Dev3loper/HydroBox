package com.hydrobox.app.ui.model

import com.hydrobox.app.api.ApiCommand
import com.hydrobox.app.api.ApiDosingRequest

fun desiredStateLabel(value: Boolean): String =
    if (value) "Solicitado: encendido" else "Solicitado: apagado"

fun reportedStateLabel(value: Boolean?): String = when (value) {
    true -> "Reportado: encendido"
    false -> "Reportado: apagado"
    null -> "Reportado: sin confirmar"
}

fun availabilityLabel(key: String): String = when (key) {
    "online" -> "Disponible"
    "offline" -> "Sin conexión"
    else -> "Disponibilidad desconocida"
}

fun commandLifecycleLabel(command: ApiCommand?): String = when (command?.statusKey) {
    "pending" -> "Solicitud registrada"
    "sent" -> "Enviada al Edge; sin ACK"
    "acknowledged" -> if (command.physicalEvidence.completedAt != null) {
        "Ejecución confirmada"
    } else {
        "ACK confirmado"
    }
    "failed" -> "Falló"
    "expired" -> "Expiró"
    else -> "Sin comando reciente"
}

fun dosingLifecycleLabel(request: ApiDosingRequest): String = when (request.statusKey) {
    "pending" -> "Dosis registrada; pendiente de calibración Edge"
    "command_created" -> "Orden física creada; pendiente de confirmación"
    "completed" -> "Dosis confirmada"
    "failed" -> "La dosis falló"
    "expired" -> "La dosis expiró"
    else -> "Estado de dosis desconocido"
}
