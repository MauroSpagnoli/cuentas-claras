# 04 · Contrato de API

La especificación completa está en [`api/openapi.yaml`](api/openapi.yaml) (OpenAPI **3.0.3**). Este documento resume las convenciones y los endpoints.

## 1. Convenciones

| Tema | Decisión |
|---|---|
| **Versionado** | En la URL: `/api/v1/...` |
| **Formato** | JSON UTF-8; `application/json` |
| **Autenticación** | `Authorization: Bearer <accessToken>` (JWT) |
| **Errores** | RFC 7807 `application/problem+json` |
| **Paginación** | Por **cursor** en colecciones potencialmente grandes |
| **Idempotencia** | Header `Idempotency-Key` en `POST` de escritura |
| **Fechas** | ISO-8601 UTC (`2026-04-01T12:00:00Z`); fechas de negocio `date` |
| **Importes** | `amountMinor` (`long`, unidades mínimas) + `currency` (`ISO 4217`) |
| **IDs** | UUID en string |
| **Correlation** | Header `X-Request-Id` (eco o generado en servidor) |

## 2. Errores (RFC 7807)

```json
{
  "type": "https://cuentas-claras.app/errors/split-mismatch",
  "title": "El reparto no cuadra con el total",
  "status": 422,
  "detail": "La suma de las partes (2999) no es igual al importe (3000).",
  "instance": "/api/v1/groups/7c.../expenses",
  "code": "SPLIT_MISMATCH",
  "errors": [
    { "field": "shares", "message": "sum(shares) != amountMinor" }
  ],
  "correlationId": "b1e6..."
}
```

Códigos de aplicación (extracto): `VALIDATION_ERROR`, `SPLIT_MISMATCH`, `PERCENTAGE_SUM`, `UNAUTHENTICATED`, `FORBIDDEN`, `NOT_MEMBER`, `NOT_FOUND`, `VERSION_CONFLICT`, `DUPLICATE_EMAIL`, `INVALID_CREDENTIALS`, `RATE_LIMITED`.

## 3. Paginación por cursor

Petición:
```
GET /api/v1/groups/{groupId}/expenses?limit=20&cursor=eyJkIjoiMjAyNi0wNC0wMSIsImlkIjoiN2MifQ==
```

Respuesta:
```json
{
  "items": [ /* ... */ ],
  "nextCursor": "eyJkIjoiMjAyNi0wMy0xNSIsImlkIjoiMWEifQ==",
  "hasMore": true
}
```

El cursor es opaco para el cliente (base64 de `(expenseDate, id)`). Ver [ADR-0004](adr/0004-paginacion-por-cursor.md).

## 4. Idempotencia

En `POST` de escritura (gastos, liquidaciones), el cliente envía:

```
Idempotency-Key: 8f2c1e34-...
```

- Si la clave ya existe para el usuario + endpoint con el mismo cuerpo → se devuelve la **respuesta original** (sin duplicar).
- Mismo cuerpo + clave ya usada con cuerpo distinto → `409 VERSION_CONFLICT`.

## 5. Resumen de endpoints (`v1`)

### 5.1 Autenticación
| Método | Ruta | Descripción |
|---|---|---|
| POST | `/auth/register` | Crea usuario y devuelve tokens |
| POST | `/auth/login` | Credenciales → access + refresh |
| POST | `/auth/refresh` | Rota el refresh y emite nuevo access |
| POST | `/auth/logout` | Revoca el refresh actual |

### 5.2 Usuario
| Método | Ruta | Descripción |
|---|---|---|
| GET | `/users/me` | Perfil del usuario autenticado |
| PATCH | `/users/me` | Actualiza nombre, divisa por defecto, locale |

