# Cuentas Claras

App de **gastos compartidos** (estilo Splitwise): crea grupos, registra gastos, ve quién debe a quién y **liquida las deudas con el mínimo número de transferencias**.

Es un proyecto **full-stack** pensado como pieza de portfolio:

- 📱 **Cliente Android**: Kotlin + Jetpack Compose + Hilt + Room + Retrofit + MVI.
- ☕ **Backend**: Java + Spring Boot + PostgreSQL + JWT, con contrato **OpenAPI** como fuente de verdad.

> **Estado: diseño completo + Fases 1 y 2 (backend) implementadas.** El repositorio
> contiene la documentación de arquitectura, modelo de datos, contrato de API y
> plan por fases, y ya el **backend de autenticación, grupos y miembros** en
> `backend/` (Spring Boot, JWT, Flyway, Swagger y tests). El cliente Android
> llegará en fases posteriores.

---

## ¿Por qué este proyecto?

A diferencia de un CRUD, aquí hay sustancia técnica real y verificable:

| Reto | Dónde se demuestra |
|---|---|
| **Algoritmo** no trivial | Liquidación de deudas minimizando transferencias |
| **Aritmética de dinero exacta** | Importes en unidades mínimas (`BIGINT`), nunca `float` |
| **Autorización por recurso** | Roles dentro de un grupo (owner/admin/member) |
| **Offline-first** | Room como fuente de verdad + cola de mutaciones (outbox) |
| **API profesional** | JWT con refresh rotativo, paginación por cursor, idempotencia, RFC 7807 |
| **Calidad** | Testcontainers, MockWebServer, Turbine, CI para ambos lados |

---

## Alcance del MVP

- Autenticación (registro, login, refresh, logout) y perfil.
- Grupos con miembros, roles e invitaciones (código/enlace).
- Gastos con 4 formas de reparto: **igual, exacto, porcentaje y por partes**.
- Balances por grupo: netos por miembro y pares "quién debe a quién".
- **Plan de liquidación** sugerido (algoritmo).
- Registro de pagos/liquidaciones reales.

**Fuera del MVP** (documentado como futuro): multi-divisa real, adjuntos/recibos, notificaciones push, apps iOS/desktop, suscripciones.

---

## Stack

**Backend**
`Java 17` · `Spring Boot 3.x` · `Spring Web` · `Spring Security + JWT` · `Spring Data JPA` · `PostgreSQL` · `Flyway` · `Bean Validation` · `springdoc-openapi` · `Docker` · `JUnit 5 + Mockito + Testcontainers`

**Android**
`Kotlin` · `Jetpack Compose (Material 3)` · `Hilt` · `Room` · `Retrofit + OkHttp + Kotlinx Serialization` · `Coroutines/Flow` · `Navigation Compose` · `Paging 3` · `WorkManager` · `Coil` · `MockK + Turbine + MockWebServer + Robolectric`

---

## Estructura planeada (monorepo)

```
cuentas-claras/
├── android/                 # App Android (Gradle multi-módulo)
│   ├── app/
│   ├── core/{common,network,database,designsystem}
│   ├── domain/
│   ├── data/
│   └── feature/{auth,groups,expenses,balances,profile}
├── backend/                 # API Spring Boot
│   └── src/main/java/...    # capas: api / application / domain / infrastructure
├── infra/                   # docker-compose, scripts, postman
└── docs/                    # ESTE repositorio de diseño
```

---

## Progreso

| Fase | Estado |
|---|---|
| **1 · Backend base + autenticación** | ✅ Implementada — ver [`backend/README.md`](backend/README.md) |
| **2 · Grupos y miembros** | ✅ Implementada — CRUD de grupos, roles, invitaciones y `GroupAccessGuard` |
| 3 · Gastos y repartos | ⏳ Pendiente |
| 4 · Balances y liquidación | ⏳ Pendiente |
| 5 · Android: base + auth | ⏳ Pendiente |
| 6+ · Resto de features Android | ⏳ Pendiente |

---

## Documentación

| Documento | Contenido |
|---|---|
| [`docs/01-vision-y-alcance.md`](docs/01-vision-y-alcance.md) | Problema, usuarios, casos de uso, requisitos funcionales y no funcionales |
| [`docs/02-arquitectura.md`](docs/02-arquitectura.md) | Visión general, componentes, autenticación, offline-first, entornos |
| [`docs/03-modelo-de-datos.md`](docs/03-modelo-de-datos.md) | ERD, tablas, columnas, índices, invariantes |
| [`docs/04-contrato-api.md`](docs/04-contrato-api.md) | Convenciones REST + resumen de endpoints |
| [`docs/api/openapi.yaml`](docs/api/openapi.yaml) | Especificación OpenAPI 3.0.3 completa de `v1` |
| [`docs/05-algoritmo-liquidacion.md`](docs/05-algoritmo-liquidacion.md) | Cómo se calculan balances y el plan de liquidación |
| [`docs/06-arquitectura-android.md`](docs/06-arquitectura-android.md) | Módulos, MVI, offline-first, DI, testing |
| [`docs/07-arquitectura-backend.md`](docs/07-arquitectura-backend.md) | Capas, seguridad, persistencia, errores, testing, despliegue |
| [`docs/08-plan-por-fases.md`](docs/08-plan-por-fases.md) | Roadmap por fases con entregables y criterios de aceptación |
| [`docs/adr/`](docs/adr/) | Decisiones de arquitectura (ADRs) |

---

## Decisiones clave (resumen)

- **`BIGINT` en unidades mínimas** para dinero (ver [ADR-0001](docs/adr/0001-dinero-como-enteros.md)).
- **JWT de acceso corto + refresh rotativo** persistido por hash ([ADR-0002](docs/adr/0002-jwt-refresh-rotativo.md)).
- **Room como fuente de verdad** en Android, con outbox para mutaciones offline ([ADR-0003](docs/adr/0003-room-source-of-truth.md)).
- **Paginación por cursor** en colecciones grandes ([ADR-0004](docs/adr/0004-paginacion-por-cursor.md)).
- **Algoritmo de liquidación heurístico** (mín. transacciones es NP-hard) ([ADR-0005](docs/adr/0005-algoritmo-liquidacion.md)).

---

*Proyecto de portfolio. Diseño primero, implementación por fases.*
