-- =============================================================================
-- Migration 007 : jetons d'accès personnels
--
-- Un jeton d'accès permet à un programme (le serveur MCP pour Claude, un script…)
-- d'utiliser l'API au nom de l'utilisateur, sans son mot de passe.
-- Créé et révoqué depuis les Paramètres de la page web.
--
-- Comme pour les sessions, la base ne garde que le HASH SHA-256 du jeton :
-- le jeton en clair n'est montré qu'une fois, à sa création.
-- Pas de date d'expiration : le jeton reste valable jusqu'à sa révocation.
-- =============================================================================

CREATE TABLE api_tokens (
    id           UUID        PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id      UUID        NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    name         TEXT        NOT NULL,          -- Ex. "Claude sur le MacBook"
    token_hash   BYTEA       NOT NULL UNIQUE,   -- SHA-256 du jeton donné à l'utilisateur
    created_at   TIMESTAMPTZ NOT NULL DEFAULT now(),
    last_used_at TIMESTAMPTZ                    -- null = jamais utilisé
);

CREATE INDEX ON api_tokens (user_id);
