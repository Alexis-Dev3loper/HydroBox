# MB-007 — Automation, alertas, health e historial

Actualizado: **2026-09-22**.

Estado: **SLICES A–C IMPLEMENTADOS / BASE B VERIFICADA / CIERRE LOCAL PENDIENTE DE CI**.

## Evidencia del estado real

- Mobile ya consume catálogos, telemetría, commands y dosing desde API v1.
- `HistoryScreen` obtiene telemetría real, pero su bloque «Eventos» contiene
  cuatro ejemplos hardcodeados que no deben presentarse como historial real.
- `NotificationScreen` solo muestra «Sistema de Notificaciones (demo)».
- Mobile tiene cliente y pantalla base Automation publicados/verificados;
  edición versionada e historial real están implementados localmente y pendientes de CI.
- Web implementa y prueba CRUD/versionado de `/automations` y lectura de
  `/automation-executions`; creación usa idempotencia y toda mutación posterior
  exige `If-Match` fuerte con la versión vigente.
- OpenAPI declara `/api/v1/health`, `/alerts` y ACK de alertas, pero las rutas,
  controllers, repositories y persistencia de alertas no existen actualmente.
- Core no contiene un dominio durable de alertas. HD-014, HD-015 y HD-016 siguen
  `PLANNED / PENDIENTE`.

## Corte implementable sin inventar contratos

### Slice A — Cliente Automation e historial — IMPLEMENTABLE

- DTOs estrictos para reglas, acciones, schedules y ejecuciones.
- Listado paginado/cacheable de reglas y ejecuciones.
- Creación idempotente; toda regla nueva queda deshabilitada, como exige el servidor.
- Edición, habilitar/pausar y borrado con `If-Match` derivado de `version`.
- Invalidación de cache posterior a mutaciones aceptadas.
- Ningún estado `dispatched` se presenta como ACK físico.

### Slice B — UX Automation reducida — IMPLEMENTABLE

El usuario solo proporciona nombre, acción, objetivo y calendario necesarios.
La aplicación genera internamente `rule_uuid`, idempotency key, timezone local y
grace seguro (30 s para nutrientes; 300 s para las demás acciones). Crear no
habilita la regla: habilitar es una acción separada y versionada.

La pantalla debe distinguir loading, vacío, error, offline/cache, permisos y
conflicto `412`; debe permitir lectura con `automation:read` y mutaciones solo
con `automation:write` y sesión online verificada.

### Slice C — Historial y health derivados de hechos reales — IMPLEMENTABLE

- Retirar los eventos demo.
- Mostrar ejecuciones Automation reales y command lifecycle real.
- Mantener availability de actuadores y freshness de telemetría como señales
  separadas; no convertirlas en un health agregado inexistente.
- `/api/v1/health` representa liveness del proceso central, no health físico del
  sitio, y no se usará como sustituto de Edge/Arduino health.

### Slice D — Alertas y ACK — BLOQUEADO POR DEPENDENCIA

No crear alertas a partir de rangos en el cliente ni persistir ACK locales.
Hasta que HD-016 defina productores, dedupe, lifecycle, persistencia y rutas
operativas, Notificaciones mostrará un estado explícito de funcionalidad no
disponible y nunca fixtures/demo como datos reales.

## Casos negativos obligatorios

- UUID/key/status/action/schedule desconocidos fallan cerrado.
- `If-Match` usa una versión positiva y un `412` exige recargar antes de reintentar.
- Mutaciones no usan cache, no se encolan offline y requieren scope de escritura.
- Respuestas offline pueden mostrar listas cacheadas con freshness visible.
- Borrado `204` no intenta parsear un envelope inexistente.
- Historial vacío no fabrica eventos.
- `dispatched != acknowledged`; alert ACK de usuario no equivale a ACK físico.

## Evidencia local del Slice A

- `HydroDomainApi` modela acciones, schedules, reglas y ejecuciones sin campos
  físicos ni aliases legacy.
- `HttpHydroDomainApi` añade list/show/create/update/delete y ejecuciones con
  idempotencia, `If-Match`, invalidación y tratamiento estricto de `204`.
- Tres pruebas nuevas cubren parsing/paginación, create→patch→delete, headers,
  payload reducido y rechazo de action/timezone/status inválidos.
- El source suma **58 tests** y `git diff --check` pasa.
- CI `35811253177` ejecutó satisfactoriamente `testDebugUnitTest`, `lintDebug` y
  `assembleDebug` sobre `16fbf50`; Slice A está publicado y verificado.

## Evidencia local del Slice B

- `4143d75` añade `AutomationScreen` al menú lateral sin desplazar las cinco
  pestañas principales.
- La lectura exige `automation:read`; el cache puede consultarse offline, pero
  crear, activar, pausar y borrar exigen sesión online y `automation:write`.
- La creación solicita solo nombre, acción/objetivo y calendario; deriva UUID,
  idempotencia, zona local y grace, y conserva la regla pausada hasta una acción
  versionada explícita.
- Las tarjetas separan estado activo/pausado, versión, calendario y próxima
  ejecución; un 412 fuerza recarga antes de reintentar.
- Cuatro pruebas puras cubren zona horaria, grace, mapeo nutriente→actuador y
  validaciones negativas. El source suma **62 tests**.
- El host continúa sin Java/JDK; cada ampliación posterior al checkpoint verde
  requiere push autorizado y CI para test/lint/assemble.

La base de Slice B quedó publicada en `4143d75`–`e61abdc`; CI `35813115448`
pasó 62 tests, lint y assemble. `47fd766` completa localmente edición de
nombre/acción/calendario con la versión visible y muestra ejecuciones reales;
`dispatched` se presenta como ACK físico pendiente.

## Evidencia local del Slice C

- `907af45` elimina los cuatro eventos hardcodeados de `HistoryScreen`.
- Historial mezcla únicamente commands y ejecuciones obtenidos con sus scopes;
  `sent` y `dispatched` conservan ACK físico pendiente.
- La lista se ordena por timestamps reales, usa nombres canónicos disponibles y
  presenta un vacío explícito cuando no existen eventos.
- `NotificationScreen` deja de anunciar una demo: explica que alertas/ACK no
  están disponibles hasta HD-016 y no infiere alertas desde rangos.
- Dos pruebas nuevas cubren orden/fuentes y `sent != acknowledged`. Con las dos
  pruebas del editor, el source suma **66 tests**.
- `47fd766` y `907af45` están revisados con `git diff --check`; requieren CI.

## Fuera de alcance

- Diseñar o implementar el dominio HD-016 de alertas.
- Inventar health Edge/Arduino, MQTT, hardware, deployment o credenciales.
- Cambiar schemas Core, keys compartidas o contratos OpenAPI.
- Background notifications/push, cámara y release hardening.
