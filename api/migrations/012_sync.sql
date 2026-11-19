-- =============================================================================
-- Migration 012 : synchronisation de l'app Android « local d'abord » (étape 8-4)
--
--   sync_deletions : une ligne par suppression (groupe de listes, liste, groupe,
--   tâche, niveau d'urgence, tag, liste intelligente). Un téléphone qui se
--   synchronise (GET /sync?since=…) apprend ainsi ce qui a disparu depuis sa
--   dernière synchronisation. Seul l'élément supprimé est noté : ce qui part avec
--   lui en cascade (les tâches d'une liste…) part aussi en cascade sur le téléphone.
--   Les lignes sont gardées : elles sont petites (pas de nettoyage pour l'instant).
-- =============================================================================

CREATE TABLE sync_deletions (
    user_id    UUID        NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    entity     TEXT        NOT NULL CHECK (entity IN ('folder', 'list', 'group', 'task', 'urgency_level', 'tag', 'smart_list')),
    entity_id  UUID        NOT NULL,
    deleted_at TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE INDEX sync_deletions_user_time ON sync_deletions (user_id, deleted_at);
