package com.hydrobox.app.api

import java.net.URI
import java.util.Locale

/** Media destinations are HTTPS origins, never principals or persistent credentials. */
object CameraMediaPolicy {
    fun origin(value: String): String {
        require(value.isNotEmpty() && value.length <= 2048)
        require(value.none { it <= ' ' || it == '\u007f' || it == '\\' })
        val uri = try { URI(value) } catch (_: Exception) {
            throw IllegalArgumentException("Invalid camera URL")
        }
        require(uri.scheme.equals("https", ignoreCase = true) && !uri.host.isNullOrBlank())
        require(uri.rawUserInfo == null && uri.rawQuery == null && uri.rawFragment == null)
        require(uri.port == -1 || uri.port in 1..65535)
        val host = uri.host.lowercase(Locale.ROOT)
        val port = if (uri.port == -1 || uri.port == 443) "" else ":${uri.port}"
        return "https://$host$port"
    }

    fun validateToken(token: String) {
        require(token.length in 32..8192 && token.all { it in '!'..'~' })
    }
}
