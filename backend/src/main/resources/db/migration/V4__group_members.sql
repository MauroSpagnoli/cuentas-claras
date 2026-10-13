-- V4 · Miembros de grupo (rol + estado)
-- Un usuario se relaciona con un grupo una sola vez; al abandonarlo o ser
-- expulsado pasa a status LEFT (permite volver a unirse con el mismo registro).

CREATE TABLE group_members (
    id         UUID        PRIMARY KEY,
    group_id   UUID        NOT NULL REFERENCES "groups" (id) ON DELETE CASCADE,
    user_id    UUID        NOT NULL REFERENCES users (id) ON DELETE CASCADE,
    role       VARCHAR(16) NOT NULL,
    status     VARCHAR(16) NOT NULL,
    joined_at  TIMESTAMPTZ,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    CONSTRAINT ux_group_members_group_user UNIQUE (group_id, user_id)
);

CREATE INDEX ix_group_members_user ON group_members (user_id);
CREATE INDEX ix_group_members_group ON group_members (group_id);
