# Decisiones de arquitectura (ADRs)

Registro de decisiones relevantes, en formato ligero (contexto → decisión → consecuencias).
Una ADR por decisión, numerada y **nunca reescrita**: si cambia la decisión, se crea una nueva que reemplaza a la anterior.

| ID | Decisión | Estado |
|---|---|---|
| [0001](0001-dinero-como-enteros.md) | Dinero como enteros en unidades mínimas | Aceptada |
| [0002](0002-jwt-refresh-rotativo.md) | JWT de acceso corto + refresh rotativo | Aceptada |
| [0003](0003-room-source-of-truth.md) | Room como fuente de verdad (offline-first) | Aceptada |
| [0004](0004-paginacion-por-cursor.md) | Paginación por cursor | Aceptada |
| [0005](0005-algoritmo-liquidacion.md) | Algoritmo de liquidación heurístico | Aceptada |

Formato de plantilla:

```markdown
# ADR-NNNN · Título
- Estado: Propuesta | Aceptada | Reemplazada por ADR-XXXX
- Fecha: YYYY-MM-DD

## Contexto
## Decisión
## Consecuencias
## Alternativas consideradas
```
