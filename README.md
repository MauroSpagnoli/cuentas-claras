# Cuentas Claras

[![backend-ci](https://github.com/MauroSpagnoli/cuentas-claras/actions/workflows/backend-ci.yml/badge.svg)](https://github.com/MauroSpagnoli/cuentas-claras/actions/workflows/backend-ci.yml)
[![android-ci](https://github.com/MauroSpagnoli/cuentas-claras/actions/workflows/android-ci.yml/badge.svg)](https://github.com/MauroSpagnoli/cuentas-claras/actions/workflows/android-ci.yml)

App de **gastos compartidos** (estilo Splitwise): crea grupos, registra gastos, ve quién debe a quién y **liquida las deudas con el mínimo número de transferencias**.

Es un proyecto **full-stack** pensado como pieza de portfolio:

- 📱 **Cliente Android**: Kotlin + Jetpack Compose + Hilt + Room + Retrofit + MVI, **offline-first**.
- ☕ **Backend**: Java + Spring Boot + PostgreSQL + JWT, con contrato **OpenAPI** como fuente de verdad.

> **Estado: MVP completo (Fases 1–8) + calidad y CI (Fase 9).** Backend entero en `backend/`
> (auth, grupos, miembros, gastos, balances, liquidación, pagos y actividad), cliente Android
> en `android/` y pipeline de CI para ambos lados.

---

## ¿Por qué este proyecto?

A diferencia de un CRUD, aquí hay sustancia técnica real y verificable:

| Reto | Dónde se demuestra |
|---|---|
| **Algoritmo** no trivial | Liquidación de deudas minimizando transferencias |
| **Aritmética de dinero exacta** | Importes en unidades mínimas (`BIGINT`), nunca `float` |
| **Autorización por recurso** | Roles dentro de un grupo (owner/admin/member) |
| **Offline-first** | Room como fuente de verdad + cola de mutaciones (outbox) con `WorkManager` |
| **API profesional** | JWT con refresh rotativo, paginación por cursor, idempotencia, RFC 7807 |
| **Calidad** | Testcontainers, MockWebServer, Turbine, Robolectric y CI para ambos lados |

---

## Alcance del MVP

- Autenticación (registro, login, refresh, logout) y perfil.
- Grupos con miembros, roles e invitaciones (código/enlace).
- Gastos con 4 formas de reparto: **igual, exacto, porcentaje y por partes**.
- Balances por grupo: netos por miembro y pares "quién debe a quién".
- **Plan de liquidación** sugerido (algoritmo) y registro de pagos reales.
- **Sincronización offline**: crear/editar/borrar gastos sin red y drenar la cola al reconectar.

**Fuera del MVP** (documentado como futuro): multi-divisa real, adjuntos/recibos, notificaciones push, apps iOS/desktop, suscripciones.

---

## Stack

**Backend**
`Java 17` · `Spring Boot 3.x` · `Spring Web` · `Spring Security + JWT` · `Spring Data JPA` · `PostgreSQL` · `Flyway` · `Bean Validation` · `springdoc-openapi` · `Docker` · `JUnit 5 + Mockito + Testcontainers`

**Android**
`Kotlin` · `Jetpack Compose (Material 3)` · `Hilt` · `Room` · `Retrofit + OkHttp + Kotlinx Serialization` · `Coroutines/Flow` · `Navigation Compose` · `Paging 3` · `WorkManager` · `MockK + Turbine + MockWebServer + Robolectric · Detekt`

---

## Estructura (monorepo)

```
cuentas-claras/
├── android/                 # App Android (Gradle multi-módulo)
│   ├── app/                 # entrypoint, navegación, DI
│   ├── core/{common,network,database,designsystem}
│   ├── domain/              # modelos, casos de uso, contratos
│   ├── data/                # repositorios, mappers, outbox, sync
│   └── feature/{auth,groups,expenses,balances}
├── backend/                 # API Spring Boot (api / application / domain / infrastructure)
├── infra/                   # docker-compose (PostgreSQL + API)
├── docs/                    # diseño, ADRs y contrato OpenAPI
└── .github/workflows/       # CI de backend, Android y release
```

---

## Cómo ejecutar

### 1. Backend

Sin dependencias externas (H2 en memoria, perfil `dev` por defecto):

```bash
cd backend
./mvnw spring-boot:run        # Windows: mvnw.cmd spring-boot:run
```

- API: <http://localhost:8080/api/v1>
- Swagger UI: <http://localhost:8080/swagger-ui.html>
- Healthcheck: <http://localhost:8080/actuator/health>

Con PostgreSQL (Flyway + `validate`), desde la raíz del repo:

```bash
docker compose -f infra/docker-compose.yml up --build
```

### 2. Cliente Android

Requisitos: **JDK 17** y Android SDK (compileSdk 34).

```bash
cd android
./gradlew :app:assembleDebug          # Windows: gradlew.bat :app:assembleDebug
```

Instala el APK en un emulador o dispositivo e inicia sesión/registro. En el emulador, la app
apunta al backend del host en `http://10.0.2.2:8080/api/v1/` (ver `app/build.gradle`).

### 3. Tests

```bash
# Backend (H2 + Testcontainers)
cd backend && ./mvnw test

# Android (unitarios + Robolectric sobre la JVM)
cd android && ./gradlew test

# Android (instrumentados E2E; requieren emulador + backend levantados)
cd android && ./gradlew :app:connectedDebugAndroidTest
```

> Los tests E2E se **omiten** automáticamente si el backend no responde en `10.0.2.2:8080`.

> `OpenApiContractTest` (`core:network`) valida los DTO escritos a mano contra `docs/api/openapi.yaml` sin
> tocar la red: falla si el contrato y el cliente se separan (campos obligatorios, campos desconocidos,
> esquemas sin mapear o enums del dominio).

### 4. Calidad y release

```bash
cd android
./gradlew detekt          # análisis estático (config en detekt.yml)
./gradlew :app:lintDebug  # Android Lint
```

Para firmar el release, copia `android/keystore.properties.example` a
`android/keystore.properties` (está en `.gitignore`), genera un keystore y ejecuta
`./gradlew :app:assembleRelease`. En CI, `.github/workflows/release.yml` firma el APK/AAB
con secrets al empujar un tag `v*`. Secretos requeridos: `ANDROID_KEYSTORE_BASE64` (el `.jks`
codificado en base64), `ANDROID_KEYSTORE_PASSWORD`, `ANDROID_KEY_ALIAS` y `ANDROID_KEY_PASSWORD`.

---

## Progreso

| Fase | Estado |
|---|---|
| **0 · Diseño** | ✅ Completada — visión, arquitectura, modelo de datos, contrato y plan |
| **1 · Backend base + autenticación** | ✅ Implementada — ver [`backend/README.md`](backend/README.md) |
| **2 · Grupos y miembros** | ✅ Implementada — CRUD de grupos, roles, invitaciones y `GroupAccessGuard` |
| **3 · Gastos y repartos** | ✅ Implementada — 4 tipos de reparto, `SplitCalculator`, cursor + filtros e idempotencia |
| **4 · Balances y liquidación** | ✅ Implementada — `BalanceCalculator`, `SettlementPlanner`, balances por pares, plan, pagos y actividad |
| **5 · Android: base + auth** | ✅ Implementada — multi-módulo, Hilt, Room, Retrofit, login/registro y preferencias |
| **6 · Android: grupos y gastos** | ✅ Implementada — listas paginadas, alta/edición de gastos y reparto |
| **7 · Android: balances y liquidación** | ✅ Implementada — balances, pares, plan sugerido y registro de pagos |
| **8 · Offline-first y sincronización** | ✅ Implementada — outbox + `SyncWorker` con backoff e idempotencia |
| **9 · Calidad, CI y publicación** | 🟡 En curso — Detekt, CI de backend/Android y release firmado |

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
