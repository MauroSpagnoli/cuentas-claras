# 08 · Plan por fases

Roadmap incremental. Cada fase termina con **algo verificable** y criterios de aceptación claros. Alcance inicial de este entregable: **solo diseño** (fases 0).

---

## Fase 0 · Diseño (completada)
**Objetivo:** definir el producto y la ingeniería antes de escribir código.
**Entregables:** visión y alcance, arquitectura, modelo de datos, contrato OpenAPI, algoritmo de liquidación, arquitecturas de cliente y backend, ADRs.
**Criterios de aceptación:**
- [x] `openapi.yaml` describe todos los endpoints del MVP.
- [x] El modelo de datos cubre auth, grupos, gastos, repartos y liquidaciones.
- [x] El algoritmo de liquidación está definido con pseudocódigo y casos de prueba.
- [x] Existen ADRs para las decisiones clave.

---

## Fase 1 · Backend base + autenticación
**Objetivo:** API que levanta, migra y autentica.
**Entregables:**
- Proyecto Spring Boot (Java 17) + `docker-compose` (Postgres + API).
- Flyway `V1__users.sql`, `V2__refresh_tokens.sql`.
- Endpoints `register`, `login`, `refresh`, `logout`, `GET/PATCH /users/me`.
- Seguridad JWT (RS256) + refresh rotativo + `@ControllerAdvice` RFC 7807.
- Swagger UI.
**Criterios de aceptación:**
- `docker-compose up` → `/actuator/health` = `UP`.
- Registro/login/refresh funcionan; un token caducado devuelve `401`; refresh rota y revoca.
- Tests de integración (Testcontainers) en verde para auth.

---

## Fase 2 · Grupos y miembros (completada)
**Objetivo:** espacios compartidos con roles e invitaciones.
**Entregables:**
- [x] `V3__groups.sql`, `V4__group_members.sql`.
- [x] CRUD de grupos, listado "mis grupos" (paginado por cursor), miembros, roles, invitaciones.
- [x] `GroupAccessGuard` (autorización por recurso).
**Criterios de aceptación:**
- [x] Solo miembros ven el grupo; solo owner/admin gestionan.
- [x] Un no-miembro recibe `403 NOT_MEMBER`.
- [x] No se puede quitar al último `OWNER` (`409 LAST_OWNER`).

---

## Fase 3 · Gastos y repartos (completada)
**Objetivo:** registrar y listar gastos con los 4 tipos de reparto.
**Entregables:**
- [x] `V5__expenses.sql`, `V6__expense_shares.sql`, `V7__idempotency_keys.sql`.
- [x] `SplitCalculator` en `domain` (EQUAL/EXACT/PERCENTAGE/SHARES) + `SplitValidationException`.
- [x] CRUD de gastos + listado paginado por cursor con filtros (`from`, `to`, `paidByUserId`, `category`).
- [x] Idempotencia en `POST /expenses` (`Idempotency-Key`: reservar → ejecutar → completar).
**Criterios de aceptación:**
- [x] `Σ share_minor = amount_minor` siempre; violaciones → `422` (`SPLIT_MISMATCH` / `PERCENTAGE_SUM`).
- [x] Reintentar un `POST` con la misma `Idempotency-Key` no duplica el gasto (y `409` si cambia el cuerpo).
- [x] Golden tests del reparto (incluido el reparto de resto) en verde.

---

## Fase 4 · Balances y plan de liquidación (completada)
**Objetivo:** el valor diferencial del producto.
**Entregables:**
- [x] `BalanceCalculator` (netos + pares).
- [x] `SettlementPlanner` (greedy, y exacto para `n ≤ 12`).
- [x] Endpoints `GET /balances` y `GET /settlement-plan`.
- [x] Registro y listado de `settlements` (`V8__settlements.sql`, `Idempotency-Key` en el POST).
- [x] Feed de actividad del grupo (`GET /groups/{id}/activity`, `V9__activity_events.sql`, UC-08).
**Criterios de aceptación:**
- [x] `Σ netos = 0` en todos los casos de prueba.
- [x] El plan salda todas las deudas (se puede verificar aplicándolo).
- [x] Tests unitarios y de propiedad (random) del plan.
- [x] El feed registra gastos, pagos y altas/bajas de miembros, ordenado y paginado por cursor.

---

## Fase 5 · Android: base
**Objetivo:** esqueleto multi-módulo funcional con auth.
**Entregables:**
- Módulos Gradle (`core/*`, `domain`, `data`, `feature/auth`, `app`).
- Hilt + `DispatchersModule` + `NetworkModule` + `DatabaseModule`.
- Retrofit + interceptores (auth, idempotencia) + almacenamiento cifrado de tokens.
- Pantalla de login/registro con MVI + navegación raíz.
**Criterios de aceptación:**
- Login real contra el backend, token guardado cifrado, sesión persistente.
- Al expirar el access, el refresh se ejecuta de forma transparente.
- Tests de ViewModel (Turbine) y de red (MockWebServer).

---

## Fase 6 · Android: grupos y gastos
**Objetivo:** flujo principal de la app.
**Entregables:**
- `feature/groups` y `feature/expenses`.
- Listado paginado (Paging 3 + `RemoteMediator`), detalle, crear/editar gasto con los 4 repartos.
- Caché en Room como fuente de verdad.
**Criterios de aceptación:**
- Los datos sobreviven a cerrar la app (Room) y se refrescan con la red.
- Crear/editar/borrar gasto se refleja tras el siguiente sync.

---

## Fase 7 · Android: balances y liquidación
**Objetivo:** visualizar deudas y saldarlas.
**Entregables:**
- `feature/balances`: netos, pares y **plan de liquidación**.
- Registrar un pago desde la sugerencia.
- Gráficos/estados vacíos y de error.
**Criterios de aceptación:**
- Coincide con el backend (mismos números).
- Registrar un pago actualiza los balances.

---

## Fase 8 · Offline-first y sincronización
**Objetivo:** uso sin conexión (stretch, tras cerrar el MVP online).
**Entregables:**
- Tabla `outbox` + `SyncWorker` (WorkManager) con backoff.
- Escritura optimista y drenado del outbox con `Idempotency-Key`.
- Manejo de `409` (conflicto de versión) en UI.
**Criterios de aceptación:**
- Crear un gasto sin red → aparece en UI y se sincroniza al recuperar conexión.
- Sin duplicados tras reintentos.

---

## Fase 9 · Calidad, CI y publicación
**Objetivo:** proyecto presentable y mantenible.
**Entregables:**
- CI para backend (build + tests + Testcontainers + validación OpenAPI) y para Android (`test`, `lint`, `detekt`).
- Generación de DTOs Android desde `openapi.yaml`.
- README con arquitectura, GIFs, instrucciones y `docker-compose`.
- Release de APK firmado.
**Criterios de aceptación:**
- Ambos pipelines verdes en `main`.
- Un clon nuevo se levanta y se prueba siguiendo el README sin ayuda.

---

## Orden recomendado y dependencias

```mermaid
flowchart LR
    F0[0 Diseño] --> F1[1 Auth]
    F1 --> F2[2 Grupos]
    F2 --> F3[3 Gastos]
    F3 --> F4[4 Balances]
    F4 --> F5[5 Android base]
    F5 --> F6[6 Grupos/Gastos UI]
    F6 --> F7[7 Balances UI]
    F7 --> F8[8 Offline]
    F7 --> F9[9 Calidad/CI]
```

**Regla práctica:** no empezar la UI de una feature antes de que su API esté estable y con tests. Así el cliente se apoya en un contrato fiable.
