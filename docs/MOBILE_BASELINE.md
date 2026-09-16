# MB-001 — Baseline reproducible Mobile

Actualizado: **2026-09-16**.

Estado: **IMPLEMENTADO / VERIFICADO / PUBLICADO**.

## Stack y build

| Componente | Estado real |
|---|---|
| Android | applicationId/namespace `com.hydrobox.app`; min 24, compile/target 36 |
| Lenguaje | Kotlin 2.0.21; Java/JVM 17 |
| Build | AGP 8.13.0, Gradle Wrapper 8.13, KSP 2.0.21-1.0.25 |
| UI | Jetpack Compose, Material 3, Navigation y Coil |
| Datos locales | Room 2.6.1 y DataStore 1.1.1 |
| Concurrencia | Kotlin coroutines 1.9.0 |
| Red legacy | `HttpURLConnection` y HiveMQ MQTT 3.1.10 |
| Tests previos | solo ejemplos del template; no caracterizaban HydroBox |

El host auditado no tiene JDK, Android SDK ni caché Gradle, por lo que el build
no puede ejecutarse localmente. `scripts/verify-mobile-baseline.ps1` detecta ese
prerequisito y, con toolchain disponible, ejecuta la misma matriz usada por CI:
`testDebugUnitTest`, `lintDebug` y `assembleDebug`.

GitHub Actions `35002475731` validó el baseline con JDK 17, Android SDK 36 y
Build Tools 36.0.0: **10/10 unit tests**, `lintDebug`, `assembleDebug` y
`BUILD SUCCESSFUL`. El único warning de código es el reconnect MQTT mediante
`GlobalScope`, deuda legacy ya asignada a MB-004.

## Matriz de configuración

Precedencia: propiedad Gradle → `local.properties` ignorado por Git → variable
de entorno → placeholder inerte.

| Propiedad Gradle | Variable de entorno | Default versionado | Uso |
|---|---|---|---|
| `hydrobox.apiBaseUrl` | `HYDROBOX_MOBILE_API_BASE_URL` | URL HTTPS `.invalid` | base versionada `/api/v1` para auth y dominio canónico |
| `hydrobox.mqttEnabled` | `HYDROBOX_MOBILE_MQTT_ENABLED` | `false` | habilita temporalmente MQTT directo solo en debug |
| `hydrobox.mqttHost` | `HYDROBOX_MOBILE_MQTT_HOST` | host `.invalid` | broker DEV aislado |
| `hydrobox.mqttPort` | `HYDROBOX_MOBILE_MQTT_PORT` | `1883` | puerto legacy caracterizado |
| `hydrobox.mqttUsername` | `HYDROBOX_MOBILE_MQTT_USERNAME` | vacío | DEV legacy; no versionar |
| `hydrobox.mqttPassword` | `HYDROBOX_MOBILE_MQTT_PASSWORD` | vacío | DEV legacy; no versionar |

La API exige URL absoluta HTTPS. Release fuerza MQTT a deshabilitado y elimina
username/password aunque existan valores locales. Esta configuración no es un
secret store: MB-004 elimina MQTT directo y MB-002 implementa la sesión humana
sin passwords persistidos.

## Inventario de contratos actuales

### API — MB-003 IMPLEMENTADO / VERIFICADO / PUBLICADO

- base URL configurable y HTTPS; anteriormente estaba fija;
- auth humana usa `/api/v1/auth/token`, `/api/v1/auth/refresh`,
  `/api/v1/auth/logout` y `/api/v1/me` desde MB-002;
- catálogos, ciclo activo/cambio y telemetría usan
  `/api/v1/sites/{site_key}/*`, Bearer y envelopes v1;
- DTOs usan `crop_key`, `sensor_key`, UUID, UTC, cursor opaco y preservan cero
  frente a lectura ausente;
- `401` invalida la sesión, `403` conserva la sesión y Problem Details no filtra
  cuerpos, tokens ni detalles del transporte;
- las pantallas de dominio ya no importan el singleton legacy ni usan
  `id_hortaliza`, `remote_id` o columnas fijas;
