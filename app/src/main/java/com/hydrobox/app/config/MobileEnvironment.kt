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

data class MobileEnvironment(
    val api: ApiEnvironment
)

object HydroBoxEnvironment {
    val current: MobileEnvironment by lazy {
        MobileEnvironment(
            api = ApiEnvironment(BuildConfig.HYDROBOX_API_BASE_URL.trimEnd('/'))
        )
    }
}
