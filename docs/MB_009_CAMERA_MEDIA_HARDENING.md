# CAM-03 — receptor Mobile, primer corte MB-009

2026-10-10. **Publicado `7edf927`, CI `38107521544` SUCCESS: Node + JVM,
lint y APK verificados**. Este host no tiene JDK/SDK; la evidencia Android
procede de CI, no de compilación local. No es
aceptación de WebView físico, relay, cámara, TLS/NAT/TURN ni piloto listo.

## Frontera y lifecycle

- `CameraMediaPolicy` valida la sesión antes de entregarla a Compose: HTTPS,
  origen con puerto efectivo, sin userinfo/query/fragment/controles/backslash,
  URL <=2048 y bearer visible ASCII entre 32 y 8192 caracteres. Rechazo usa el
  Problem Details `api.invalid_response` existente. No nuevo contrato API.
- El script del documento exige el mismo origen de la playback URL autenticada
  para POST y Location/DELETE; no deriva permisos del Location. Preserva CSP
  relay-only, sin ampliar a `https:` o wildcard. Este consumidor recibe el
  origen de la sesión API autenticada, no inventa un relay hardcodeado.
- Media fetch usa `redirect:error`, `credentials:omit`, `no-referrer`, no-store
  y deadline 10 s. SDP exige 201/application-sdp, stream <=64 KiB, UTF-8 válido,
  video y deadline 10 s. ICE retira su listener incluso por timeout.
- Generación/stop separan respuesta tardía y estado actual. Cerrar durante POST
  no restaura peer/bearer/video; solo cierra el recurso tardío confinado. Expiry
  detiene peer y video; track no se presenta como reproducción: se espera playing.
- JSON embebido escapa delimitadores HTML para que un valor no termine script.
  No logs, almacenamiento browser, caché media ni secretos versionados.
- WebView conserva bloqueo cleartext/SSL fail-closed y storage/file/content
  desactivados. CameraScreen conserva cierre API UUID y expiry desde Kotlin;
  no se rediseña identidad, permisos o navegación en este corte.

Cleanup remoto es **best-effort**, especialmente durante destrucción WebView,
salida o pérdida de red; no afirmar revocación confirmada por un teardown local.
No captura/grabación, hardware, migration persistente ni credenciales reales.

## Evidencia

- `node --test tests/js/camera-web-document.test.mjs`: **12/12**, ejecutando el
  script exacto de la plantilla Kotlin (solo sustituye sus cuatro valores y un
  hook de prueba). DOM/peer/fetch/timers sintéticos, sin red/dispositivo.
  Prueba destinos adversariales, headers, track/playing, ICE, expiry, POST
  tardío, desconexión, status/MIME/SDP/tamaño y ambos timeouts.
- Se añaden **6 tests JVM** (82 `@Test` en source): origen IPv6/puerto/default,
  rechazo de URL/token, integración real del parser API y render/escape HTML.
  Ejecutados mediante testDebugUnitTest en CI verde `38107521544`; no se
  confundió el conteo previo en source con una ejecución local.
- El verificador PowerShell ejecuta primero Node y después la matriz Gradle.
  Esta ejecución pasa Node y termina en el guard JDK requerido, no en PASS
  completo. CI añade Node 22 y mantiene testDebugUnitTest/lintDebug/assembleDebug;
  el run autorizado `38107521544` verificó la matriz completa sin errores.
- OpenAPI/manifest compartidos no cambian. No se adelantará el pin transversal
  hasta push autorizado y CI propietaria verde de este corte.

## Próximo paso

El push autorizado y CI de `7edf927` están cumplidos. No avanzar pins
transversales mientras Web siga bloqueado: `20f6f97` publicado, CI `38107523680`
backend verde / frontend audit failure. SEC-WEB-01 propone una migración Web
independiente aún no aceptada. #27/Web #10/Mobile #10 continúan abiertos;
no dispositivo, media física ni deployment. Los avisos de runners/Actions
no bloqueantes permanecen como CI-01, no se cambian por este corte de cámara.
