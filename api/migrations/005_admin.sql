-- =============================================================================
-- Migration 005 : compte administrateur
--
--   users.is_admin : true = peut créer, supprimer et réinitialiser les comptes
--                    (console admin). Le premier compte créé est admin.
--
-- Sur une installation qui a déjà des comptes, le plus ancien devient admin.
-- =============================================================================

ALTER TABLE users
    ADD COLUMN is_admin BOOLEAN NOT NULL DEFAULT false;

UPDATE users SET is_admin = true
WHERE id = (SELECT id FROM users ORDER BY created_at, id LIMIT 1);
