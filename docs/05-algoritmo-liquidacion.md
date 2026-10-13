# 05 · Algoritmo de liquidación

Dos problemas distintos, resueltos por separado:

1. **Reparto de un gasto** → dividir un importe entre varios participantes sin perder céntimos.
2. **Liquidación del grupo** → con los balances netos, proponer el **mínimo de transferencias** que salda las deudas.

---

## 1. Reparto de un gasto (`SplitInstruction` → `ExpenseShare[]`)

Entrada: `amountMinor`, `splitType` y la lista de participantes.

### 1.1 Igual (`EQUAL`)
```
n = nº participantes
base = amountMinor / n            (división entera)
resto = amountMinor % n
cada parte = base + (1 si su índice < resto)
```
> El resto se reparte de forma **determinista** (p. ej. orden por `userId`) para que el resultado sea reproducible.

### 1.2 Exacto (`EXACT`)
Se validan las partes: cada `>= 0` y `Σ = amountMinor`. Si no cuadra → `422 SPLIT_MISMATCH`.

### 1.3 Porcentaje (`PERCENTAGE`)
```
Σ percentage = 100            (si no → 422 PERCENTAGE_SUM)
cada parte (sin redondear) = amountMinor * percentage / 100
se aplica floor; el céntimo sobrante se reparte como en EQUAL
```

### 1.4 Por partes (`SHARES`)
```
cada parte = floor(amountMinor * weight_i / Σweight)
+ reparto de resto como en EQUAL
```

**Invariante garantizada en todos los casos:** `Σ share_minor = amountMinor`.

---

## 2. Balance neto por miembro

Para un grupo, dado el conjunto de gastos activos y liquidaciones:

```
para cada miembro u:
    pagado(u)  = Σ amount_minor de gastos donde paid_by = u
    debe(u)    = Σ share_minor de las partes de u
    pagosNetos(u) = Σ liquidaciones donde from = u   (dinero que u entregó)
    recibido(u)   = Σ liquidaciones donde to   = u

    neto(u) = pagado(u) − debe(u) + pagosNetos(u) − recibido(u)
```

Interpretación:
- `neto(u) > 0` → **acreedor**: le deben dinero.
- `neto(u) < 0` → **deudor**: debe dinero.
- `Σ neto(u) = 0` para todo grupo (prueba de sanidad).

### Balances por pares ("quién debe a quién")
Para UX, además del neto, se muestra la matriz de deudas mínimas entre pares (quién paga a quién directamente), calculada con el mismo criterio de "minimizar transferencias".

---

## 3. Plan de liquidación (minimizar transferencias)

**Problema:** dado un vector de netos que suma 0, encontrar el conjunto de transferencias que deja a todos en 0 usando **el mínimo número** de movimientos.

- La versión **exacta** (mínimo global) es **NP-hard** (equivale a partición/cobertura óptima, problema del "optimal debt settlement").
- El tamaño real de un grupo es pequeño (decenas de personas), así que se puede usar fuerza bruta/subset-sum exacta **hasta un umbral** (p. ej. `n ≤ 12`) y una **heurística** por encima.

### 3.1 Heurística voraz (greedy) — base del MVP

```
entradas: netos[1..n] que suman 0
transferencias = []

while existan netos != 0:
    deudor  = miembro con el neto más negativo   (el que más debe)
    acreedor = miembro con el neto más positivo  (al que más le deben)
    monto = min(|deudor|, acreedor)
    registrar transferencia: deudor → acreedor por monto
    netos[deudor]  += monto
    netos[acreedor] -= monto
```

- Complejidad: `O(n log n)` por iteración de ordenamiento (o `O(n²)` con búsquedas), a lo sumo `n − 1` transferencias.
- **Garantía:** salda todas las deudas, pero **no siempre** es el mínimo global.

### 3.2 Mejora opcional: exacto para grupos pequeños
Para `n ≤ 12` (ajustable), resolver el mínimo exacto con programación dinámica sobre subconjuntos que suman 0:
- Dividir el conjunto en el mayor número de subconjuntos con suma 0 → `nº mínimo de transferencias = n − nº subconjuntos`.
- Complejidad `O(2^n)`: aceptable para `n ≤ 12`, inviable a partir de ~20.

### 3.3 Ejemplo

Netos: `Ana +30, Luis −20, Marta −10`. Liquidación greedy:

| # | De | A | Importe |
|---|---|---|---|
| 1 | Luis | Ana | 20 |
| 2 | Marta | Ana | 10 |

Resultado: 2 transferencias y todos a cero.

Otro caso: `A +5, B +5, C −10` → 1 sola transferencia: `C → A 5` y `C → B 5` (o una única si se consolidara, pero con dos acreedores distintos son 2). El algoritmo evita movimientos innecesarios entre acreedores y deudores.

---

## 4. Idempotencia y consistencia

- El cálculo es **puro** (sin estado): mismo grupo → mismo plan. Esto habilita cachear el resultado y testearlo con *golden tests*.
- El plan es una **sugerencia**; solo las `settlements` persistidas alteran los balances reales.
- Toda escritura usa **bloqueo optimista** (`version`) para evitar que dos ediciones concurrentes corrompan la invariante de reparto.

---

## 5. Casos de prueba (golden)

| Caso | Entrada | Esperado |
|---|---|---|
| Equilibrio | `[0,0,0]` | sin transferencias |
| Un deudor / varios acreedores | `[+5,+5,-10]` | 2 transferencias |
| Varios deudores / un acreedor | `[-20,-10,+30]` | 2 transferencias |
| Reparto con resto | `100 / 3` | `34 + 33 + 33` |
| Porcentajes | `100 * 33.33% ×3` | suma exacta = 100 |
| Sanidad | cualquier grupo | `Σ netos = 0` |
