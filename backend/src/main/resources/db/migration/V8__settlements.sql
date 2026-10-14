-- V8 · Pagos entre miembros (liquidaciones)
-- Cada fila registra un pago real: from_user_id le devuelve dinero a
-- to_user_id. El importe va en unidades minimas (centimos) y el borrado es
-- logico (deleted_at). El plan de liquidacion es solo una sugerencia; solo los
-- pagos persistidos alteran los balances reales.

CREATE TABLE settlements (
    id           UUID          PRIMARY KEY,
    group_id     UUID          NOT NULL REFERENCES "groups" (id) ON DELETE CASCADE,
    from_user_id UUID          NOT NULL REFERENCES users (id),
    to_user_id   UUID          NOT NULL REFERENCES users (id),
    amount_minor BIGINT        NOT NULL CHECK (amount_minor > 0),
    currency     VARCHAR(3)    NOT NULL,
    settled_on   DATE          NOT NULL,
    note         VARCHAR(500),
    created_by   UUID          NOT NULL REFERENCES users (id),
    created_at   TIMESTAMPTZ   NOT NULL DEFAULT now(),
    deleted_at   TIMESTAMPTZ,
    CONSTRAINT ck_settlements_distinct_users CHECK (from_user_id <> to_user_id)
);

-- Listado por grupo ordenado por fecha desc (con id como desempate del cursor).
CREATE INDEX ix_settlements_group_date ON settlements (group_id, settled_on DESC, id DESC);
CREATE INDEX ix_settlements_from_user ON settlements (from_user_id);
CREATE INDEX ix_settlements_to_user ON settlements (to_user_id);