### 5.3 Grupos y miembros
| Método | Ruta | Descripción |
|---|---|---|
| POST | `/groups` | Crea grupo (creador = `OWNER`) |
| GET | `/groups` | Lista grupos del usuario |
| GET | `/groups/{groupId}` | Detalle del grupo |
| PATCH | `/groups/{groupId}` | Renombra / describe / archiva |
| DELETE | `/groups/{groupId}` | Borrado lógico (solo owner) |
| GET | `/groups/{groupId}/members` | Miembros y roles |
| POST | `/groups/{groupId}/members` | Añade miembro por email |
| PATCH | `/groups/{groupId}/members/{userId}` | Cambia rol |
| DELETE | `/groups/{groupId}/members/{userId}` | Expulsa / sale del grupo |
| POST | `/groups/{groupId}/invites` | Genera código/enlace de invitación |
| POST | `/invites/{code}/accept` | Acepta invitación |

### 5.4 Gastos
| Método | Ruta | Descripción |
|---|---|---|
| POST | `/groups/{groupId}/expenses` | Crea gasto con reparto |
| GET | `/groups/{groupId}/expenses` | Lista paginada (filtros: fecha, pagador, categoría) |
| GET | `/expenses/{expenseId}` | Detalle |
| PATCH | `/expenses/{expenseId}` | Edita (creador/admin) |
| DELETE | `/expenses/{expenseId}` | Borrado lógico |

### 5.5 Liquidaciones (pagos)
| Método | Ruta | Descripción |
|---|---|---|
| POST | `/groups/{groupId}/settlements` | Registra un pago real |
| GET | `/groups/{groupId}/settlements` | Lista paginada |

### 5.6 Balances y plan de liquidación
| Método | Ruta | Descripción |
|---|---|---|
| GET | `/groups/{groupId}/balances` | Netos por miembro + pares "debe a" |
| GET | `/groups/{groupId}/settlement-plan` | Transferencias sugeridas (algoritmo) |

### 5.7 Actividad
| Método | Ruta | Descripción |
|---|---|---|
| GET | `/groups/{groupId}/activity` | Feed de eventos del grupo (paginado) |

## 6. Ejemplo: crear un gasto con reparto exacto

**Petición**
```http
POST /api/v1/groups/7c9e.../expenses
Authorization: Bearer <access>
Idempotency-Key: 2b7f... 
Content-Type: application/json

{
  "description": "Cena",
  "amountMinor": 3000,
  "currency": "EUR",
  "paidByUserId": "a1...",
  "splitType": "EXACT",
  "expenseDate": "2026-04-01",
  "shares": [
    { "userId": "a1...", "amountMinor": 1000 },
    { "userId": "b2...", "amountMinor": 1000 },
    { "userId": "c3...", "amountMinor": 1000 }
  ]
}
```

**Respuesta `201`**
```json
{
  "id": "9f...",
  "groupId": "7c9e...",
  "description": "Cena",
  "amountMinor": 3000,
  "currency": "EUR",
  "paidByUserId": "a1...",
  "splitType": "EXACT",
  "expenseDate": "2026-04-01",
  "createdAt": "2026-04-01T20:15:00Z",
  "version": 0,
  "shares": [
    { "userId": "a1...", "amountMinor": 1000, "percentage": null, "weight": null },
    { "userId": "b2...", "amountMinor": 1000, "percentage": null, "weight": null },
    { "userId": "c3...", "amountMinor": 1000, "percentage": null, "weight": null }
  ]
}
```

## 7. Reglas de validación destacadas

- `amountMinor > 0`.
- `splitType = EQUAL` → se ignora cualquier `amountMinor` por parte y se reparte el total.
- `splitType = EXACT` → `sum(shares[].amountMinor) == amountMinor`, cada parte `>= 0`.
- `splitType = PERCENTAGE` → `sum(shares[].percentage) == 100` (tolerancia 0) y luego se **resuelven** los importes con reparto de resto.
- `splitType = SHARES` → al menos una parte con `weight > 0`; importe = `amountMinor * weight / Σweight` (reparto de resto).
- Cada `userId` referenciado **debe ser miembro activo** del grupo.
- `currency` debe coincidir con la del grupo.

## 8. Generación del cliente

El contrato es la fuente de verdad: se generarán los DTOs de Android desde `openapi.yaml` (tarea de fase 9) para que un cambio incompatible rompa la compilación en lugar de fallar en runtime.
