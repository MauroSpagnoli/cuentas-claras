# ADR-0003 · Room como fuente de verdad (offline-first)

- Estado: Aceptada
- Fecha: 2026-10-13

## Contexto
La app Android consume una API remota. Un patrón habitual (llamar a la red y pintar el resultado) deja la UI vacía sin conexión y provoca parpadeos y estados inconsistentes. Además, la red introduce latencia y fallos parciales.

## Decisión
**Room es la única fuente de verdad para la UI.** Las pantallas observan `Flow`/`PagingData` que provienen de Room. La red **escribe** en Room (hidrata/actualiza) y nunca pinta directamente. Las escrituras del usuario:
- **online**: se envían a la API y, al `2xx`, actualizan Room;
- **offline**: se encolan en una tabla `outbox` y un `SyncWorker` (WorkManager) las drena con `Idempotency-Key` y backoff exponencial.

## Consecuencias
- Rendimiento y UX predecibles: arranque instantáneo desde caché y funcionamiento sin conexión en lectura.
- Un solo origen de datos → se elimina la clase de bugs "UI distinta según de dónde vino el dato".
- Más trabajo inicial: mappers de tres niveles (DTO ↔ entidad Room ↔ modelo de dominio) y lógica de sync.
- Los conflictos requieren bloqueo optimista (`version`) y manejo explícito de `409`.

## Alternativas consideradas
- **Red como fuente de verdad**: simple al principio, frágil sin conexión. Descartado.
- **Doble fuente con merge en memoria**: genera inconsistencias y estados imposibles.
- **Solo caché (sin outbox)**: no permite crear/editar sin conexión. Se deja como evolución en la fase 8.