- exactamente un sitio está soportado; cero o múltiples sitios fallan cerrado
  hasta implementar selección explícita.

MB-003 está publicado hasta `0b7f10c`. La matriz CI `35060435836` valida sus
40 pruebas, `lintDebug` y `assembleDebug`; Mobile #4 está cerrada. Las rutas Web
legacy no se retiran hasta comprobar cero consumidores operacionales.

### MQTT — LEGACY / TRANSITIONAL

- topic `hydrobox/actuators/{deviceId}/set`;
- switch `{"on": boolean}` y dosis `{"dose_ml": integer}`;
- MQTT 3, QoS 1, retain false, clean session y reconnect simple;
- la UI cambia estado tras publish; no existen command UUID, expiry, dedupe,
  ACK accepted/completed ni reported state;
- transporte sin TLS visible en este cliente.

`LegacyMqttContractTest` congela este shape solo para impedir cambios
accidentales durante la migración. No lo convierte en contrato objetivo.

### Persistencia local — MB-002 IMPLEMENTADO / VERIFICADO / PUBLICADO

- Room `hydro_local.db`, schema 4, tabla `users_local` sin password;
- migration explícita 3→4 preserva el perfil no sensible y descarta
  irreversiblemente `passwordPlain`;
- se retiraron el seed local y `fallbackToDestructiveMigration()`;
- access/refresh tokens se cifran AES-GCM mediante Android Keystore; SharedPrefs
  solo contiene IV y ciphertext;
- DataStore conserva únicamente `remember_me` y el último email opcional;
- DB, DataStore y preferencias cifradas están excluidos de cloud backup y
  device transfer;
- se detectó una posible credencial versionada histórica en `MainActivity.kt`;
  la copia activa fue sanitizada, pero requiere rotación y revisión del historial;

El detalle de lifecycle, amenazas y rollout está en `MOBILE_AUTH.md`. No copiar
passwords, tokens o credenciales históricas a fixtures, logs o docs.

## Discrepancias de dominio caracterizadas

- **RESUELTO EN MB-003:** cultivos y sensores usan keys canónicas;
  desaparece el cruce 3/4 Rúcula/Acelga y las duraciones/rangos provienen del API.
- **RESUELTO EN MB-003:** telemetría usa readings canónicas y UTC;
  ya no interpreta `ce_value` como ORP ni `fecha` sin timezone.
- Nivel de agua conserva la unidad entregada por el catálogo. Cualquier
  transformación física futura continúa gated por calibración Edge.
- Alias físicos de actuadores se derivan desde títulos UI y no son keys lógicas
  canónicas.

MB-003/MB-005 migran estas discrepancias; MB-001 no inventa mappings.

## Navegación y smoke

Pantallas detectadas: login, resumen, historial, sensores, actuadores, cultivos,
cuenta, notificaciones y ajustes. El smoke host verificable es:

1. compilar sources y recursos con `assembleDebug`;
2. ejecutar unit tests de configuración y contrato legacy;
3. ejecutar `lintDebug`;
4. confirmar que defaults no abren API/broker real y MQTT queda deshabilitado;
5. confirmar que el APK release no habilita MQTT directo ni incorpora sus
   credenciales de debug.

Un smoke UI instrumentado necesita emulador/dispositivo y se mantiene separado;
no requiere ni autoriza broker, API, hardware o credenciales reales.

## Deuda priorizada

1. **MB-004 P0:** retirar MQTT directo y representar lifecycle real de commands.
2. **MB-005 P1:** cerrar UX/unidades/freshness de dominio sobre el API canónico.
3. **MB-006+**: cache/offline, UX integrada, cámara y hardening final.

MB-002 quedó publicado hasta `54f28c3` y la matriz Android completa pasó en CI
`35036523635` con los 29 tests del source tree, lint y assemble.
MB-003 está publicado hasta `0b7f10c`; `testDebugUnitTest` (**40/40**),
`lintDebug` y `assembleDebug` pasan en CI `35060435836`.
