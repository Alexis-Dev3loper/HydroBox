# HD-017 — Mobile security hardening

Actualizado: **2026-09-25**

Estado: **SLICE C IMPLEMENTADO LOCALMENTE / CI PENDIENTE DE PUSH**.

- Android declara `usesCleartextTraffic=false`; el requisito HTTPS del cliente
  ya no depende únicamente de validación lógica.
- Las reglas existentes continúan excluyendo sesión, preferencias auth y Room
  tanto de cloud backup como de device transfer.
- `SensitiveBackupRulesTest` verifica también el bloqueo cleartext.
- La CI usa JDK 17, SDK 36 y actions fijadas por SHA; checkout/setup-java se
  modernizan a v5 y Gradle queda fijado a v4.4.3.
- Dependabot revisará Gradle y GitHub Actions; habilitar Dependabot security
  alerts en settings sigue siendo una acción manual del owner.

No se añadieron certificados, hosts, tokens ni secretos. La validación local
completa depende del JDK/SDK del host o de GitHub Actions.
