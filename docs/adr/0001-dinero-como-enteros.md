# ADR-0001 · Dinero como enteros en unidades mínimas

- Estado: Aceptada
- Fecha: 2026-10-13

## Contexto
La app maneja importes monetarios y repartos con división (igual, porcentaje, partes). Usar `float`/`double` produce errores de redondeo inaceptables (p. ej. `0.1 + 0.2 != 0.3`), y los errores se acumulan al calcular balances.

## Decisión
Representar **todo** importe monetario como un entero de 64 bits en la **unidad mínima** de la divisa (céntimos), campo `amountMinor` (`BIGINT` en Postgres, `long` en Java/Kotlin). La divisa se guarda aparte (`currency`, ISO 4217). En Android existe un `value class Money(minor, currency)` con operaciones y formato.

## Consecuencias
- Aritmética exacta y determinista; la invariante `Σ partes = total` es verificable.
- Hay que centralizar el **formato/parseo** por divisa y locale (no todas tienen 2 decimales).
- Se necesita una regla explícita de **reparto del resto** cuando una división no es exacta.
- `BigDecimal` se evita en el dominio por ser más costoso y propenso a escalas inconsistentes; se reserva, si acaso, para la capa de entrada/salida.

## Alternativas consideradas
- **`double`/`float`**: descartado por redondeo.
- **`BigDecimal` en todo el dominio**: correcto pero pesado y con gestión de escala; el entero en unidades mínimas es más simple y seguro.
- **Guardar el importe como string**: complica la aritmética y las consultas SQL.
