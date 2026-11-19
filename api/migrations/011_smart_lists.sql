-- =============================================================================
-- Migration 011 : listes intelligentes personnalisées (étape 7-5)
--
--   smart_lists : une vue sur les tâches à faire de TOUTES les listes, filtrées
--     - par tags (kind = 'tags') : au moins un des tags (tag_match = 'any')
--       ou tous (tag_match = 'all') ;
--     - par urgence (kind = 'urgency') : les niveaux choisis.
--   tag_ids / urgency_ids sont de simples tableaux d'UUID : un tag ou un niveau
--   supprimé y reste, il est juste ignoré à la lecture (jointure).
--
--   users.show_today / show_scheduled : afficher ou masquer « Aujourd'hui » et
--   « Planifié » dans la barre latérale et sur l'accueil mobile.
-- =============================================================================

CREATE TABLE smart_lists (
    id          UUID        PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id     UUID        NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    name        TEXT        NOT NULL,
    color       TEXT        NOT NULL CHECK (color ~ '^#[0-9A-Fa-f]{6}$'),
    icon        TEXT        NOT NULL,
    kind        TEXT        NOT NULL CHECK (kind IN ('tags', 'urgency')),
    tag_match   TEXT        NOT NULL DEFAULT 'any' CHECK (tag_match IN ('any', 'all')),
    tag_ids     UUID[]      NOT NULL DEFAULT '{}',
    urgency_ids UUID[]      NOT NULL DEFAULT '{}',
    visible     BOOLEAN     NOT NULL DEFAULT true,  -- Affichée dans la barre latérale / l'accueil
    position    INTEGER     NOT NULL DEFAULT 0,
    created_at  TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at  TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE INDEX ON smart_lists (user_id);

CREATE TRIGGER smart_lists_updated_at BEFORE UPDATE ON smart_lists FOR EACH ROW EXECUTE FUNCTION set_updated_at();

ALTER TABLE users
    ADD COLUMN show_today     BOOLEAN NOT NULL DEFAULT true,
    ADD COLUMN show_scheduled BOOLEAN NOT NULL DEFAULT true;
