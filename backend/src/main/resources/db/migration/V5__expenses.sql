-- V5 · Gastos
-- Un gasto pertenece a un grupo, tiene un pagador y un tipo de reparto.
-- El importe se guarda en unidades minimas (centimos) para evitar errores de
-- coma flotante. El borrado es logico (deleted_at).

CREATE TABLE expenses (
    id              UUID          PRIMARY KEY,
    group_id        UUID          NOT NULL REFERENCES "groups" (id) ON DELETE CASCADE,
    description     VARCHAR(200)  NOT NULL,
    amount_minor    BIGINT        NOT NULL CHECK (amount_minor > 0),
    currency        VARCHAR(3)    NOT NULL,
    paid_by_user_id UUID          NOT NULL REFERENCES users (id),
    split_type      VARCHAR(16)   NOT NULL,
    category        VARCHAR(32),
    expense_date    DATE          NOT NULL,
    notes           VARCHAR(1000),
    created_by      UUID          NOT NULL REFERENCES users (id),
    created_at      TIMESTAMPTZ   NOT NULL DEFAULT now(),
    updated_at      TIMESTAMPTZ   NOT NULL DEFAULT now(),
    deleted_at      TIMESTAMPTZ,
    version         BIGINT        NOT NULL DEFAULT 0
);

-- Listado por grupo ordenado por fecha desc (con id como desempate del cursor).
CREATE INDEX ix_expenses_group_date ON expenses (group_id, expense_date DESC, id DESC);
CREATE INDEX ix_expenses_paid_by ON expenses (paid_by_user_id);
