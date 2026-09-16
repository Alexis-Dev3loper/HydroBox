# HydroBox Mobile — Continuidad para agentes

Este repositorio es la aplicación Android de HydroBox. En el workspace completo, la documentación canónica vive en `../Desktop/docs/project/`; comenzar por `CURRENT_STATE.md`, `DECISIONS.md`, `ROADMAP.md` y `ARCHITECTURE.md`, y consultar `PROJECT_CONTEXT.md` cuando haga falta contexto amplio.

## Inicio de sesión

1. Ejecutar `git status --short`, `git branch --show-current` y `git log -5 --oneline --decorate`.
2. Preservar cambios locales y verificar que la documentación coincide con el código real.
3. Confirmar contratos Web/API/MQTT antes de cambiar integración.
4. Actualizar el estado canónico del Desktop al cerrar una etapa compartida.

## Stack real auditado

- Android nativo, Kotlin 2.0.21, AGP 8.13, Java 17, compile/target SDK 36, min SDK 24.
- Jetpack Compose/Material 3, Navigation, Room, DataStore, coroutines y Coil.
- Auth humana usa `/api/v1/auth/*` y `/api/v1/me`; MB-003 implementa además
  catálogos, ciclos y telemetría `/api/v1/sites/{site_key}/*` con Bearer,
  Problem Details, UUID/UTC y keys canónicas. Está publicado y verificado en CI.
- MB-004 está implementado localmente en `2f8907f`–`9072966`: commands y
  dosing pasan por API v1, la UI separa intención/lifecycle/estado reportado y
  se retiraron cliente, configuración y dependencia MQTT directos. Falta
  publicar esos commits y confirmar la matriz Android en CI.
- Room v4 elimina irreversiblemente `passwordPlain`; access/refresh tokens solo
  se guardan cifrados con una clave Android Keystore y quedan fuera de backups.
- MB-001 validó CI reproducible con JDK 17/SDK 36. MB-002 quedó publicado y
  verificado en CI `35036523635`. MB-003 está publicado hasta `0b7f10c`; sus
  40 pruebas, `lintDebug` y `assembleDebug` pasan en CI `35060435836`. El host
  actual no tiene JDK/SDK, por lo que la matriz completa se valida en CI.

## Reglas específicas

- Mobile objetivo consume la API central; nunca MariaDB directa.
- DataStore no es autoridad de sesión ni almacén de tokens; solo conserva la
  preferencia `remember_me` y el último email cuando el usuario lo solicita.
- No ampliar el acoplamiento a endpoints, hosts, topics o IDs físicos hardcodeados.
- Mobile no publica MQTT directamente; los intents físicos se registran por API v1.
- No persistir nuevos secretos ni copiar contraseñas/tokens a logs, documentación o fixtures.
- No considerar publish MQTT como ACK físico. Respetar lifecycle futuro de comandos.
- Cambios de API requieren contrato versionado y coordinación con Web/Backend.
- No presentar Smart Rules como ML; ML queda casi al final, después de datos reales confiables.
- No modificar firmware/Edge desde este repo.

## Flujo y verificación

```text
AUDIT → DESIGN → IMPLEMENT → TEST → VERIFY → COMMIT → PUSH only with approval
```

Cuando dependencias/SDK estén disponibles y el alcance lo autorice, usar Gradle Wrapper para tests/lint/build relevantes. Revisar `git diff --check`, diff completo y staging explícito. No hacer commit/push sin autorización.

## Flujo autónomo y de recuperación de Codex

HydroBox utiliza dos archivos de instrucciones operativas a nivel del
workspace:

- `CODEX_AUTONOMOUS_MODE.md`
- `CODEX_RECOVERY_PROMPT.md`

Estos archivos se encuentran en la raíz del workspace de HydroBox,
fuera de los repositorios individuales.

### Modo autónomo

Para el trabajo normal de desarrollo, Codex debe seguir:

`CODEX_AUTONOMOUS_MODE.md`

Utilízalo cuando corresponda:

