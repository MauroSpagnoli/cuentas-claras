-- V9 · Historial de actividad del grupo
-- Cada fila es un evento inmutable del grupo (UC-08): alta/edicion/borrado de
-- gastos, pagos registrados y altas/bajas de miembros. Se guarda un resumen
-- legible (con el nombre del actor) para que el feed se renderice sin joins.
-- El feed se pagina por cursor ordenado por occurred_at DESC, id DESC.

CREATE TABLE activity_events (
    id            UUID          PRIMARY KEY,
    group_id      UUID          NOT NULL REFERENCES "groups" (id) ON DELETE CASCADE,
    type          VARCHAR(32)   NOT NULL,
    actor_user_id UUID          NOT NULL REFERENCES users (id),
    summary       VARCHAR(300)  NOT NULL,
    amount_minor  BIGINT,
    occurred_at   TIMESTAMPTZ   NOT NULL DEFAULT now()
);

-- Feed por grupo ordenado por fecha desc (con id como desempate del cursor).
CREATE INDEX ix_activity_events_group_date ON activity_events (group_id, occurred_at DESC, id DESC);
CREATE INDEX ix_activity_events_actor ON activity_events (actor_user_id);
