-- =============================================================================
-- Migration 008 : les tâches faites sont masquées par défaut
--
--   Une nouvelle liste est créée avec show_done = false. Les listes existantes
--   gardent leur réglage (on ne change que la valeur par défaut).
-- =============================================================================

ALTER TABLE lists
    ALTER COLUMN show_done SET DEFAULT false;
