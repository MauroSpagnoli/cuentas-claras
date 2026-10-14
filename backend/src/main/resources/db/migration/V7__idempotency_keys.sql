-- V7 · Claves de idempotencia
-- Permite repetir un POST con la misma Idempotency-Key sin duplicar el efecto:
-- se guarda el hash del cuerpo y la respuesta original para devolverla tal cual.
-- La columna "key" va citada porque KEY es palabra reservada en H2.

CREATE TABLE idempotency_keys (
    id              UUID          PRIMARY KEY,
    user_id         UUID          NOT NULL REFERENCES users (id) ON DELETE CASCADE,
    "key"           VARCHAR(128)  NOT NULL,
    endpoint        VARCHAR(255)  NOT NULL,
    request_hash    VARCHAR(64)   NOT NULL,
    response_status INTEGER,
    response_body   VARCHAR(20000),
    created_at      TIMESTAMPTZ   NOT NULL DEFAULT now(),
    CONSTRAINT ux_idempotency_keys_user_key UNIQUE (user_id, "key")
);
