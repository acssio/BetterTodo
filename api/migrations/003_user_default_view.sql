-- =============================================================================
-- Migration 003 : vue par défaut des nouvelles listes, préférence du compte
--
-- Utilisée par POST /lists quand le client ne précise pas default_view,
-- et modifiable avec PATCH /me (écran Paramètres).
-- =============================================================================

ALTER TABLE users
    ADD COLUMN default_view TEXT NOT NULL DEFAULT 'columns'
    CHECK (default_view IN ('columns', 'sections'));
