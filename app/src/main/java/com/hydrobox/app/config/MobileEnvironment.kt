package com.hydrobox.app.config

import com.hydrobox.app.BuildConfig
import java.net.URI

data class ApiEnvironment(val baseUrl: String) {
    init {
        val uri = runCatching { URI(baseUrl) }.getOrNull()
        require(uri?.scheme == "https" && !uri.host.isNullOrBlank()) {
            "HydroBox API base URL must be an absolute HTTPS URL"
        }
    }
}

data class LegacyMqttEnvironment(
    val enabled: Boolean,
    val host: String,
    val port: Int,
    val username: String?,
    val password: String?
) {
    init {
        require(port in 1..65535) { "MQTT port must be between 1 and 65535" }
        require(!enabled || host.isNotBlank()) { "MQTT host is required when legacy MQTT is enabled" }
    }
}

data class MobileEnvironment(
    val api: ApiEnvironment,
    val legacyMqtt: LegacyMqttEnvironment
)

object HydroBoxEnvironment {
    val current: MobileEnvironment by lazy {
        MobileEnvironment(
            api = ApiEnvironment(BuildConfig.HYDROBOX_API_BASE_URL.trimEnd('/')),
            legacyMqtt = LegacyMqttEnvironment(
                enabled = BuildConfig.HYDROBOX_MQTT_ENABLED,
                host = BuildConfig.HYDROBOX_MQTT_HOST,
                port = BuildConfig.HYDROBOX_MQTT_PORT,
                username = BuildConfig.HYDROBOX_MQTT_USERNAME.ifBlank { null },
                password = BuildConfig.HYDROBOX_MQTT_PASSWORD.ifBlank { null }
            )
        )
    }
}
