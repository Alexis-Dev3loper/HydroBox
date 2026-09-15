# HydroBox Mobile

Cliente Android nativo de HydroBox construido con Kotlin, Jetpack Compose y
Material 3. La arquitectura objetivo consume exclusivamente la API central;
el acceso MQTT directo que todavía existe está marcado como **LEGACY** y queda
deshabilitado por defecto.

## Requisitos

- JDK 17.
- Android SDK 36 y Build Tools compatibles.
- Gradle Wrapper incluido en el repositorio.

No se requiere una API ni un broker para compilar o ejecutar los unit tests.

## Configuración local

1. Copiar `local.properties.example` como `local.properties`.
2. Configurar `sdk.dir` para el Android SDK local.
3. Mantener los placeholders si solo se compila o prueba el proyecto.
4. Para un entorno DEV aislado, definir `hydrobox.apiBaseUrl` sin copiar
   credenciales al repositorio.

También se aceptan propiedades Gradle (`-Phydrobox.apiBaseUrl=...`) o variables
de entorno con prefijo `HYDROBOX_MOBILE_`; la precedencia exacta y la matriz se
documentan en `docs/MOBILE_BASELINE.md`.

El MQTT directo solo puede habilitarse en debug y no debe apuntar a hardware ni
infraestructura real durante pruebas. Release lo fuerza a deshabilitado.

## Verificación

```powershell
.\scripts\verify-mobile-baseline.ps1
```

El script ejecuta unit tests, lint y ensamblado debug mediante el Wrapper. En
Linux/macOS, los comandos equivalentes son:

```bash
./gradlew --no-daemon testDebugUnitTest lintDebug assembleDebug
```

Los tests instrumentados requieren emulador/dispositivo y se mantienen fuera
del baseline host. No conectar hardware real ni introducir secretos para correr
esta verificación.

## Continuidad

Leer `AGENTS.md` antes de modificar. La documentación canónica cross-repo vive
en `../Desktop/docs/project/`; el inventario específico de Mobile está en
`docs/MOBILE_BASELINE.md`.
