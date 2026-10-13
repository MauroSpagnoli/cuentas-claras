-- V3 · Grupos
-- Nota: "groups" es palabra reservada en H2, por eso el identificador va citado
-- (en PostgreSQL tambien baja a minusculas, asi coincide con el mapeo JPA).

CREATE TABLE "groups" (
    id          UUID         PRIMARY KEY,
    name        VARCHAR(120) NOT NULL,
    description VARCHAR(500),
    currency    VARCHAR(3)   NOT NULL,
    created_by  UUID         NOT NULL REFERENCES users (id),
    invite_code VARCHAR(32)  NOT NULL,
    archived_at TIMESTAMPTZ,
    deleted_at  TIMESTAMPTZ,
    created_at  TIMESTAMPTZ  NOT NULL DEFAULT now(),
    updated_at  TIMESTAMPTZ  NOT NULL DEFAULT now(),
    version     BIGINT       NOT NULL DEFAULT 0
);

CREATE UNIQUE INDEX ux_groups_invite_code ON "groups" (invite_code);
CREATE INDEX ix_groups_created_by ON "groups" (created_by);
