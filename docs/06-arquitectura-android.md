# 06 · Arquitectura Android

Reutiliza el stack del todolist (Compose, Hilt, Room, Coroutines/Flow, Navigation) y lo lleva a nivel producción: **multi-módulo**, **MVI**, **offline-first** y **capa de red**.

## 1. Módulos Gradle

```
android/
├── app/                        # Application + MainActivity + navegación raíz
├── core/
│   ├── common/                 # Result, dispatchers, extensiones, Money
│   ├── designsystem/           # tema, tipografía, componentes base
│   ├── network/                # Retrofit, OkHttp, interceptores, DTOs
│   └── database/               # Room, entidades, DAOs, migraciones
├── domain/                     # modelos puros, casos de uso, contratos
├── data/                       # implementación de repositorios, mappers, outbox
└── feature/
    ├── auth/                   # login/registro/refresh
    ├── groups/                 # listado, detalle, miembros, invitaciones
    ├── expenses/               # listado, crear/editar gasto
    ├── balances/               # balances + plan de liquidación
    └── profile/                # perfil y ajustes
```

**Regla de dependencias:** `feature → domain ← data`; `feature` **no** conoce Retrofit ni Room. `app` compone e inyecta.

## 2. Capas y flujo de una pantalla (MVI)

```mermaid
flowchart LR
    UI["Composable"] -->|onEvent| VM["ViewModel (Reducer)"]
    VM --> UC["UseCase"]
    UC --> REPO["Repository (interface, en domain)"]
    REPO --> IMPL["Repo impl (data)"]
    IMPL --> ROOM[("Room")]
    IMPL --> API["Retrofit"]
    ROOM --> IMPL --> VM
    VM -->|UiState (StateFlow)| UI
    VM -->|UiEffect (Channel)| UI
```

Contrato por pantalla:
- **`UiState`**: `data class` inmutable con todo lo que pinta la UI.
- **`UiEvent`**: funciones `onX()` en el ViewModel (acciones del usuario).
- **`UiEffect`**: navegación, snackbars → `Channel<UiEffect>` (one-shot).

Ejemplo de estado (balances):
```kotlin
sealed interface BalancesUiState {
    data object Loading : BalancesUiState
    data class Content(
        val members: List<BalanceUi>,
        val pairs: List<DebtUi>,
        val plan: List<SettlementStepUi>,
        val isRefreshing: Boolean
    ) : BalancesUiState
    data class Error(val message: String) : BalancesUiState
}
```

## 3. Modelo de dominio (sin dependencias de framework)

```kotlin
@JvmInline value class Money(val minor: Long, val currency: Currency) {
    operator fun plus(other: Money): Money { /* mismo currency */ }
    operator fun minus(other: Money): Money
    fun format(locale: Locale): String
}

data class SplitPart(val userId: UserId, val amount: Money)

sealed interface Split {
    data class Equal(val participants: List<UserId>) : Split
    data class Exact(val parts: List<SplitPart>) : Split
    data class Percentage(val parts: List<PercentPart>) : Split
    data class Shares(val parts: List<SharePart>) : Split
}
```

Un `SplitCalculator` (puro, en `domain`) resuelve `Split → List<SplitPart>` con la invariante de suma. Es **testeable sin Android**.

## 4. Capa de red

- **Retrofit + OkHttp** + `kotlinx.serialization`.
- Interceptor `AuthInterceptor`: añade `Authorization: Bearer`.
- `Authenticator` (o interceptor de refresh): al recibir `401`, llama a `/auth/refresh` **una vez** y reintenta; si falla, cierra sesión.
- Interceptor de idempotencia: añade `Idempotency-Key` (UUID) en `POST` de escritura.
- Errores: se mapean a un `ApiResult` sellado (`Success`, `HttpError(problem)`, `NetworkError`, `Unknown`), y de ahí a errores de dominio.

## 5. Capa de datos (offline-first)

- **Room = fuente de verdad.** La UI observa `Flow` de la base de datos; la red escribe en Room.
- **Lectura paginada**: `Paging 3` + `RemoteMediator` (red → Room → UI).
- **Escritura offline**: tabla `outbox(id, entityType, payload, createdAt, status)`.
  - Se guarda la mutación y se refleja de forma **optimista** en Room.
  - `WorkManager` (`SyncWorker`, con `Constraints`) drena el outbox con backoff exponencial.
  - Se marca como `SENT`/`FAILED`; los fallos definitivos se muestran como error de la operación.
- **Conflictos**: si el `PATCH` devuelve `409`, se recarga el recurso y se notifica al usuario.

## 6. Inyección de dependencias (Hilt)

| Módulo Hilt | Provee |
|---|---|
| `DispatchersModule` | `@IoDispatcher`, `@DefaultDispatcher` (ya usado en el todolist) |
| `NetworkModule` | `OkHttpClient`, `Retrofit`, `Json`, `ApiService` |
| `DatabaseModule` | `AppDatabase`, DAOs |
| `DataModule` / `RepositoryModule` | `@Binds` de repos `interface → impl` |
| `UseCaseModule` | (opcional) agrupación de casos de uso |

`app` es `@HiltAndroidApp`; `MainActivity` es `@AndroidEntryPoint`.

## 7. Navegación

- **Navigation Compose** con rutas **type-safe** (`@Serializable` route objects).
- **Deep links** para invitaciones: `https://cuentas-claras.app/invite/{code}` → pantalla de aceptar.
- Back stack coherente: login vs. sesión activa (`auth` como grafo separado).

## 8. Almacenamiento seguro de tokens

- `EncryptedSharedPreferences` (Jetpack Security) o DataStore cifrado para `accessToken`/`refreshToken`.
- Nunca se persisten tokens en texto plano ni en `SharedPreferences` común.

## 9. Testing

| Nivel | Herramientas | Qué cubre |
|---|---|---|
| Dominio | JUnit 5 / 4, `kotlin.test` | `SplitCalculator`, liquidación, `Money` |
| ViewModel | **MockK + Turbine** + `kotlinx-coroutines-test` | estados emitidos, efectos |
| Red | **MockWebServer** + fixtures JSON | parsing, errores, refresh |
| Datos | Room in-memory / Robolectric | DAOs, mappers, outbox |
| UI | **Compose UI test + Robolectric** (JVM) e instrumentados | render e interacción |

Objetivo de cobertura: **dominio y ViewModels > 80 %** (donde está la lógica), sin perseguir cobertura en Compose.

## 10. Cómo se ve respecto al todolist

| Aspecto | todolist | Cuentas Claras |
|---|---|---|
| Módulos | 1 (`:app`) | Multi-módulo por feature |
| Patrón UI | MVVM simple | **MVI** (UiState/Event/Effect) |
| Persistencia | Room local | Room + **red** + outbox |
| Red | — | Retrofit + auth + idempotencia |
| Auth | — | JWT + refresh + almacenamiento cifrado |
| Paginación | — | Paging 3 con `RemoteMediator` |
| Dinero | `String`/`Long` sin reglas | `Money` con invariantes |
