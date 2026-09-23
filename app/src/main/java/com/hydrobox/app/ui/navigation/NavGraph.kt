package com.hydrobox.app.ui.navigation

import android.net.Uri
import android.content.Intent
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import com.hydrobox.app.ui.components.HydroTopBar
import androidx.compose.material.icons.automirrored.outlined.Logout
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.foundation.shape.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.*
import androidx.compose.runtime.rememberCoroutineScope
import kotlinx.coroutines.launch
import androidx.compose.ui.platform.LocalContext
import coil.request.ImageRequest
import coil.request.CachePolicy
import java.io.File
import com.hydrobox.app.R
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.*
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.navigation.NavDestination
import androidx.navigation.NavDestination.Companion.hierarchy
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.compose.*
import coil.compose.rememberAsyncImagePainter

sealed class Route(val path: String) {

    data object Resume : Route("resume")
    data object Sensors : Route("sensors")
    data object Actuators : Route("actuators")
    data object History : Route("history")
    data object Crops : Route("crops")

    data object Account : Route("account")
    data object Automation : Route("automation")
    data object Settings : Route("settings")
    data object Notification : Route("notification")
}

data class BottomItem(val route: Route, val label: String, val icon: ImageVector)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun MainScaffold(
    authVM: com.hydrobox.app.auth.AuthViewModel
) {
    val nav = rememberNavController()
    val backStack by nav.currentBackStackEntryAsState()
    val current = backStack?.destination
    val drawerState = rememberDrawerState(initialValue = DrawerValue.Closed)
    val scope = rememberCoroutineScope()

    var showLogoutDialog by remember { mutableStateOf(false) }

    val user = authVM.currentUser.collectAsState().value
    val auth = authVM.authState.collectAsState().value

    val adminName = remember(user?.name, user?.lastName) {
        "${user?.name.orEmpty()} ${user?.lastName.orEmpty()}".trim().ifBlank { "Hydro Admin" }
    }
    val avatarUri: Uri? = user?.avatarUri?.let(Uri::parse)

    val isInRootTabs = current.isInHierarchyOf(
        Route.Resume.path, Route.Sensors.path, Route.Actuators.path, Route.History.path, Route.Crops.path
    )

    if (showLogoutDialog) {
        AlertDialog(
            onDismissRequest = { showLogoutDialog = false },
            title = { Text("Cerrar sesión") },
            text = { Text("¿Está seguro que desea cerrar sesión?") },
            confirmButton = {
                TextButton(onClick = {
                    showLogoutDialog = false
                    scope.launch { drawerState.close() }
                    authVM.logout()
                }) { Text("Aceptar") }
            },
            dismissButton = { TextButton(onClick = { showLogoutDialog = false }) { Text("Cancelar") } }
        )
    }

    val context = LocalContext.current
    val openExternal: (String) -> Unit = { url ->
        scope.launch { drawerState.close() }
        val intent = Intent(Intent.ACTION_VIEW, Uri.parse(url)).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        context.startActivity(intent)
    }

    ModalNavigationDrawer(
        drawerState = drawerState,
        gesturesEnabled = isInRootTabs,
        drawerContent = {
            ModalDrawerSheet(
                drawerContainerColor = MaterialTheme.colorScheme.surface,
                drawerContentColor   = MaterialTheme.colorScheme.onSurface
                ) {
                DrawerHeader(adminName, avatarUri) {
                    scope.launch { drawerState.close() }
                    nav.navigate(Route.Account.path)
                }
                HorizontalDivider(color = MaterialTheme.colorScheme.surfaceVariant)
                DrawerContent(
                    onAccount      = { scope.launch { drawerState.close() }; nav.navigate(Route.Account.path) },
                    onAutomation   = { navigateFromDrawer(nav, drawerState, Route.Automation.path, scope) },
                    onSettings     = { navigateFromDrawer(nav, drawerState, Route.Settings.path, scope) },
                    onNotification = { navigateFromDrawer(nav, drawerState, Route.Notification.path, scope) },
                    onOpenHydrobox  = { openExternal("https://hydrobox.pi.jademajesty.com/") },
                    onLogout       = { scope.launch { drawerState.close() }; showLogoutDialog = true }
                )
            }
        }
    ) {
        Scaffold(
            containerColor = MaterialTheme.colorScheme.background,
            topBar = {
                HydroTopBar(
                    inRootTabs = isInRootTabs,
                    onMenuClick = { if (isInRootTabs) scope.launch { drawerState.open() } },
                    onNavigateUp = { nav.navigateUp() }
                )
            },
            bottomBar = {
                if (isInRootTabs) {
                    NavigationBar(
                        containerColor = MaterialTheme.colorScheme.surface,
                        contentColor   = MaterialTheme.colorScheme.onSurface
                    ) {
                        listOf(
                            BottomItem(Route.Resume, "Resumen", Icons.Filled.Dashboard),
                            BottomItem(Route.Sensors, "Sensores", Icons.Filled.Speed),
                            BottomItem(Route.Actuators, "Actuadores", Icons.Filled.ToggleOn),
                            BottomItem(Route.History, "Historial", Icons.Filled.History),
                            BottomItem(Route.Crops, "Gestión", Icons.Filled.Yard)
                        ).forEach { item ->
                            val selected = current.isInHierarchyOf(item.route.path)
                            NavigationBarItem(
                                selected = selected,
                                onClick = {
                                    nav.navigate(item.route.path) {
                                        popUpTo(nav.graph.findStartDestination().id) { saveState = true }
                                        launchSingleTop = true; restoreState = true
                                    }
                                },
                                icon  = { Icon(item.icon, contentDescription = item.label) },
                                label = { Text(item.label) },
                                colors = NavigationBarItemDefaults.colors(
                                    selectedIconColor   = MaterialTheme.colorScheme.onPrimaryContainer,
                                    selectedTextColor   = MaterialTheme.colorScheme.onPrimaryContainer,
                                    indicatorColor      = MaterialTheme.colorScheme.primaryContainer,
                                    unselectedIconColor = MaterialTheme.colorScheme.onSurfaceVariant,
                                    unselectedTextColor = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            )
                        }
                    }
                }
            }
        ) { padding ->
            NavHost(
                navController = nav,
                startDestination = Route.Resume.path,
                modifier = Modifier.padding(padding)
            ) {
                composable(Route.Resume.path)    {
                    ResumeScreen(paddingValues = PaddingValues(), api = authVM.domainApi)
                }
                composable(Route.Sensors.path)   {
                    SensorsScreen(paddingValues = PaddingValues(), api = authVM.domainApi)
                }
                composable(Route.Actuators.path) {
                    ActuatorsScreen(
                        paddingValues = PaddingValues(),
                        api = authVM.domainApi,
                        scopes = if (auth.offlineMode) emptySet() else auth.scopes
                    )
                }
                composable(Route.History.path)   {
                    HistoryScreen(
                        paddingValues = PaddingValues(),
                        api = authVM.domainApi,
                        scopes = auth.scopes
                    )
                }
                composable(Route.Crops.path)     {
                    CropsScreen(
                        paddingValues = PaddingValues(),
                        api = authVM.domainApi,
                        canChangeCrop = !auth.offlineMode && "cycle:write" in auth.scopes
                    )
                }
                composable(Route.Account.path)   { AccountScreen(vm = authVM) }
                composable(Route.Automation.path) {
                    AutomationScreen(
                        paddingValues = PaddingValues(),
                        api = authVM.domainApi,
                        canRead = "automation:read" in auth.scopes,
                        canWrite = !auth.offlineMode && "automation:write" in auth.scopes,
                        offlineMode = auth.offlineMode
                    )
                }
                composable(Route.Settings.path)  { SettingsScreen() }
                composable(Route.Notification.path) { NotificationScreen(paddingValues = PaddingValues()) }
            }
        }
    }
}

@Composable
private fun FullScreenLoading(text: String = "Cargando…") {
    Box(
        Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.scrim.copy(alpha = 0.55f)),
        contentAlignment = Alignment.Center
    ) {
        Surface(
            shape = RoundedCornerShape(16.dp),
            tonalElevation = 6.dp,
            color = MaterialTheme.colorScheme.surface.copy(alpha = 0.96f)
        ) {
            Row(
                Modifier.padding(horizontal = 20.dp, vertical = 16.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                CircularProgressIndicator(strokeWidth = 3.dp, modifier = Modifier.size(26.dp))
                Spacer(Modifier.width(12.dp))
                Text(text, style = MaterialTheme.typography.titleMedium)
            }
        }
    }
}

@Composable
fun HydroNavRoot() {
    val vm: com.hydrobox.app.auth.AuthViewModel = androidx.lifecycle.viewmodel.compose.viewModel()
    val auth = vm.authState.collectAsState(initial = null).value ?: return
    val booting = vm.booting.collectAsState().value

    var wasLogged by rememberSaveable { mutableStateOf(auth.isLoggedIn) }
    var gateMessage by rememberSaveable { mutableStateOf<String?>(null) }

    LaunchedEffect(booting, auth.isLoggedIn) {
        if (booting) return@LaunchedEffect

        if (!wasLogged && auth.isLoggedIn) {
            gateMessage = "Entrando…"
            kotlinx.coroutines.delay(450)
            gateMessage = null
        } else if (wasLogged && !auth.isLoggedIn) {
            gateMessage = "Cerrando sesión…"
            kotlinx.coroutines.delay(300)
            gateMessage = null
        }
        wasLogged = auth.isLoggedIn
    }

    when {
        booting            -> FullScreenLoading("Preparando…")
        gateMessage != null-> FullScreenLoading(gateMessage!!)
        auth.isLoggedIn    -> MainScaffold(vm)
        else               -> LoginScreen(onLoggedIn = { }, vm = vm)
    }
}

private fun navigateFromDrawer(
    nav: androidx.navigation.NavHostController,
    drawerState: DrawerState,
    route: String,
    scope: kotlinx.coroutines.CoroutineScope
) {
    scope.launch { drawerState.close() }
    nav.navigate(route) {
        popUpTo(nav.graph.findStartDestination().id) { saveState = true }
        launchSingleTop = true
        restoreState = true
    }
}

@Composable
private fun DrawerHeader(
    adminName: String,
    avatarUri: Uri?,
    onAccount: () -> Unit
) {
    val context = LocalContext.current

    val version: Long = if (avatarUri?.scheme?.equals("file", ignoreCase = true) == true) {
        try {
            avatarUri.path?.let { File(it).lastModified() } ?: 0L
        } catch (_: Throwable) { 0L }
    } else 0L

    val req = ImageRequest.Builder(context)
        .data(avatarUri)
        .setParameter("version", version)
        .memoryCacheKey("avatar_${avatarUri?.path.orEmpty()}_$version")
        .diskCacheKey("avatar_${avatarUri?.path.orEmpty()}_$version")
        .memoryCachePolicy(CachePolicy.ENABLED)
        .diskCachePolicy(CachePolicy.ENABLED)
        .error(R.drawable.ic_avatar_placeholder)
        .fallback(R.drawable.ic_avatar_placeholder)
        .build()

    val painter = rememberAsyncImagePainter(model = req)

    Row(
        Modifier.fillMaxWidth().padding(16.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Image(
            painter = painter,
            contentDescription = "Foto de perfil",
            modifier = Modifier.size(56.dp).clip(CircleShape)
        )
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text("Administrador", style = MaterialTheme.typography.labelMedium)
            Text(adminName, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
        }
        TextButton(onClick = onAccount) { Text("Cuenta") }
    }
}

@Composable
private fun DrawerContent(
    onAccount: () -> Unit,
    onAutomation: () -> Unit,
    onSettings: () -> Unit,
    onNotification: () -> Unit,
    onOpenHydrobox: () -> Unit,
    onLogout: () -> Unit
) {
    NavigationDrawerItem(
        label = { Text("Cuenta") },
        selected = false,
        onClick = onAccount,
        icon = { Icon(Icons.Outlined.AccountCircle, null) },
        colors = NavigationDrawerItemDefaults.colors(
            selectedContainerColor = MaterialTheme.colorScheme.primaryContainer,
            selectedIconColor = MaterialTheme.colorScheme.onPrimaryContainer,
            selectedTextColor = MaterialTheme.colorScheme.onPrimaryContainer,
            unselectedIconColor = MaterialTheme.colorScheme.onSurfaceVariant,
            unselectedTextColor = MaterialTheme.colorScheme.onSurface
        ),
        modifier = Modifier.padding(NavigationDrawerItemDefaults.ItemPadding)
    )
    NavigationDrawerItem(
        label = { Text("Automatizaciones") },
        selected = false,
        onClick = onAutomation,
        icon = { Icon(Icons.Outlined.Schedule, contentDescription = null) },
        colors = NavigationDrawerItemDefaults.colors(
            selectedContainerColor = MaterialTheme.colorScheme.primaryContainer,
            selectedIconColor = MaterialTheme.colorScheme.onPrimaryContainer,
            selectedTextColor = MaterialTheme.colorScheme.onPrimaryContainer,
            unselectedIconColor = MaterialTheme.colorScheme.onSurfaceVariant,
            unselectedTextColor = MaterialTheme.colorScheme.onSurface
        ),
        modifier = Modifier.padding(NavigationDrawerItemDefaults.ItemPadding)
    )
    NavigationDrawerItem(
        label = { Text("Configuración") },
        selected = false,
        onClick = onSettings,
        icon = { Icon(Icons.Outlined.Settings, contentDescription = null) },
        colors = NavigationDrawerItemDefaults.colors(
            selectedContainerColor = MaterialTheme.colorScheme.primaryContainer,
            selectedIconColor = MaterialTheme.colorScheme.onPrimaryContainer,
            selectedTextColor = MaterialTheme.colorScheme.onPrimaryContainer,
            unselectedIconColor = MaterialTheme.colorScheme.onSurfaceVariant,
            unselectedTextColor = MaterialTheme.colorScheme.onSurface
        ),
        modifier = Modifier.padding(NavigationDrawerItemDefaults.ItemPadding)
    )
    NavigationDrawerItem(
        label = { Text("Notificación") },
        selected = false,
        onClick = onNotification,
        icon = { Icon(Icons.Filled.NotificationsActive, contentDescription = null) },
        colors = NavigationDrawerItemDefaults.colors(
            selectedContainerColor = MaterialTheme.colorScheme.primaryContainer,
            selectedIconColor = MaterialTheme.colorScheme.onPrimaryContainer,
            selectedTextColor = MaterialTheme.colorScheme.onPrimaryContainer,
            unselectedIconColor = MaterialTheme.colorScheme.onSurfaceVariant,
            unselectedTextColor = MaterialTheme.colorScheme.onSurface
        ),
        modifier = Modifier.padding(NavigationDrawerItemDefaults.ItemPadding)
    )

    NavigationDrawerItem(
        label = { Text("HydroBox Web") },
        selected = false,
        onClick = onOpenHydrobox,
        icon = { Icon(Icons.Outlined.Public, contentDescription = null) },
        colors = NavigationDrawerItemDefaults.colors(
            selectedContainerColor = MaterialTheme.colorScheme.primaryContainer,
            selectedIconColor = MaterialTheme.colorScheme.onPrimaryContainer,
            selectedTextColor = MaterialTheme.colorScheme.onPrimaryContainer,
            unselectedIconColor = MaterialTheme.colorScheme.onSurfaceVariant,
            unselectedTextColor = MaterialTheme.colorScheme.onSurface
        ),
        modifier = Modifier.padding(NavigationDrawerItemDefaults.ItemPadding)
    )

    NavigationDrawerItem(
        label = { Text("Cerrar sesión") },
        selected = false,
        onClick = onLogout,
        icon = { Icon(Icons.AutoMirrored.Outlined.Logout, contentDescription = null) },
        colors = NavigationDrawerItemDefaults.colors(
            selectedContainerColor = MaterialTheme.colorScheme.primaryContainer,
            selectedIconColor = MaterialTheme.colorScheme.onPrimaryContainer,
            selectedTextColor = MaterialTheme.colorScheme.onPrimaryContainer,
            unselectedIconColor = MaterialTheme.colorScheme.onSurfaceVariant,
            unselectedTextColor = MaterialTheme.colorScheme.onSurface
        ),
        modifier = Modifier.padding(NavigationDrawerItemDefaults.ItemPadding)
    )
}

private fun NavDestination?.isInHierarchyOf(vararg routes: String): Boolean =
    this?.hierarchy?.any { it.route in routes } == true
