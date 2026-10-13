# Cuentas Claras · Backend (Fase 1)

API REST de autenticación y perfil construida con **Spring Boot 3 (Java 17)**.
Fase 1 del [plan por fases](../docs/08-plan-por-fases.md).

## Qué incluye esta fase

- Registro, login, refresh (con **rotación** y detección de reuso) y logout.
- `GET/PATCH /api/v1/users/me` (perfil).
- Access token **JWT RS256** (15 min) + refresh token **opaco** hasheado (30 días).
- Errores **RFC 7807**, `X-Request-Id`, Swagger UI y Actuator.
- Migraciones **Flyway** (`V1`, `V2`) para PostgreSQL.
- Tests: unitarios, flujo HTTP completo (H2) e integración con PostgreSQL (Testcontainers).

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

- `TokenServiceTest` y `AuthApiH2Test` corren **siempre** (sin Docker).
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
├── application/    # casos de uso (auth, user) y seguridad
├── domain/         # modelos y puertos de repositorio (sin framework)
└── infrastructure/ # JPA, seguridad (JWT) y configuración
```
