# Cuentas Claras · Backend (Fases 1-4)

API REST de autenticación, perfil, grupos, miembros, gastos, balances y
liquidaciones construida con **Spring Boot 3 (Java 17)**. Fases 1, 2, 3 y 4 del
[plan por fases](../docs/08-plan-por-fases.md).

## Qué incluye

- Registro, login, refresh (con **rotación** y detección de reuso) y logout.
- `GET/PATCH /api/v1/users/me` (perfil).
- **Grupos**: alta, listado "mis grupos" (paginado por cursor), detalle, edición y borrado lógico.
- **Miembros**: listado, alta por email, cambio de rol (OWNER/ADMIN/MEMBER) y baja.
- **Invitaciones** por código/enlace (`POST /groups/{id}/invites`, `POST /invites/{code}/accept`).
- **`GroupAccessGuard`**: autorización por recurso (miembro mínimo, gestor, owner) y la
  invariante "el grupo conserva al menos un OWNER".
- **Gastos con reparto**: los 4 tipos (`EQUAL`, `EXACT`, `PERCENTAGE`, `SHARES`) resueltos
  por `SplitCalculator` (dominio puro) garantizando `Σ shareMinor = amountMinor`; reparto
  de resto determinista.
- **Listado de gastos** paginado por cursor con filtros `from`, `to`, `paidByUserId`, `category`.
- **Idempotencia** en `POST /expenses` vía cabecera `Idempotency-Key` (reservar → ejecutar →
  completar): un reintento no duplica el gasto y un cuerpo distinto con la misma clave da `409`.
- **Balances** por grupo (`GET /groups/{id}/balances`): neto por miembro (positivo = le deben)
  y deudas mínimas por pares, calculadas por `BalanceCalculator` (dominio puro).
- **Plan de liquidación** (`GET /groups/{id}/settlement-plan`): mínimo de transferencias que
  salda el grupo. `SettlementPlanner` resuelve el óptimo exacto (subset-sum) hasta 12
  participantes y una heurística voraz por encima; reporta el algoritmo usado (`EXACT`/`GREEDY`).
- **Pagos** (`GET/POST /groups/{id}/settlements`): registro de liquidaciones reales con
  `Idempotency-Key` opcional; los balances pasan a reflejar los pagos.
- **Feed de actividad** del grupo (`GET /groups/{id}/activity`, paginado por cursor): registra
  altas/ediciones/borrados de gastos, pagos y altas/bajas de miembros. Se escribe en la misma
  transacción que el cambio de negocio (`ActivityRecorder`, `V9__activity_events.sql`).
- Access token **JWT RS256** (15 min) + refresh token **opaco** hasheado (30 días).
- Errores **RFC 7807**, `X-Request-Id`, Swagger UI y Actuator.
- Migraciones **Flyway** (`V1`–`V9`) para PostgreSQL.
- Tests: unitarios, flujos HTTP completos (H2) e integración con PostgreSQL (Testcontainers).

## Requisitos

- JDK **17**
- No hace falta instalar Maven: usa el wrapper `./mvnw` (Windows: `mvnw.cmd`).
- Docker **solo** para el perfil Postgres y los tests con Testcontainers (opcionales).

## Ejecutar sin Docker (perfil `dev`, H2 en memoria)

```bash
./mvnw spring-boot:run
```

- API: http://localhost:8080
- Swagger UI: http://localhost:8080/swagger-ui.html
- Health: http://localhost:8080/actuator/health

El perfil `dev` es el **por defecto**: no necesita base de datos externa.

### Usuario de demostración (entrar sin registrarse)

Al arrancar en el perfil `dev`, `DemoUserSeeder` crea un usuario de prueba de
forma **idempotente** (si ya existe, no hace nada):

| Campo | Valor |
|---|---|
| Email | `demo@cuentasclaras.app` |
| Contraseña | `demo1234` |
| Moneda | `EUR` |

Se configura (y se puede desactivar) en `application-dev.yml`:

```yaml
app:
  demo-user:
    enabled: true
    email: demo@cuentasclaras.app
    password: demo1234
    display-name: Demo
    default-currency: EUR
```

> Solo existe en el perfil `dev`. Como la base es H2 `create-drop`, el usuario se
> recrea en cada arranque; en los perfiles con Postgres (`docker`, `test`) no se crea.

## Ejecutar con PostgreSQL

```bash
# desde la raíz del repo
docker compose -f infra/docker-compose.yml up --build
```

## Perfiles

| Perfil | Base de datos | Esquema |
|---|---|---|
| `dev` (por defecto) | H2 en memoria | Hibernate `create-drop` |
| `docker` | PostgreSQL | Flyway + Hibernate `validate` |
| `test` | PostgreSQL (Testcontainers) | Flyway + Hibernate `validate` |

## Tests

```bash
./mvnw test
```

- `TokenServiceTest`, `SplitCalculatorTest`, `BalanceCalculatorTest`, `SettlementPlannerTest`,
  `AuthApiH2Test`, `GroupApiH2Test`, `ExpenseApiH2Test`, `BalanceAndSettlementApiH2Test`,
  `ActivityApiH2Test` y `DemoUserSeederTest` corren **siempre** (sin Docker).
- `MigrationSmokeH2Test` aplica **todo** el historial Flyway sobre H2 (modo PostgreSQL) y deja
  que Hibernate (`ddl-auto=validate`) compruebe que las entidades coinciden con las migraciones.
