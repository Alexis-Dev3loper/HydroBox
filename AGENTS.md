# HydroBox Mobile — Continuidad para agentes

Este repositorio es la aplicación Android de HydroBox. En el workspace completo, la documentación canónica vive en `../Desktop/docs/project/`; comenzar por `CURRENT_STATE.md`, `DECISIONS.md`, `ROADMAP.md` y `ARCHITECTURE.md`, y consultar `PROJECT_CONTEXT.md` cuando haga falta contexto amplio.

## Inicio de sesión

1. Ejecutar `git status --short`, `git branch --show-current` y `git log -5 --oneline --decorate`.
2. Preservar cambios locales y verificar que la documentación coincide con el código real.
3. Confirmar contratos Web/API/MQTT antes de cambiar integración.
4. Actualizar el estado canónico del Desktop al cerrar una etapa compartida.

## Stack real auditado

- Android nativo, Kotlin 2.0.21, AGP 8.13, Java 17, compile/target SDK 36, min SDK 24.
- Jetpack Compose/Material 3, Navigation, Room, DataStore, coroutines, Coil y HiveMQ MQTT.
- API implementada con `HttpURLConnection`; base HTTPS fija y sin versión visible.
- MQTT usa host fijo, puerto 1883, QoS 1 y sin TLS visible.
- Persistencia local actual guarda `passwordPlain` en Room: **RIESGO PENDIENTE**, no patrón a reutilizar.
- Los únicos tests detectados son los ejemplos unit/instrumented del template.

## Reglas específicas

- Mobile objetivo consume la API central; nunca MariaDB directa.
- No ampliar el acoplamiento a endpoints, hosts, topics o IDs físicos hardcodeados.
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
