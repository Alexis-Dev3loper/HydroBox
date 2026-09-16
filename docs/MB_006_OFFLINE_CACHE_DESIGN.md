# MB-006 — Offline, cache y manejo de errores

Actualizado: **2026-09-16**.

Estado: **IMPLEMENTADO / VERIFICADO / PUBLICADO**.

## Auditoría del estado heredado

- `HttpHydroDomainApi` ejecuta cada lectura contra API v1 y conserva únicamente
  datos en estado Compose; al reiniciar el proceso no queda cache de dominio.
- Room v4 persiste solo el perfil humano. DataStore guarda `remember_me` y el
  último email; el vault Android Keystore guarda tokens cifrados.
- Un restore sin red conserva el refresh token, pero no conserva todavía la
  identidad/sitio/scopes suficientes para abrir una sesión de solo lectura.
- `HttpURLConnection` traduce I/O, `429` y `5xx` a errores transitorios. Las
  mutaciones idempotentes repiten una vez, actualmente sin backoff.
- Las pantallas muestran errores aislados, pero no distinguen datos live,
  cacheados recientes, cacheados vencidos o cache no disponible.
- Mobile no usa ya MariaDB ni MQTT directos. HD-012 pertenece al sync durable
  Edge↔Core; sus receipts/cursors no son un cache de cliente Mobile.

## Decisiones de diseño

1. La API central sigue siendo la única autoridad. Mobile usa estrategia
   **network first** para GET; nunca mezcla dos versiones ni resuelve conflictos
   escribiendo desde el cache.
2. Se cachean únicamente envelopes GET que ya pasaron validación contractual.
   El cache no contiene tokens, headers, passwords ni cuerpos de mutaciones.
3. Cada entrada queda aislada por usuario local + `site_key` + path/query API.
   La persistencia vive en `noBackupFilesDir`, usa escritura temporal/rename y
   un límite de entradas; corrupción invalida el store y nunca fabrica datos.
4. Política inicial:

   | Recurso | Freshness UX | Ventana offline máxima |
   |---|---:|---:|
   | catálogos y rangos | 24 h | 7 días |
   | ciclo, actuadores, commands y dosing | 30 s | 24 h |
   | telemetría | 5 min | 24 h |

   Una entrada fuera de la ventana máxima no se sirve. Freshness del cache no
   sustituye `captured_at`, sensor health, `availability_key` ni reported state.
5. GET reintenta una vez con backoff cancelable antes de consultar cache, solo
   para red, `429` o `5xx`. `401`, `403`, errores contractuales y otros fallos
   terminales nunca usan cache para ocultar el error.
6. Cambio de cultivo, commands y dosing **no se encolan offline**. Requieren
   aceptación online; después de éxito invalidan lecturas relacionadas. El
   retry existente conserva UUID/body/idempotency key y añadirá backoff.
7. Una sesión persistente guarda cifrados el principal, sitio y scopes mínimos
   necesarios para restaurar una vista **offline/no verificada**. Si el refresh
   expiró o el servidor rechaza la sesión, se borra y no se muestra cache. Las
   acciones de escritura permanecen deshabilitadas hasta una respuesta API
   autenticada satisfactoria.
8. La UI expone un estado común: live, cache reciente, cache antigua o no
   disponible, con `stored_at` local. Una lectura desde cache nunca se presenta
   como sincronizada ni como confirmación física.
9. Al reconectar, la siguiente lectura live reemplaza la entrada completa. No
   se reutilizan receipts/cursors HD-012 porque Mobile no es un peer Edge.

## Casos de prueba obligatorios

- network success escribe y luego restart recupera la entrada;
- red intermitente: retry cancelable y fallback solo para error transitorio;
- cache reciente, antigua, fuera de ventana y query aislada;
- store corrupto se descarta sin crash ni dato inventado;
- `401` invalida sesión y nunca cae a cache;
- refresh expirado impide restauración offline;
- restore offline conserva lectura pero bloquea mutaciones;
- reconexión vuelve a live y reemplaza cache;
- commands/dosing/crop nunca crean una cola offline;
- cache y logs no contienen Bearer, refresh token ni passwords.

## Fuera de alcance

- sync Edge↔Core, outbox/receipts HD-012 o nuevos endpoints;
- background sync periódico/WorkManager y notificaciones push;
- selección multi-sitio;
- DB/MQTT directos, cambios de contrato API, deployment o hardware real.

## Evidencia local

- `b6e5be7`: auditoría y diseño de autoridad, políticas y casos negativos.
- `d25dd30`: cache JSON atómico en `noBackupFilesDir`, scope usuario/sitio,
  network-first, retry/backoff cancelable, freshness/max-age, invalidación,
  restore offline cifrado y purga en logout/`401`.
- `0348994`: banners live/cache/stale/no-disponible en las cinco pantallas de
  dominio y bloqueo de permisos de escritura mientras la sesión está offline/no
  verificada.
- `6700725`: checkpoint documental publicado.
- El source suma **55 tests**, incluidos restart, corrupción, límite/pruning,
  aislamiento, fresh/stale/expired, cancelación, `401`, invalidación y ausencia
  de fallback para intents físicos.
- CI `35138093458`, job `104935431777`, ejecuta
  `testDebugUnitTest lintDebug assembleDebug`: `BUILD SUCCESSFUL`, 58 tareas.
- `git diff --check` pasa; Mobile #7 está cerrada. Las advertencias heredadas de
  Actions sobre Node 20 y `setup-java@v4` quedan como deuda no bloqueante.