- `AuthApiPostgresTest` se **omite** automáticamente si no hay Docker.

## Endpoints

| Método | Ruta | Descripción |
|---|---|---|
| POST | `/api/v1/auth/register` | Crea usuario y devuelve tokens |
| POST | `/api/v1/auth/login` | Login |
| POST | `/api/v1/auth/refresh` | Rota el refresh |
| POST | `/api/v1/auth/logout` | Revoca el refresh |
| GET | `/api/v1/users/me` | Perfil |
| PATCH | `/api/v1/users/me` | Actualiza perfil |
| GET | `/api/v1/groups` | Lista "mis grupos" (cursor: `limit`, `cursor`) |
| POST | `/api/v1/groups` | Crea grupo (el creador es `OWNER`) |
| GET | `/api/v1/groups/{groupId}` | Detalle (solo miembros) |
| PATCH | `/api/v1/groups/{groupId}` | Edita (OWNER/ADMIN) |
| DELETE | `/api/v1/groups/{groupId}` | Borrado lógico (OWNER) |
| GET | `/api/v1/groups/{groupId}/members` | Lista miembros (solo miembros) |
| POST | `/api/v1/groups/{groupId}/members` | Alta por email (OWNER/ADMIN) |
| PATCH | `/api/v1/groups/{groupId}/members/{userId}` | Cambia rol (OWNER/ADMIN) |
| DELETE | `/api/v1/groups/{groupId}/members/{userId}` | Expulsa o abandona |
| POST | `/api/v1/groups/{groupId}/invites` | Devuelve código/enlace (cualquier miembro) |
| POST | `/api/v1/invites/{code}/accept` | Une al usuario con el código |
| GET | `/api/v1/groups/{groupId}/expenses` | Lista gastos del grupo (cursor + filtros) |
| POST | `/api/v1/groups/{groupId}/expenses` | Crea gasto con reparto (`Idempotency-Key` opcional) |
| GET | `/api/v1/expenses/{expenseId}` | Detalle del gasto (solo miembros) |
| PATCH | `/api/v1/expenses/{expenseId}` | Edita (creador o OWNER/ADMIN) |
| DELETE | `/api/v1/expenses/{expenseId}` | Borrado lógico (creador o OWNER/ADMIN) |
| GET | `/api/v1/groups/{groupId}/balances` | Balances netos y deudas por pares |
| GET | `/api/v1/groups/{groupId}/settlement-plan` | Plan de liquidación (mín. transferencias) |
| GET | `/api/v1/groups/{groupId}/settlements` | Lista pagos registrados (cursor) |
| POST | `/api/v1/groups/{groupId}/settlements` | Registra un pago (`Idempotency-Key` opcional) |
| GET | `/api/v1/groups/{groupId}/activity` | Feed de actividad del grupo (cursor) |

Reglas de autorización (Fase 2): un no-miembro recibe `403 NOT_MEMBER`;
rol insuficiente → `403 INSUFFICIENT_ROLE`; no se puede degradar ni quitar
al último `OWNER` (`409 LAST_OWNER`).

Reglas de gastos (Fase 3): cualquier miembro activo puede crear gastos, pero el
pagador y todos los participantes deben ser miembros activos (`422 INVALID_PARTICIPANT`);
la moneda del gasto debe coincidir con la del grupo (`400`); un reparto que no cuadra
da `422 SPLIT_MISMATCH` y unos porcentajes que no suman 100 dan `422 PERCENTAGE_SUM`.
Editar o borrar un gasto requiere ser su creador o tener rol `OWNER`/`ADMIN`.

Reglas de balances y pagos (Fase 4): solo los miembros ven balances y plan
(`403 NOT_MEMBER` para el resto). Cualquier miembro activo puede registrar un pago,
pero el pagador y el receptor deben ser miembros activos y distintos
(`422 INVALID_PARTICIPANT` / `400`); la moneda debe coincidir con la del grupo.
El plan de liquidación es una **sugerencia**: solo los pagos persistidos cambian
los balances.

Reglas de actividad (Fase 4, UC-08): el feed solo lo ven los miembros
(`403 NOT_MEMBER` para el resto) y se pagina por cursor. Los eventos se generan
solos al crear/editar/borrar gastos, registrar pagos y unirse o salir miembros;
no hay endpoint para escribirlos a mano.

### Ejemplo

```bash
curl -X POST http://localhost:8080/api/v1/auth/register \
  -H "Content-Type: application/json" \
  -d '{"email":"ana@example.com","password":"secret123","displayName":"Ana"}'
```

## Claves JWT

En desarrollo se genera un par RSA **efímero** al arrancar. En producción se
inyectan claves PEM vía configuración:

```yaml
app:
  jwt:
    private-key: ${JWT_PRIVATE_KEY}
    public-key: ${JWT_PUBLIC_KEY}
```

## Estructura

```
src/main/java/app/cuentasclaras/
├── api/            # controllers, DTOs y manejo de errores
├── application/    # casos de uso (auth, user, group, expense, balance, settlement, activity, idempotency) y seguridad
├── domain/         # modelos y puertos de repositorio (sin framework)
└── infrastructure/ # JPA, seguridad (JWT), configuración y bootstrap
```

El paquete `infrastructure/bootstrap` contiene los inicializadores de datos de
desarrollo (por ahora `DemoUserSeeder`, activo solo con el perfil `dev`).
