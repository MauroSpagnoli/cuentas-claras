# 01 · Visión y alcance

## 1. Problema

Cuando un grupo de personas comparte gastos (viajes, piso, cenas, regalos), llevar las cuentas a mano es propenso a errores y genera fricción al saldar. Las apps existentes funcionan, pero el problema tiene partes interesantes de ingeniería: **exactitud del dinero**, **repartos flexibles**, **autorización por recurso** y **cálculo de una liquidación eficiente**.

## 2. Usuarios

- **Persona individual**: quiere saber cuánto debe y a quién, sin calculadora.
- **Grupo** (compañeros de piso, viaje, pareja): necesita un espacio compartido con historial y saldos.
- **Administrador del grupo**: gestiona miembros, roles y cierre de cuentas.

## 3. Casos de uso principales

| ID | Caso de uso | Actor |
|---|---|---|
| UC-01 | Registrarse e iniciar sesión | Usuario |
| UC-02 | Crear un grupo e invitar miembros | Usuario |
| UC-03 | Registrar un gasto con reparto (igual/exacto/% /partes) | Miembro |
| UC-04 | Editar o borrar un gasto | Creador / admin |
| UC-05 | Ver balances del grupo (quién debe a quién) | Miembro |
| UC-06 | Ver plan de liquidación sugerido | Miembro |
| UC-07 | Registrar un pago/liquidación real | Miembro |
| UC-08 | Consultar historial/actividad del grupo | Miembro |
| UC-09 | Registrarse y editar el perfil | Usuario |
| UC-10 | Gestionar miembros y roles | Owner / admin |

## 4. Alcance

### 4.1 En el MVP
- Auth completa (registro, login, refresh, logout) + perfil.
- Grupos, miembros, roles e invitaciones.
- Gastos con 4 tipos de reparto.
- Balances y plan de liquidación.
- Liquidaciones (pagos) reales.
- App Android con caché local y uso sin conexión **para lectura**; creación offline como *stretch goal*.

### 4.2 Fuera del MVP (futuro)
- Multi-divisa real con tipos de cambio.
- Adjuntos/recibos, OCR de tickets.
- Notificaciones push (FCM) y recordatorios.
- Exportación (CSV/PDF) y enlaces públicos.
- App iOS (posible KMP) / web.
- Suscripciones y facturación.

## 5. Requisitos funcionales (extracto)

- **RF-01**: El dinero se almacena y calcula con exactitud; nunca se usa coma flotante para importes.
- **RF-02**: En un reparto, la suma de las partes **debe** cuadrar con el total; si no, la petición falla con `422`.
- **RF-03**: Un grupo opera en **una** divisa (definida al crearlo).
- **RF-04**: Solo miembros activos del grupo pueden ver sus datos; los cambios de gastos los hacen el creador o un admin.
- **RF-05**: Los balances y el plan de liquidación se derivan siempre de gastos **y** liquidaciones registradas.
- **RF-06**: Borrar un gasto es *soft delete* (queda traza para el historial y la auditoría).

## 6. Requisitos no funcionales

| Área | Requisito |
|---|---|
| **Seguridad** | JWT de acceso corto + refresh rotativo; contraseñas con `BCrypt`; autorización por recurso; rate limiting en auth |
| **Rendimiento** | Listado de gastos paginado por cursor; respuestas p95 < 300 ms en endpoints de lectura |
| **Consistencia** | Invariante de suma de repartos validada en API **y** en base de datos; bloqueo optimista (`@Version`) |
| **Disponibilidad** | Backend stateless, escalable horizontalmente; health checks (`/actuator/health`) |
| **Mantenibilidad** | Arquitectura en capas, contrato OpenAPI versionado, ADRs, tests automatizados |
| **Portabilidad** | Backend y base de datos levantan con `docker-compose up` |
| **Observabilidad** | Logs estructurados con correlation-id, métricas de Actuator |
| **Accesibilidad** | Contraste, tamaños táctiles y `contentDescription` en Compose |

## 7. Criterios de éxito (definición de "terminado")

1. Un usuario nuevo puede registrarse, crear un grupo, registrar 3 gastos con repartos distintos y ver balances correctos.
2. El plan de liquidación propuesto salda todas las deudas del grupo.
3. `docker-compose up` levanta Postgres + API y `openapi.yaml` valida sin errores.
4. Existen tests: unitarios de servicios y del algoritmo, de integración con Testcontainers, y de UI en Android.
5. El backend y el cliente se construyen y testean en CI.
