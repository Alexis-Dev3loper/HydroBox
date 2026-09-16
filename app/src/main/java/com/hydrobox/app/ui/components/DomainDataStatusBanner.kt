package com.hydrobox.app.ui.components

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.hydrobox.app.api.DomainDataSource
import com.hydrobox.app.api.HydroDomainApi
import java.time.ZoneId
import java.time.format.DateTimeFormatter

@Composable
fun DomainDataStatusBanner(api: HydroDomainApi) {
    val status by api.dataStatus.collectAsState()
    val storedAt = status.cacheStoredAt?.atZone(ZoneId.systemDefault())
        ?.format(DateTimeFormatter.ofPattern("dd/MM HH:mm"))
    val (message, isWarning) = when (status.source) {
        DomainDataSource.CACHE_FRESH ->
            "Sin conexión · datos guardados${storedAt?.let { " el $it" }.orEmpty()}" to false
        DomainDataSource.CACHE_STALE ->
            "Sin conexión · datos antiguos${storedAt?.let { " del $it" }.orEmpty()}" to true
        DomainDataSource.UNAVAILABLE ->
            "Datos no disponibles · ${status.problemCode ?: "error de conexión"}" to true
        DomainDataSource.IDLE, DomainDataSource.LIVE -> return
    }
    val color = if (isWarning) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.secondary
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp),
        color = color.copy(alpha = 0.12f)
    ) {
        Text(
            text = message,
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
            color = color,
            style = MaterialTheme.typography.bodySmall
        )
    }
}
