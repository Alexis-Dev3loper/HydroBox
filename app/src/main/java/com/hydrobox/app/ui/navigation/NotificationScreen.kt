package com.hydrobox.app.ui.navigation

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.hydrobox.app.ui.components.HydroCard

@Composable
fun NotificationScreen(paddingValues: PaddingValues) {
    Column(
        Modifier
            .padding(paddingValues)
            .fillMaxSize()
            .padding(16.dp)
    ) {
        Text("Notificaciones", style = MaterialTheme.typography.headlineSmall)
        HydroCard(
            title = "Alertas todavía no disponibles",
            subtitle = "Esta pantalla no muestra datos de demostración.",
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 16.dp)
        ) {
            Text(
                "El dominio durable de alertas y su confirmación se habilitarán cuando HD-016 defina productores, deduplicación, lifecycle y persistencia. Las lecturas fuera de rango no se convierten aquí en alertas ni reglas automáticas.",
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}
