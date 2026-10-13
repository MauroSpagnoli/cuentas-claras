# ADR-0005 · Algoritmo de liquidación heurístico

- Estado: Aceptada
- Fecha: 2026-10-13

## Contexto
Dado el vector de balances netos de un grupo (que suma 0), el producto sugiere un conjunto de transferencias que deja a todos en cero. Minimizar el **número** de transferencias es el objetivo, pero el problema exacto ("optimal debt settlement") es **NP-hard**.

## Decisión
Usar una **heurística voraz**: emparejar repetidamente al mayor deudor con el mayor acreedor por el mínimo de ambos. Producir a lo sumo `n − 1` transferencias.

Para grupos pequeños (`n ≤ 12`) se ofrece además el **óptimo exacto** vía subconjuntos que suman cero (PD sobre subconjuntos), y la respuesta de la API indica qué algoritmo se usó (`GREEDY` o `EXACT`).

## Consecuencias
- Cálculo rápido y determinista; fácil de testear con *golden tests*.
- La heurística **no siempre** da el mínimo global, pero el resultado es correcto (salda todo).
- El campo `algorithm` hace el comportamiento observable y permite mejorarlo sin cambiar el contrato.
- El cálculo es puro y cacheable; no depende de red ni de estado.

## Alternativas consideradas
- **Óptimo exacto siempre**: inviable para `n` grande (`O(2^n)`).
- **Cada deudor paga a un único acreedor "central"**: simple, pero produce más transferencias y peor UX.
- **No sugerir plan** (dejar que el usuario decida): pierde el valor diferencial del producto.
