# MB-004 — Commands, dosing y lifecycle real

Actualizado: **2026-09-16**.

Estado: **IMPLEMENTADO / VERIFICADO / PUBLICADO**.

## Problema auditado

La pantalla `ActuatorsScreen` crea seis filas locales, traduce sus títulos a
aliases físicos y llama directamente a `HydroMqtt`. El switch cambia antes de
tener respuesta durable y un publish exitoso no representa `sent`, ACK ni
`reported_state`. El proceso Mobile también inicia un cliente MQTT con host,
puerto y credenciales propios.

La API v1 publicada ya expone las fronteras necesarias:

- `catalogs/actuators` separa `desired_state`, `reported_state` y disponibilidad;
- `catalogs/nutrients` relaciona nutrient keys con actuadores dosificadores;
- `commands` crea/lista/consulta intents con UUID, expiry y lifecycle;
- `dosing-requests` crea/consulta una intención durable en mililitros;
- HTTP `202` solo registra la intención; no acredita transporte ni ejecución.

## Diseño

1. Mobile obtiene catálogos, estados y lifecycle exclusivamente por API v1 con
   Bearer y `site_key`.
2. Un switch crea un command `set_state` con UUID e `Idempotency-Key` iguales,
   expiry UTC de cinco minutos y la `actuator_key` canónica.
3. Una dosis manual crea un `dosing-request` con UUID/idempotencia, `nutrient_key`,
   mililitros y la misma expiry corta. Mobile nunca convierte ml a duración.
4. La UI presenta por separado intención deseada, estado reportado,
   disponibilidad y último lifecycle. Solo `reported_state` describe el estado
   físico observado.
5. `pending`, `sent`, `acknowledged`, `failed` y `expired` se conservan sin
   aliases optimistas. La evidencia `accepted/completed/reported` tampoco se
   colapsa en un booleano local.
6. Los permisos `command:write` y `dosing:write` controlan acciones; lectura
   sigue disponible con sus scopes. Un `403` no invalida la sesión.
7. La recarga consulta el servidor y permite reconstruir commands tras restart.
   El listado general de dosing no existe en API v1; Mobile conserva y presenta
   la respuesta de la solicitud actual, sin inventar historial. Offline/cache
   durable de cliente pertenece a MB-006.
8. Se eliminan `HydroMqtt`, el contrato legacy, configuración BuildConfig,
   credenciales locales y dependencia HiveMQ. El transporte Web↔Edge permanece
   responsabilidad de WB-007/HD-012.

## Errores y seguridad

- Requests mutables no siguen redirects y usan timeouts existentes.
- Un retry con el mismo UUID conserva la misma intención; un UUID con payload
  diferente es conflicto y no se reinterpreta.
- Error de red se muestra como estado recuperable; no activa fallback MQTT.
- No se registran tokens, bodies, credenciales ni detalles privados de red.
- No se conecta broker, API real, hardware ni infraestructura durante pruebas.

## Verificación

- Contract tests HTTP en memoria para catálogos, command create/list/get,
  dosing create/get, idempotencia, lifecycle, evidence, permisos y offline.
- Tests puros del modelo de presentación para desired/reported/lifecycle.
- Escaneo sin `HydroMqtt`, HiveMQ, aliases físicos ni variables MQTT Mobile.
- CI: `testDebugUnitTest`, `lintDebug` y `assembleDebug` con JDK 17/SDK 36.

## Evidencia local

- `2f8907f`: diseño funcional y límites de responsabilidad.
- `a74fe1e`: DTOs, cliente HTTP, lifecycle, idempotencia y contract tests.
- `9072966`: UI de actuadores/dosing por API y retiro de MQTT/HiveMQ/config.
- El source tree conserva **40 tests** y el escaneo estático no encuentra
  referencias activas a `HydroMqtt`, HiveMQ, variables MQTT ni aliases físicos.
- `git diff --check` pasa. La CI `35081160677` ejecuta con JDK 17/SDK 36
  `testDebugUnitTest`, `lintDebug` y `assembleDebug`: **BUILD SUCCESSFUL**.
- Mobile #5 está cerrada. No hubo deployment, broker, API ni hardware reales.
