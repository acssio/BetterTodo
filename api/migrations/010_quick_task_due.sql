-- =============================================================================
-- Migration 010 : échéance proposée par la Quick task (étape 7-4)
--
--   users.quick_task_due : 'none' (pas d'échéance), 'today' ou 'tomorrow'.
--   'today' par défaut : les comptes existants gardent le comportement d'avant.
-- =============================================================================

ALTER TABLE users
    ADD COLUMN quick_task_due TEXT NOT NULL DEFAULT 'today'
    CHECK (quick_task_due IN ('none', 'today', 'tomorrow'));
