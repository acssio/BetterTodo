-- =============================================================================
-- Migration 009 : heure facultative sur une tâche (étape 7-3)
--
--   due_time complète due_date ("14:30"). Une heure sans date n'a pas de sens :
--   la contrainte l'interdit. Les tâches existantes n'ont pas d'heure (NULL).
-- =============================================================================

ALTER TABLE tasks
    ADD COLUMN due_time TIME,                                                -- NULL = pas d'heure
    ADD CONSTRAINT tasks_due_time_needs_date CHECK (due_time IS NULL OR due_date IS NOT NULL);
