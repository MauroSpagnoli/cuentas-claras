-- V1 · Usuarios
-- Nota de implementacion: el email se normaliza a minusculas en la aplicacion,
-- por eso la unicidad case-insensitive se garantiza sin la extension citext.

CREATE TABLE users (
    id               UUID         PRIMARY KEY,
    email            VARCHAR(255) NOT NULL,
    password_hash    VARCHAR(255) NOT NULL,
    display_name     VARCHAR(120) NOT NULL,
    default_currency VARCHAR(3)   NOT NULL DEFAULT 'EUR',
    locale           VARCHAR(10),
    created_at       TIMESTAMPTZ  NOT NULL DEFAULT now(),
    updated_at       TIMESTAMPTZ  NOT NULL DEFAULT now(),
    version          BIGINT       NOT NULL DEFAULT 0
);

CREATE UNIQUE INDEX ux_users_email ON users (email);
