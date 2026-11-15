-- =============================================================================
-- Migration 006 : changement de mot de passe obligatoire
--
--   users.must_change_password : true = à la prochaine connexion, la page web
--   demande de choisir un nouveau mot de passe avant d'ouvrir les listes.
--   Mis à true quand l'admin crée le compte ou change son mot de passe ;
--   remis à false quand la personne choisit le sien (POST /me/password).
--
-- Les comptes existants ne sont pas concernés (false).
-- =============================================================================

ALTER TABLE users
    ADD COLUMN must_change_password BOOLEAN NOT NULL DEFAULT false;
