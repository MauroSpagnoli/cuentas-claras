# ADR-0002 · JWT de acceso corto + refresh rotativo

- Estado: Aceptada
- Fecha: 2026-10-13

## Contexto
El backend es stateless y sirve a clientes móviles. Se necesita autenticación segura, con sesiones de larga duración en el dispositivo pero ventanas de exposición cortas si un token se filtra.

## Decisión
- **Access token**: JWT firmado con **RS256**, vida **15 minutos**, sin estado en servidor. Se valida firmando con la clave pública.
- **Refresh token**: valor **opaco** de 256 bits, vida **30 días**, almacenado **hasheado** en `refresh_tokens`. **Rota en cada uso**; el token anterior se marca `revoked_at` y enlaza a `replaced_by_id`.
- **Detección de reuso**: si llega un refresh ya revocado, se revoca **toda la cadena** del usuario (posible robo).
- En el cliente, tokens guardados con **almacenamiento cifrado** (Jetpack Security / DataStore cifrado).

## Consecuencias
- El servidor no guarda sesiones para el access token → escalado horizontal sencillo.
- El refresh sí requiere estado en BD (revocación), pero con bajo volumen.
- Es obligatorio manejar la rotación en el cliente (un único refresco a la vez) y persistir el token rotado de forma atómica.

## Alternativas consideradas
- **Sesiones en servidor (cookie/session)**: difícil para clientes móviles, requiere estado compartido (Redis) para escalar.
- **Solo JWT de larga duración**: imposible revocar y ventana de exposición amplia. Descartado.
- **Refresh como JWT**: revocarlo exige lista negra; el token opaco hasheado es más directo.