- continuar una fase ya definida del roadmap;
- implementar subfases normales;
- ejecutar pruebas;
- corregir bugs relacionados;
- crear commits locales;
- actualizar documentación de continuidad;
- avanzar automáticamente a la siguiente subfase definida.

El flujo esperado de ejecución es:

AUDIT
→ DESIGN
→ IMPLEMENT
→ TEST
→ VERIFY
→ REVIEW DIFF
→ COMMIT LOCAL
→ UPDATE CURRENT_STATE
→ CONTINUE

No detenerse entre subfases normales salvo que se alcance un HARD GATE.

### Modo de recuperación

Si la ejecución anterior pudo haber sido interrumpida por cualquiera de
las siguientes causas:

- límite de uso/tokens;
- cierre de la aplicación Codex;
- suspensión o apagado de la laptop;
- interrupción manual;
- timeout;
- comando bloqueado;
- pérdida de salida de terminal;
- ejecución incompleta de una herramienta;
- estado de ejecución incierto;

NO continuar basándose en memoria.

Primero leer:

`CODEX_RECOVERY_PROMPT.md`

y seguir completamente su procedimiento de recuperación.

El flujo esperado de recuperación es:

RECOVER
→ VERIFY REAL GIT/PROCESS STATE
→ CLASSIFY INTERRUPTED WORK
→ COMPLETE OR CLOSE THE SAFE UNIT
→ RUN NEEDED TESTS
→ UPDATE CURRENT_STATE
→ CONTINUE AUTONOMOUSLY

Git, el código, el estado de la base de datos y los archivos actuales
son la fuente de verdad.

No asumir que la última operación tuvo éxito o falló únicamente porque
la conversación anterior fue interrumpida.

### Regla de inicio de sesión

Al comenzar una sesión normal:

1. Leer este `AGENTS.md`.
2. Leer la documentación canónica de continuidad de HydroBox.
3. Revisar el estado Git.
4. Si existe evidencia de que la sesión anterior terminó normalmente,
   continuar utilizando `CODEX_AUTONOMOUS_MODE.md`.
5. Si existe evidencia o indicación del usuario de que la sesión
   anterior fue interrumpida, utilizar primero
   `CODEX_RECOVERY_PROMPT.md`.

### Atajos de lenguaje del usuario

Si el usuario dice algo equivalente a:

- "continúa HydroBox";
- "sigue con el proyecto";
- "continúa desde CURRENT_STATE";

utilizar el flujo autónomo normal.

Si el usuario dice algo equivalente a:

- "se acabaron los tokens";
- "se cortó Codex";
- "se apagó/suspendió la laptop";
- "reanuda donde quedó";
- "recupera la sesión anterior";

interpretarlo como una instrucción para utilizar:

`CODEX_RECOVERY_PROMPT.md`

antes de realizar cambios.

### Los HARD GATES siguen vigentes

Ni el modo autónomo ni el modo de recuperación sustituyen los
HARD GATES.

Codex debe detenerse para:

- push;
- merge hacia ramas protegidas;
- deployment real;
- operaciones destructivas;
- secretos o credenciales reales;
- acciones sobre hardware físico;
- flasheo de Arduino;
- cambios arquitectónicos importantes;
- cambios incompatibles en contratos compartidos;
- decisiones de producto importantes que sigan sin resolverse.

La implementación local, pruebas, compilación, bases de datos
temporales aisladas, contenedores temporales de pruebas, documentación
y commits locales normalmente NO requieren aprobación del usuario,
salvo que otra restricción de seguridad lo exija.

### Preservación de contexto

No pedir al usuario que vuelva a explicar el contexto de HydroBox.

Recuperar el contexto del proyecto desde:

- `AGENTS.md`
- `docs/project/CURRENT_STATE.md`
- `docs/project/DECISIONS.md`
- `docs/project/ROADMAP.md`
- `docs/project/ARCHITECTURE.md`
- `docs/project/PROJECT_CONTEXT.md`
- `HYDROBOX_CODEX_MASTER_CONTEXT.md`

Actualizar `CURRENT_STATE.md` después de cada etapa significativa
completada y, cuando sea posible, antes de detener intencionalmente una
sesión larga de trabajo.
