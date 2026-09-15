package com.hydrobox.app.ui.navigation

import android.net.Uri
import androidx.compose.ui.res.painterResource
import com.hydrobox.app.R
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.AccountCircle
import androidx.compose.material.icons.outlined.CameraAlt
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import kotlinx.coroutines.launch

@Composable
fun AccountScreen(
    vm: com.hydrobox.app.auth.AuthViewModel
) {
    val user = vm.currentUser.collectAsState().value
    if (user == null) {
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            CircularProgressIndicator()
        }
        return
    }

    var photoUri by remember(user.avatarUri) { mutableStateOf(user.avatarUri?.let(Uri::parse)) }
    val pickMedia = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickVisualMedia()
    ) { uri -> if (uri != null) photoUri = uri }

    var nameFull by remember(user) { mutableStateOf("${user.name} ${user.lastName}".trim()) }
    var email by remember(user) { mutableStateOf(user.email) }
    var phonePrefix by remember(user) { mutableStateOf(user.phonePrefix ?: "+52") }
    var phone by remember(user) { mutableStateOf(user.phone ?: "") }

    val emailOk = remember(email) { email.isNotBlank() && android.util.Patterns.EMAIL_ADDRESS.matcher(email).matches() }
    val phoneOk = remember(phone) { phone.isEmpty() || phone.all(Char::isDigit) }

    val hasChanges = remember(nameFull, email, phonePrefix, phone, photoUri) {
        val baseFull = "${user.name} ${user.lastName}".trim()
        nameFull.isNotBlank() && (
                nameFull != baseFull ||
                        email != user.email ||
                        phonePrefix != (user.phonePrefix ?: "+52") ||
                        phone != (user.phone ?: "") ||
                        (photoUri?.toString() ?: "") != (user.avatarUri ?: "")
                )
    }

    val tfColors = TextFieldDefaults.colors(
        focusedContainerColor   = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.25f),
        unfocusedContainerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.20f),
        disabledContainerColor  = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.12f),
        focusedIndicatorColor   = Color.Transparent,
        unfocusedIndicatorColor = Color.Transparent,
        disabledIndicatorColor  = Color.Transparent,
        cursorColor             = MaterialTheme.colorScheme.primary
    )

    val snackbarHostState = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()

    if (photoUri != null) {
        AsyncImage(
            model = photoUri,
            contentDescription = "Foto de perfil",
            modifier = Modifier.size(96.dp).clip(CircleShape),
            error = painterResource(R.drawable.ic_avatar_placeholder),
            fallback = painterResource(R.drawable.ic_avatar_placeholder)
        )
    } else {
        Icon(
            imageVector = Icons.Outlined.AccountCircle,
            contentDescription = "Foto de perfil",
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.size(96.dp).clip(CircleShape)
        )
    }

    Scaffold(
        snackbarHost = { SnackbarHost(snackbarHostState) }
    ) { inner ->
        Column(
            Modifier
                .padding(inner)
                .fillMaxSize()
                .padding(16.dp)
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Box(contentAlignment = Alignment.BottomEnd) {
                if (photoUri != null) {
                    AsyncImage(
                        model = photoUri,
                        contentDescription = "Foto de perfil",
                        modifier = Modifier.size(96.dp).clip(CircleShape)
                    )
                } else {
                    Icon(
                        imageVector = Icons.Outlined.AccountCircle,
                        contentDescription = "Foto de perfil",
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(96.dp).clip(CircleShape)
                    )
                }
                FilledTonalIconButton(
                    onClick = {
                        pickMedia.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
                    },
                    modifier = Modifier
                        .offset(x = 4.dp, y = 4.dp)
                        .size(32.dp)
                        .clip(CircleShape)
                        .background(MaterialTheme.colorScheme.surface)
                ) { Icon(Icons.Outlined.CameraAlt, contentDescription = "Cambiar foto") }
            }

            LabeledField("Nombre y apellido") {
                TextField(
                    value = nameFull,
                    onValueChange = { nameFull = it },
                    placeholder = { Text("Alexis Verduzco") },
                    singleLine = true,
                    shape = RoundedCornerShape(14.dp),
                    modifier = Modifier.fillMaxWidth(),
                    colors = tfColors
                )
            }

            LabeledField("E-mail") {
                TextField(
                    value = email,
                    onValueChange = { email = it },
                    placeholder = { Text("tucorreo@ejemplo.com") },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email),
                    isError = !emailOk,
                    supportingText = { if (!emailOk) Text("Email no válido", color = MaterialTheme.colorScheme.error) },
                    shape = RoundedCornerShape(14.dp),
                    modifier = Modifier.fillMaxWidth(),
                    colors = tfColors
                )
            }

            LabeledField("Teléfono") {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    TextField(
                        value = phonePrefix,
                        onValueChange = { if (it.length <= 5) phonePrefix = it },
                        singleLine = true,
                        modifier = Modifier.width(90.dp),
                        shape = RoundedCornerShape(14.dp),
                        colors = tfColors
                    )
                    TextField(
                        value = phone,
                        onValueChange = { phone = it.filter(Char::isDigit) },
                        placeholder = { Text("6895312") },
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone),
                        isError = !phoneOk,
                        supportingText = { if (!phoneOk) Text("Solo dígitos", color = MaterialTheme.colorScheme.error) },
                        modifier = Modifier.weight(1f),
                        shape = RoundedCornerShape(14.dp),
                        colors = tfColors
                    )
                }
            }

            Spacer(Modifier.height(8.dp))

            Button(
                onClick = {
                    val parts = nameFull.trim().split(" ", limit = 2)
                    val n = parts.getOrNull(0).orEmpty()
                    val l = parts.getOrNull(1).orEmpty()
                    vm.updateProfile(
                        name = n,
                        lastName = l,
                        email = email,
                        avatarUri = photoUri?.toString(),
                        phonePrefix = phonePrefix,
                        phone = phone
                    ) {
                        scope.launch {
                            snackbarHostState.showSnackbar("Cambios guardados")
                        }
                    }
                },
                enabled = hasChanges && emailOk && phoneOk,
                shape = RoundedCornerShape(14.dp),
                modifier = Modifier.fillMaxWidth().height(48.dp)
            ) { Text("Guardar cambios") }

            val roleLabel = when (user.roleKey) {
                "administrator" -> "Administrador"
                "operator" -> "Operador"
                else -> "Sin asignar"
            }
            Text("Rol: $roleLabel", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Spacer(Modifier.height(8.dp))
        }
    }
}

@Composable
private fun LabeledField(
    label: String,
    content: @Composable () -> Unit
) {
    Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Text(label, style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurface)
        content()
    }
}
