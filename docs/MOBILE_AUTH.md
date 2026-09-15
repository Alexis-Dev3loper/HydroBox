# MB-002 — Sesión humana segura

Actualizado: **2026-09-15**.

Estado: **IMPLEMENTADO LOCALMENTE / VERIFICACIÓN CI Y PUBLICACIÓN PENDIENTES**.

## Contrato

Mobile consume la frontera Web API v1 aceptada en `DEC-047`:

- `POST /api/v1/auth/token`: access token opaco de 15 minutos y refresh token
  rotatorio de 30 días;
- `POST /api/v1/auth/refresh`: rota ambos secretos; reuse revoca la familia;
- `POST /api/v1/auth/logout`: revoca la familia de la sesión actual;
- `GET /api/v1/me`: valida el access token y devuelve UUID, rol, sitios y scopes.

La base configurada debe ser una URL HTTPS absoluta que termine en `/api/v1`.
El cliente no sigue redirects, para no reenviar un Bearer a otro destino. Usa el
trust store TLS del sistema; certificate pinning no está implementado.

## Persistencia y autoridad

- La contraseña existe solo como input y body del request de login. Compose usa
  `remember`, no SavedState, y limpia el campo al terminar el intento.
- Room v4 elimina `passwordPlain` mediante migration 3→4, conserva únicamente
  perfil local no sensible y ya no crea un usuario ficticio.
- Android Keystore genera una clave AES-256/GCM no exportable. SharedPreferences
  guarda solo IV y ciphertext autenticado de la sesión.
- DataStore no decide si existe sesión: conserva solo `remember_me` y el último
  email opcional.
- preferencias de sesión, DataStore y DB local quedan excluidos de Auto Backup
  y device transfer. La clave Keystore tampoco se exporta mediante backup.
- Web es la autoridad de identidad y revocación; el perfil Room es cache local.

## Lifecycle

### Login

1. Emite el par de tokens.
2. Valida el access token con `/me`.
3. Persiste/actualiza el principal público en Room.
4. Cifra la sesión y recién entonces publica estado autenticado.

Si falla cualquier paso posterior a emitir tokens, intenta revocar la familia,
borra material local y muestra un error genérico sin eco de secretos.

### Restauración y refresh

- `remember_me=false`: una sesión cifrada que sobrevivió a process death se
  revoca best-effort y se elimina en el siguiente arranque.
- `remember_me=true`: se rota cuando al access token le quedan 30 segundos o
  menos y luego se valida `/me`.
- `401`, `403`, refresh reuse `409`, expiración o corrupción local eliminan la
  sesión local.
- una caída transitoria conserva el ciphertext para reintento, pero no declara
  al usuario autenticado.
- si el servidor rotó el refresh y el nuevo par no puede persistirse, se limpia
  la copia anterior porque reutilizarla revocaría la familia.

### Logout

Si el access token expiró y el refresh sigue vigente, se intenta rotar para
obtener un Bearer válido y revocar en servidor. Con o sin conectividad, la
sesión local se elimina siempre.

## Threat notes y límites

- Keystore reduce la extracción de secretos en reposo, pero no vuelve seguro un
  dispositivo rooteado, desbloqueado o con el proceso comprometido.
- La clave no exige biometría por operación; añadir user authentication cambiaría
  la UX y queda para el hardening de seguridad integral.
- No hay sesión offline autenticada: sin API disponible, el usuario queda fuera
  de la UI protegida aunque el recovery material cifrado pueda conservarse.
- No se usan credenciales reales en tests, documentación ni configuración
  versionada. Logs y excepciones no contienen bodies, passwords o tokens.
- Las rutas de dominio y MQTT legacy se retiran en MB-003/MB-004; esta fase no
  convierte esos contratos en seguros ni productivos.

## Rollout

1. Publicar y desplegar primero la migration/frontera auth Web ya implementada;
   deployment y secretos siguen bajo hard gate.
2. Configurar localmente `hydrobox.apiBaseUrl=https://<host>/api/v1` sin
   versionar host ni credenciales.
3. Instalar/actualizar Mobile; Room ejecuta 3→4 dentro de su migration.
4. Esperar reautenticación si no existe sesión, la clave fue invalidada o el
   ciphertext no puede verificarse.
5. No restaurar la DB/preferencias desde backups antiguos ni intentar recuperar
   el password eliminado.

## Verificación

El source tree contiene 29 unit tests totales; 19 cubren MB-002: migration SQL,
reglas de backup, contrato HTTP, redirects, login, refresh, expiry, reuse,
revocación, errores transitorios, fallo de persistencia tras rotación, logout y
process death. La matriz obligatoria es:

```text
testDebugUnitTest
lintDebug
assembleDebug
```

El host actual no tiene JDK/Android SDK. Por eso estos resultados deben
confirmarse en la CI reproducible de MB-001 antes de marcar MB-002 como
VERIFICADO o cerrar su issue.
