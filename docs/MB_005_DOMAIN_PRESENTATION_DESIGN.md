# MB-005 — Cultivos, telemetría, unidades y tiempo

Actualizado: **2026-09-16**.

Estado: **IMPLEMENTADO LOCALMENTE / VERIFICACIÓN CI Y PUBLICACIÓN PENDIENTES**.

## Estado heredado

MB-003 ya resolvió la identidad canónica y el transporte:

- cultivos usan `crop_key`, incluido `chard`/Acelga y `arugula`/Rúcula;
- sensores y lecturas usan las seis `sensor_key` canónicas;
- `0` se preserva y una key ausente representa missing;
- `captured_at`/`ingested_at` se validan como `Instant` UTC;
- catálogo, ciclos y telemetría provienen de API v1.

La auditoría MB-005 encontró deuda únicamente en presentación:

- `Crop` conserva días fallback inventados y las vistas los usan si API entrega
  `NULL`;
- Inicio fija unidades (`%`, `°C`, `mV`) y convierte `water_level` a porcentaje
  aunque Core lo publica actualmente en `cm`;
- Inicio muestra el `Instant` UTC crudo y no distingue lectura reciente/antigua;
- Historial fija unidades y rangos óptimos, y usa índices en el eje temporal;
- no existe un modelo puro probado para precisión visual, missing, freshness,
  orden cronológico y conversión a zona local.

## Diseño

1. El catálogo API es autoridad para nombre y `unit_symbol`. Mobile no convierte
   magnitudes físicas ni inventa una unidad cuando el catálogo está ausente.
2. La precisión es una política de visualización por `sensor_key`; no altera el
   `Double` recibido ni implica calibración. pH usa 2 decimales, ORP 0 y las
   demás magnitudes 1.
3. Una key ausente se presenta como `Sin lectura`; `0` se formatea como dato
   real. No se usa Elvis/default numérico.
4. `captured_at` es la autoridad de freshness. Se presenta en la zona local del
   dispositivo y se conserva como `Instant` para ordenar/filtrar.
5. El umbral UX Mobile inicial es 5 minutos y queda explícito/injectable. Solo
   describe antigüedad de la lectura en cliente; no sustituye health del sensor,
   disponibilidad Edge ni una futura política central.
6. Las series se ordenan por `captured_at`, omiten únicamente valores ausentes y
   conservan cero. Las etiquetas temporales se derivan de los timestamps reales.
7. `default_cycle_days=NULL` permanece desconocido. Mobile muestra que no hay
   duración configurada y envía `null` al crear el ciclo; no recupera días
   hardcodeados locales.
8. Rangos óptimos de Historial, cuando se muestren, vienen del cultivo activo y
   `sensor-ranges`; no se mantienen constantes locales.
9. Keys desconocidas en catálogos/respuestas siguen fallando cerrado en el
   adapter. Los recursos gráficos continúan siendo una asociación local por key,
   no una fuente de nombres, unidades o duración.

## Verificación requerida

- tests puros de precisión, unidad, cero, missing, fresh/stale y zona local;
- tests de series desordenadas, missing y cero;
- tests del adapter para keys de catálogo desconocidas;
- escaneo sin `fallbackCycleDays` ni unidad `%` aplicada a `water_level`;
- `testDebugUnitTest`, `lintDebug` y `assembleDebug` en CI JDK 17/SDK 36.

## Fuera de alcance

- cache/offline durable y reconciliación (MB-006);
- eventos/alertas/Automation reales de Historial (MB-007);
- calibración o conversión física Edge;
- cambios API, DB, MQTT, hardware, deployment o credenciales reales.

## Evidencia local

- `64d5566`: auditoría y diseño de semántica de presentación.
- `cbf1b94`: modelo puro de unidad/precisión/freshness/tiempo/series y tests;
  contract test adicional rechaza keys de catálogo desconocidas.
- `950139f`: Inicio, Historial y Cultivos consumen unidades/rangos/duración API,
  presentan hora local y eliminan fallbacks físicos/temporales inventados.
- El source tree suma **46 tests**. El escaneo no encuentra
  `fallbackCycleDays`, `%` aplicado a `water_level`, rangos óptimos locales ni
  índices ficticios para el eje temporal.
- `git diff --check` pasa. El host continúa sin JDK/SDK; tests, lint y assemble
  quedan pendientes de la CI posterior al push.
