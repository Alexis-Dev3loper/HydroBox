package com.hydrobox.app.ui.navigation

import android.annotation.SuppressLint
import android.graphics.Color
import android.net.http.SslError
import android.webkit.SslErrorHandler
import android.webkit.WebResourceRequest
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.weight
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.unit.dp
import com.hydrobox.app.api.ApiCameraSession
import com.hydrobox.app.api.DomainApiException
import com.hydrobox.app.api.HydroDomainApi
import com.hydrobox.app.ui.camera.CameraWebDocument
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import java.time.Duration
import java.time.Instant

@Composable
fun CameraScreen(
    paddingValues: PaddingValues,
    api: HydroDomainApi,
    canRead: Boolean,
    offlineMode: Boolean
) {
    var session by remember { mutableStateOf<ApiCameraSession?>(null) }
    var loading by remember { mutableStateOf(false) }
    var problem by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(canRead, offlineMode) {
        if (!canRead || offlineMode) session = null
    }

    LaunchedEffect(session?.sessionUuid) {
        val active = session ?: return@LaunchedEffect
        try {
            val remaining = Duration.between(Instant.now(), active.expiresAt).toMillis().coerceAtLeast(1)
            delay(remaining)
            session = null
            problem = "La sesión de cámara expiró. Puedes conectarte de nuevo."
        } finally {
            withContext(NonCancellable) {
                runCatching { api.closeCameraSession(active.sessionUuid) }
            }
        }
    }

    Column(
        modifier = Modifier.fillMaxSize().padding(paddingValues).padding(20.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Text("Cámara del cultivo", style = MaterialTheme.typography.headlineSmall)
        Text(
            "Vídeo en vivo mediante una sesión cifrada y temporal. La app no graba ni conserva el token de reproducción.",
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )

        when {
            !canRead -> CameraUnavailable("Tu cuenta no tiene permiso para ver la cámara.")
            offlineMode -> CameraUnavailable("La cámara requiere conexión con la API de HydroBox.")
            loading -> Box(Modifier.fillMaxWidth().weight(1f), contentAlignment = Alignment.Center) {
                CircularProgressIndicator()
            }
            session != null -> {
                SecureCameraWebView(session = session!!, modifier = Modifier.fillMaxWidth().weight(1f))
                OutlinedButton(onClick = { session = null }, modifier = Modifier.fillMaxWidth()) {
                    Text("Cerrar cámara")
                }
            }
            else -> {
                Box(Modifier.fillMaxWidth().weight(1f), contentAlignment = Alignment.Center) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        Text(problem ?: "La cámara permanece apagada hasta que solicites una sesión.")
                        Button(onClick = {
                            loading = true
                            problem = null
                        }) { Text("Conectar cámara") }
                    }
                }
            }
        }
    }

    if (loading) {
        LaunchedEffect(Unit) {
            try {
                session = api.createCameraSession()
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (error: DomainApiException) {
                problem = cameraProblemMessage(error.problem.code)
            } catch (_: Exception) {
                problem = "No fue posible iniciar la cámara."
            } finally {
                loading = false
            }
        }
    }
}

@Composable
private fun ColumnScope.CameraUnavailable(message: String) {
    Box(Modifier.fillMaxWidth().weight(1f), contentAlignment = Alignment.Center) {
        Text(message, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@SuppressLint("SetJavaScriptEnabled")
@Composable
private fun SecureCameraWebView(session: ApiCameraSession, modifier: Modifier = Modifier) {
    val document = remember(session.sessionUuid) { CameraWebDocument.render(session) }
    val baseUrl = remember(session.playbackUrl) { CameraWebDocument.relayOrigin(session.playbackUrl) }
    var webView by remember { mutableStateOf<WebView?>(null) }

    AndroidView(
        modifier = modifier,
        factory = { context ->
            WebView(context).apply {
                setBackgroundColor(Color.BLACK)
                settings.javaScriptEnabled = true
                settings.domStorageEnabled = false
                settings.databaseEnabled = false
                settings.allowFileAccess = false
                settings.allowContentAccess = false
                settings.cacheMode = WebSettings.LOAD_NO_CACHE
                settings.mixedContentMode = WebSettings.MIXED_CONTENT_NEVER_ALLOW
                settings.mediaPlaybackRequiresUserGesture = false
                webViewClient = object : WebViewClient() {
                    override fun shouldOverrideUrlLoading(view: WebView?, request: WebResourceRequest?): Boolean = true
                    override fun onReceivedSslError(view: WebView?, handler: SslErrorHandler, error: SslError?) {
                        handler.cancel()
                    }
                }
                loadDataWithBaseURL(baseUrl, document, "text/html", "UTF-8", null)
                webView = this
            }
        }
    )

    DisposableEffect(session.sessionUuid) {
        onDispose {
            webView?.apply {
                evaluateJavascript("globalThis.hydroboxStop?.()", null)
                loadUrl("about:blank")
                clearHistory()
                clearCache(true)
                removeAllViews()
                destroy()
            }
            webView = null
        }
    }
}

private fun cameraProblemMessage(code: String): String = when (code) {
    "camera.unavailable" -> "La cámara no está disponible en este momento."
    "camera.session_limit" -> "Ya existen demasiadas sesiones abiertas. Cierra otra sesión e inténtalo de nuevo."
    "authorization.scope_denied", "authorization.site_denied" -> "Tu cuenta no tiene permiso para ver esta cámara."
    else -> "No fue posible iniciar la cámara."
}
