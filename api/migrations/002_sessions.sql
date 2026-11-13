-- =============================================================================
-- Migration 002 : sessions de connexion
--
-- Après une connexion réussie, le serveur donne au client un jeton aléatoire.
-- La base ne garde que le HASH SHA-256 du jeton : une fuite de la base ne
-- permet pas de se faire passer pour un utilisateur.
-- =============================================================================

CREATE TABLE sessions (
    id         UUID        PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id    UUID        NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    token_hash BYTEA       NOT NULL UNIQUE,   -- SHA-256 du jeton donné au client
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    expires_at TIMESTAMPTZ NOT NULL           -- Après cette date, le jeton est refusé
);

CREATE INDEX ON sessions (user_id);
