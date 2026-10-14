-- V6 · Reparto de gastos
-- Cada fila es la parte de un gasto asignada a un usuario. La invariante
-- sum(share_minor) == expenses.amount_minor la garantiza SplitCalculator en el
-- dominio antes de persistir. Se conserva el parametro original del reparto
-- (weight o percentage) para poder reeditar el gasto.

CREATE TABLE expense_shares (
    id          UUID          PRIMARY KEY,
    expense_id  UUID          NOT NULL REFERENCES expenses (id) ON DELETE CASCADE,
    user_id     UUID          NOT NULL REFERENCES users (id),
    share_minor BIGINT        NOT NULL CHECK (share_minor >= 0),
    weight      NUMERIC(12,4),
    percentage  NUMERIC(7,4),
    created_at  TIMESTAMPTZ   NOT NULL DEFAULT now(),
    CONSTRAINT ux_expense_shares_expense_user UNIQUE (expense_id, user_id)
);

CREATE INDEX ix_expense_shares_user ON expense_shares (user_id);
