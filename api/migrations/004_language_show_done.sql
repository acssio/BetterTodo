-- =============================================================================
-- Migration 004 : langue du compte, et affichage des tâches faites par liste
--
--   users.language  : 'fr' ou 'en'. Sert d'abord à nommer le groupe créé avec
--                     chaque nouvelle liste (« Tâches » / « Tasks ») ; servira
--                     ensuite à la traduction de l'interface.
--   lists.show_done : false = les tâches faites sont masquées dans cette liste.
-- =============================================================================

ALTER TABLE users
    ADD COLUMN language TEXT NOT NULL DEFAULT 'fr'
    CHECK (language IN ('fr', 'en'));

ALTER TABLE lists
    ADD COLUMN show_done BOOLEAN NOT NULL DEFAULT true;
