# MB-003 — Cliente de dominio API v1

Actualizado: **2026-09-16**.

Estado: **IMPLEMENTADO / VERIFICADO / PUBLICADO**.

## Alcance real auditado

Mobile mantiene cuatro operaciones de dominio sobre `/api/*` legacy:

| Consumidor | Legacy | API v1 objetivo |
|---|---|---|
| Sensores | `GET sensores` e IDs numéricos | `GET sites/{site_key}/catalogs/sensors` y `sensor_key` |
| Historial/resumen | `GET registro-mediciones` con columnas agregadas | `GET sites/{site_key}/telemetry/measurements` con UUID, UTC y `readings` |
| Cultivo activo | `GET hortaliza/actual` por ID | `GET sites/{site_key}/cycles?active=true` y `crop_key`/UUID |
| Cambio de cultivo | `POST hortaliza/cambiar` por ID | `PATCH` del ciclo activo + `POST cycles` idempotente por UUID |

Auth `/api/v1/auth/*` y `/api/v1/me` ya pertenecen a MB-002. MQTT directo,
commands y dosing pertenecen a MB-004 y no se modifican aquí.

## Frontera del cliente

- `HydroDomainApi` expone DTOs canónicos sin IDs autoincrementales.
- `HttpHydroDomainApi` recibe la URL HTTPS configurada, un proveedor de contexto
  autenticado y una factory de conexión inyectable para pruebas.
- Cada request de dominio obtiene un access token fresco desde `SessionManager`,
  usa `Authorization: Bearer`, deshabilita redirects y limita timeouts.
- El contexto exige exactamente un `site_key`. Cero sitios falla cerrado; más de
  uno devuelve `mobile.site_selection_required` hasta que exista selector de
  sitio. Nunca se elige silenciosamente el primer sitio.
- Un `401` invalida la sesión local; un `403` conserva la sesión y representa
  falta de scope/rol.
- Las respuestas exitosas deben incluir `meta.api_version=1`. Una versión
  desconocida, envelope malformado, UUID/UTC inválido o lectura desconocida se
  rechaza como `api.invalid_response` sin tolerar fallback legacy.
- Problem Details se reduce a `status`, `code`, `detail` y `request_id`; cuerpos,
  tokens y cabeceras sensibles no aparecen en mensajes ni logs.
- Errores de red son transitorios y observables. MB-003 no inventa datos ni
  cambia automáticamente a `/api/*` legacy.

## DTOs y semántica

- Keys aceptadas: las seis `sensor_key` y seis `crop_key` del OpenAPI v1.
- Telemetría preserva `0` como lectura real y ausencia como key ausente.
- Timestamps se validan como `Instant` UTC y se conservan sin strings locales.
- Paginación usa cursor opaco; Mobile no interpreta ni fabrica cursors.
- El catálogo API aporta nombres, unidades, actividad y duración por defecto.
  Los recursos gráficos se resuelven localmente por `crop_key`, nunca por ID.
- El cambio de cultivo cierra el ciclo activo con un timestamp UTC y crea el
  siguiente con UUID/`Idempotency-Key`. Seleccionar el cultivo ya activo es
  idempotente y no crea otro ciclo.

## Integración UI

- `Resume`, `Sensors`, `History` y `Crops` reciben el port por composición desde
  `AuthViewModel`; dejan de importar el singleton legacy.
- `AuthState` conserva en memoria `site_keys` y scopes obtenidos desde `/me`.
- La UI no muestra una mutación de ciclo a quien carece de `cycle:write`.
- Se retira la preferencia `remote_id`; el ciclo servidor es la autoridad. Cache
  offline/freshness durable queda para MB-006.

## Rollout y rollback

1. Publicar y verificar Mobile con contract tests sobre conexiones en memoria.
2. Configurar `hydrobox.apiBaseUrl=https://<host>/api/v1` localmente, sin
   versionar host ni credenciales.
3. Desplegar Web/auth/Core únicamente bajo su gate operacional.
4. Medir cero uso antes de retirar `/api/*` legacy del backend.

Rollback de la app es el commit anterior. No existe fallback automático por
request porque ocultaría errores de contrato/autorización y prolongaría IDs
legacy. No se ejecuta deployment desde esta fase.

## Verificación requerida

- catálogos, ciclo activo/transición y telemetría/paginación;
- Bearer, site scoping e idempotencia;
- cero frente a missing, UUID y UTC;
- Problem Details, `401`, `403`, offline y schema desconocido;
- ausencia de endpoints/IDs legacy en el source activo;
- `testDebugUnitTest`, `lintDebug` y `assembleDebug` en CI reproducible.

## Evidencia

- `b59726b`: auditoría y diseño del cliente API v1;
- `49c85d3`: port/adapter autenticado, contexto de sitio, DTOs estrictos y tests
  de contrato HTTP en memoria;
- `a831887`: migración de Resumen, Sensores, Historial y Cultivos; elimina
  `HydroApi.kt`, IDs/rutas legacy y telemetría demo;
- `4c939ae` y `c260b7f`: corrigen invocación de parsers y alinean `LogicalKey`
  exactamente con OpenAPI v1;
- `0b7f10c`: permite verificar `PATCH` con el fake JVM sin cambiar producción;
- 40 métodos `@Test` existen en el source tree; los 11 nuevos cubren cliente,
  invalidación de sesión y resolución de `crop_key`;
- escaneo estático sin `id_hortaliza`, `remote_id`, `registro-mediciones`,
  `hortaliza/actual`, `hortaliza/cambiar` ni imports del singleton legacy.

El host actual no dispone de Java/JDK (`JAVA_HOME` y `java` ausentes), por lo
que Gradle no puede ejecutarse localmente. GitHub Actions `35060435836` validó
`testDebugUnitTest` (**40/40**), `lintDebug` y `assembleDebug`; Mobile #4 está
cerrada. No hubo deployment ni se configuraron credenciales reales.
