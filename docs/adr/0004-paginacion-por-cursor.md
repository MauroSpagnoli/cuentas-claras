# ADR-0004 · Paginación por cursor

- Estado: Aceptada
- Fecha: 2026-10-13

## Contexto
Los grupos acumulan muchos gastos, liquidaciones y eventos de actividad. El cliente necesita listar de forma incremental y estable. La paginación clásica con `OFFSET` degrada su rendimiento en páginas profundas y puede **duplicar u omitir** ítems si se insertan registros entre dos peticiones.

## Decisión
Paginar las colecciones grandes por **cursor opaco**. El cursor codifica la **clave de ordenación** del último ítem (p. ej. base64 de `expenseDate + id`). La consulta usa `WHERE (expense_date, id) < (:date, :id) ORDER BY expense_date DESC, id DESC LIMIT :n`.

Respuesta estándar:
```json
{ "items": [ ... ], "nextCursor": "...", "hasMore": true }
```

## Consecuencias
- Rendimiento estable independientemente de la profundidad (usa índices, sin `OFFSET` creciente).
- Inserción/borrado no provoca saltos ni duplicados entre páginas.
- No se puede "saltar a la página N"; la UX es de scroll infinito (aceptable y esperable en móvil).
- El cliente trata el cursor como una cadena opaca; el formato interno puede cambiar sin romper el contrato.

## Alternativas consideradas
- **`OFFSET`/`LIMIT`**: simple pero con degradación y riesgo de duplicados. Descartado para colecciones grandes.
- **Keyset sin cursor opaco** (exponer `(fecha, id)` en claro): filtra detalles de ordenación y acopla el cliente. Se prefiere el cursor opaco.
