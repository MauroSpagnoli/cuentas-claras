# Cuentas Claras · Android

Cliente Android (Kotlin + Jetpack Compose) de **Cuentas Claras**, basado en
Clean Architecture y organizado como un build Gradle multi-módulo.

## Requisitos

- JDK **17**
- Android SDK con `platforms;android-34` (compila contra 34)
- Gradle Wrapper incluido (Gradle 8.13)
- SDK localizado vía `android/local.properties` (`sdk.dir`, archivo no versionado)

Versiones relevantes: AGP 8.3.0, Kotlin 1.9.10, Compose compiler 1.5.3,
Compose BOM 2024.02.01, Hilt 2.48, Room 2.6.1, Retrofit 2.9.0, OkHttp 4.12.0.
Catálogo centralizado en `gradle/libs.versions.toml`.

## Módulos

| Módulo | Rol |
| --- | --- |
| `app` | Aplicación, `MainActivity`, navegación raíz, DI de red/DB. |
| `domain` | Modelos, `AppResult`/`AppError`, contratos de repositorio, casos de uso (JVM puro). |
| `data` | Implementaciones de repositorios, mappers, almacenamiento cifrado de tokens. |
| `core:common` | Cualificadores de dispatchers y `DispatcherModule`. |
| `core:network` | DTOs, APIs Retrofit, interceptores (auth, idempotencia), refresh de token. |
| `core:database` | Room (`CuentasClarasDatabase`), entidades y DAOs. |
| `core:designsystem` | Tema Material 3 y componentes compartidos. |
| `feature:auth` | Pantalla de login/registro (MVI). |

Dirección de dependencias: `app` → `feature/*` → `data` → `domain`, con `core/*`
como infraestructura transversal.

## Comandos

```powershell
.\gradlew.bat :app:assembleDebug          # APK debug
.\gradlew.bat :app:assembleRelease        # APK release
.\gradlew.bat test                        # tests unitarios + Robolectric (JVM)
.\gradlew.bat :app:connectedDebugAndroidTest   # instrumentados (device/emulador)
.\gradlew.bat projects                    # listado de módulos
```

## Backend y entorno local

El `baseUrl` de red se inyecta desde `NetworkConfig` (implementado en `app`).
En debug apunta a `http://10.0.2.2:8080` (host visto desde el emulador) y el
manifest habilita `usesCleartextTraffic` sólo para desarrollo.

```powershell
# desde ../backend
.\mvnw.cmd spring-boot:run    # perfil dev: H2 en :8080
```

La prueba `AuthEndToEndTest` se ejecuta contra el backend real y **se omite
automáticamente** si no está disponible (por lo que CI puede correr
`connectedDebugAndroidTest` sin fallos).

## Estado

Fase 5 (base + auth) completada: build debug/release, 26 tests unitarios en
verde y prueba instrumentada end-to-end contra el backend. Ver
`../docs/08-plan-por-fases.md` para el detalle de fases.
