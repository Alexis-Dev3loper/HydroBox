package com.hydrobox.app.ui.navigation

import androidx.compose.animation.*
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.hydrobox.app.auth.AuthViewModel
import com.hydrobox.app.R
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.foundation.background
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Email
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material3.Icon
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.platform.LocalFocusManager

@Composable
fun LoginScreen(
    onLoggedIn: () -> Unit,
    setGlobalBusy: (Boolean) -> Unit = {},
    vm: AuthViewModel = viewModel()
) {

    val rememberedEmail by vm.lastEmail.collectAsState()
    val rememberedPref  by vm.rememberPref.collectAsState()

    var email by rememberSaveable(rememberedEmail) { mutableStateOf(rememberedEmail.orEmpty()) }
    // Passwords must not enter Compose saved-state bundles.
    var pass by remember { mutableStateOf("") }
    var rememberMe by rememberSaveable(rememberedPref) { mutableStateOf(rememberedPref) }

    var loading by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    var showPass by remember { mutableStateOf(false) }
    val focus = LocalFocusManager.current

    val tryLogin: () -> Unit = remember(email, pass, loading, rememberMe) {
        {
            if (!loading) {
                focus.clearFocus()
                loading = true; error = null
                setGlobalBusy(true)
                vm.login(email, pass, rememberMe) { ok ->
                    pass = ""
                    loading = false
                    if (ok) onLoggedIn() else {
                        error = "No fue posible iniciar sesión. Revisa tus datos y conexión."
                        setGlobalBusy(false)
                    }
                }
            }
        }
    }

    Box(Modifier.fillMaxSize()) {
        val bg = painterResource(id = R.drawable.bg_login)
        Image(
            painter = bg,
            contentDescription = null,
            contentScale = ContentScale.Crop,
            modifier = Modifier.fillMaxSize().alpha(0.80f)
        )
        Box(
            Modifier
                .fillMaxSize()
                .background(
                    Brush.verticalGradient(
                        listOf(
                            Color.Black.copy(alpha = 0.30f),
                            Color.Transparent,
                            Color.Black.copy(alpha = 0.35f)
                        )
                    )
                )
        )
        Box(
            Modifier
                .fillMaxSize()
                .padding(20.dp),
            contentAlignment = Alignment.Center
        ) {
            Surface(
                shape = RoundedCornerShape(28.dp),
                tonalElevation = 2.dp,
                color = MaterialTheme.colorScheme.surface.copy(alpha = 0.92f),
                shadowElevation = 4.dp,
                modifier = Modifier.fillMaxWidth().wrapContentHeight()
            ) {
                Column(
                    Modifier.padding(vertical = 28.dp, horizontal = 22.dp).imePadding(),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text("HydroBox", style = MaterialTheme.typography.headlineMedium, color = MaterialTheme.colorScheme.primary)
                    Spacer(Modifier.height(6.dp))
                    Text("Iniciar sesión", style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.8f))

                    Spacer(Modifier.height(20.dp))

                    OutlinedTextField(
                        value = email,
                        onValueChange = { email = it },
                        modifier = Modifier.fillMaxWidth(),
                        label = { Text("Email") },
                        leadingIcon = { Icon(Icons.Filled.Email, null) },
                        singleLine = true,
                        keyboardOptions = KeyboardOptions.Default.copy(imeAction = ImeAction.Next)
                    )

                    Spacer(Modifier.height(12.dp))

                    OutlinedTextField(
                        value = pass,
                        onValueChange = { pass = it },
                        modifier = Modifier.fillMaxWidth(),
                        label = { Text("Contraseña") },
                        leadingIcon = { Icon(Icons.Filled.Lock, null) },
                        singleLine = true,
                        visualTransformation = if (showPass) VisualTransformation.None else PasswordVisualTransformation(),
                        trailingIcon = {
                            TextButton(onClick = { showPass = !showPass }) {
                                Text(if (showPass) "Ocultar" else "Mostrar")
                            }
                        },
                        keyboardOptions = KeyboardOptions.Default.copy(imeAction = ImeAction.Done),
                        keyboardActions = KeyboardActions(onDone = { tryLogin() })
                    )

                    Spacer(Modifier.height(8.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.End,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text("Recordarme")
                        Spacer(Modifier.width(6.dp))
                        Checkbox(checked = rememberMe, onCheckedChange = { rememberMe = it })
                    }

                    if (error != null) {
                        Spacer(Modifier.height(8.dp))
                        Text(error!!, color = MaterialTheme.colorScheme.error)
                    }

                    Spacer(Modifier.height(18.dp))

                    Button(
                        onClick = { tryLogin() },
                        enabled = !loading,
                        modifier = Modifier.fillMaxWidth().height(52.dp).animateContentSize(),
                        shape = RoundedCornerShape(16.dp)
                    ) {
                        if (loading) {
                            CircularProgressIndicator(strokeWidth = 2.5.dp, modifier = Modifier.size(20.dp))
                            Spacer(Modifier.width(8.dp))
                            Text("Ingresando…")
                        } else {
                            Text("Entrar")
                        }
                    }
                }
            }
        }
    }
}
