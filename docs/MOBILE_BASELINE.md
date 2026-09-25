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
| Red | `HttpURLConnection` sobre API v1; MQTT directo retirado en MB-004 |
| Tests previos | solo ejemplos del template; no caracterizaban HydroBox |

El host auditado no tiene JDK, Android SDK ni caché Gradle, por lo que el build
no puede ejecutarse localmente. `scripts/verify-mobile-baseline.ps1` detecta ese
prerequisito y, con toolchain disponible, ejecuta la misma matriz usada por CI:
`testDebugUnitTest`, `lintDebug` y `assembleDebug`.

GitHub Actions `35002475731` validó el baseline con JDK 17, Android SDK 36 y
Build Tools 36.0.0: **10/10 unit tests**, `lintDebug`, `assembleDebug` y
`BUILD SUCCESSFUL`. MB-004 retira el cliente MQTT y su reconnect; la misma
matriz pasó de nuevo en CI `35081160677` sobre el cierre publicado.

## Matriz de configuración

Precedencia: propiedad Gradle → `local.properties` ignorado por Git → variable
de entorno → placeholder inerte.

| Propiedad Gradle | Variable de entorno | Default versionado | Uso |
|---|---|---|---|
| `hydrobox.apiBaseUrl` | `HYDROBOX_MOBILE_API_BASE_URL` | URL HTTPS `.invalid` | base versionada `/api/v1` para auth y dominio canónico |

La API exige URL absoluta HTTPS. MB-004 elimina las propiedades MQTT y sus
credenciales del build; `local.properties` no es un secret store. MB-002
implementa la sesión humana sin passwords persistidos.

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

### Commands/dosing API — MB-004 IMPLEMENTADO / VERIFICADO / PUBLICADO

- catálogos, commands y dosing requests usan API v1 autenticada por sitio;
- cada mutación reutilizable usa UUID e `Idempotency-Key`, expiry corta y un
  único retry con el mismo body ante fallo transitorio;
- la UI distingue desired, reported, availability y lifecycle; HTTP `202` no
  se presenta como ACK ni ejecución física;
- dosing registra mililitros y deja calibración/correlación física a Edge;
- `HydroMqtt`, contrato/configuración MQTT y HiveMQ fueron retirados.

Evidencia: `2f8907f`–`92c9862`; CI `35081160677`; Mobile #5 cerrada.

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
- **RESUELTO EN MB-004:** actuadores y nutrientes usan keys lógicas
  de catálogos API; la UI ya no deriva aliases físicos desde títulos.

MB-003/MB-005 migran estas discrepancias; MB-001 no inventa mappings.

## Navegación y smoke

Pantallas detectadas: login, resumen, historial, sensores, actuadores, cultivos,
cuenta, notificaciones y ajustes. El smoke host verificable es:

1. compilar sources y recursos con `assembleDebug`;
2. ejecutar unit/contract tests de API, lifecycle y presentación;
3. ejecutar `lintDebug`;
4. confirmar que defaults no abren una API real;
5. confirmar que el APK no contiene cliente, configuración ni credenciales MQTT.

Un smoke UI instrumentado necesita emulador/dispositivo y se mantiene separado;
no requiere ni autoriza broker, API, hardware o credenciales reales.

## Deuda priorizada

1. **HD-017 / hardening:** auditar seguridad cross-repo antes de ampliar
   superficies Mobile.
2. **MB-008+**: cámara segura y hardening final.
3. **CI:** migrar las Actions que aún apuntan a `setup-java@v4`/Node 20.

MB-002 quedó publicado hasta `54f28c3` y la matriz Android completa pasó en CI
`35036523635` con los 29 tests del source tree, lint y assemble.
MB-003 está publicado hasta `0b7f10c`; `testDebugUnitTest` (**40/40**),
`lintDebug` y `assembleDebug` pasan en CI `35060435836`.
MB-004 está publicado en `2f8907f`–`92c9862`; CI `35081160677` valida
`testDebugUnitTest`, `lintDebug` y `assembleDebug`. Mobile #5 está cerrada.
MB-005 está publicado en `64d5566`–`1a1c594`: 46 tests en source,
unidades/duraciones API, cero/missing, freshness y hora local. CI `35131675188`
valida `testDebugUnitTest`, `lintDebug` y `assembleDebug`; Mobile #6 está cerrada.
MB-006 está publicado y verificado en `b6e5be7`–`6700725`: 55 tests en source,
cache network-first persistente y scoped, sesión offline read-only, backoff,
invalidación y estado visible. CI `35138093458` valida `testDebugUnitTest`,
`lintDebug` y `assembleDebug`; Mobile #7 está cerrada.
MB-007 está completado y publicado en `95f4e1a`–`0c8f450`: Automation,
historial real, health/freshness, alertas durables/cache y ACK humano online.
CI `36112739068` valida 73 tests en source, lint y assemble; Mobile #8 está
cerrada.
