-- =============================================================================
-- BetterTodo : données d'exemple (les listes et tâches de la maquette Figma)
--
-- Ce script n'est PAS lancé automatiquement. On le charge à la main en dev :
--   docker compose exec -T db sh -c 'psql -U "$POSTGRES_USER" -d "$POSTGRES_DB"' < db/exemple/exemple.sql
--
-- Compte de démo : demo@bettertodo.local / mot de passe : demo
-- =============================================================================

-- pgcrypto fournit crypt() pour hacher le mot de passe de démo en bcrypt.
-- (L'API en Go hachera elle-même les mots de passe à l'étape 2.)
CREATE EXTENSION IF NOT EXISTS pgcrypto;


-- -----------------------------------------------------------------------------
-- Fonction temporaire pour ajouter une tâche et ses tags en une ligne.
-- pg_temp = elle disparaît à la fin de la session psql.
-- -----------------------------------------------------------------------------
CREATE FUNCTION pg_temp.add_task(
    p_user     UUID,
    p_group    UUID,
    p_position INTEGER,
    p_title    TEXT,
    p_due      DATE,
    p_urgency  UUID,
    p_tags     TEXT[],
    p_notes    TEXT    DEFAULT '',
    p_done     BOOLEAN DEFAULT false
) RETURNS void AS $$
DECLARE
    v_task UUID;
    v_tag  TEXT;
BEGIN
    INSERT INTO tasks (group_id, position, title, due_date, urgency_id, notes, done, done_at)
    VALUES (p_group, p_position, p_title, p_due, p_urgency, p_notes, p_done,
            CASE WHEN p_done THEN now() END)
    RETURNING id INTO v_task;

    -- Crée le tag s'il n'existe pas encore, puis le relie à la tâche
    FOREACH v_tag IN ARRAY p_tags LOOP
        INSERT INTO tags (user_id, name) VALUES (p_user, v_tag) ON CONFLICT (user_id, name) DO NOTHING;
        INSERT INTO task_tags (task_id, tag_id)
        SELECT v_task, id FROM tags WHERE user_id = p_user AND name = v_tag;
    END LOOP;
END;
$$ LANGUAGE plpgsql;


DO $$
DECLARE
    u          UUID; -- Utilisateur de démo
    -- Niveaux d'urgence
    u_basse    UUID;
    u_moyenne  UUID;
    u_haute    UUID;
    -- Groupes de listes
    f_perso    UUID;
    f_travail  UUID;
    f_etudes   UUID;
    -- Listes
    l_perso    UUID;
    l_travail  UUID;
    -- Groupes de tâches
    g          UUID;
    today      DATE := current_date;
BEGIN
    -- Utilisateur ---------------------------------------------------------------
    -- Admin seulement si c'est le premier compte (installation vide), comme POST /auth/setup
    INSERT INTO users (email, display_name, password_hash, is_admin)
    VALUES ('demo@bettertodo.local', 'Démo', crypt('demo', gen_salt('bf', 10)),
            NOT EXISTS (SELECT 1 FROM users))
    RETURNING id INTO u;

    -- Niveaux d'urgence par défaut --------------------------------------------
    INSERT INTO urgency_levels (user_id, name, color, rank) VALUES (u, 'Basse',   '#3FA66B', 0) RETURNING id INTO u_basse;
    INSERT INTO urgency_levels (user_id, name, color, rank) VALUES (u, 'Moyenne', '#E8A33D', 1) RETURNING id INTO u_moyenne;
    INSERT INTO urgency_levels (user_id, name, color, rank) VALUES (u, 'Haute',   '#E5484D', 2) RETURNING id INTO u_haute;

    -- Groupes de listes -------------------------------------------------------
    INSERT INTO list_folders (user_id, name, position)            VALUES (u, 'Personnel', 0)       RETURNING id INTO f_perso;
    INSERT INTO list_folders (user_id, name, position)            VALUES (u, 'Travail',   1)       RETURNING id INTO f_travail;
    INSERT INTO list_folders (user_id, name, position, collapsed) VALUES (u, 'Études',    2, true) RETURNING id INTO f_etudes;

    -- Listes -------------------------------------------------------------------
    INSERT INTO lists (user_id, folder_id, name, color, icon, position) VALUES (u, f_perso, 'Perso',  '#E5484D', 'user',  0) RETURNING id INTO l_perso;
    INSERT INTO lists (user_id, folder_id, name, color, icon, position) VALUES (u, f_perso, 'Maison', '#3FA66B', 'house', 1);
    INSERT INTO lists (user_id, folder_id, name, color, icon, position) VALUES (u, f_perso, 'Santé',  '#D6409F', 'heart', 2);

    INSERT INTO lists (user_id, folder_id, name, color, icon, position) VALUES (u, f_travail, 'Travail', '#2F6FEB', 'briefcase', 0) RETURNING id INTO l_travail;
    INSERT INTO lists (user_id, folder_id, name, color, icon, position) VALUES (u, f_travail, 'Projets', '#E8A33D', 'lightbulb', 1);

    INSERT INTO lists (user_id, folder_id, name, color, icon, position) VALUES (u, f_etudes, 'Cours',    '#8E4EC6', 'graduation-cap', 0);
    INSERT INTO lists (user_id, folder_id, name, color, icon, position) VALUES (u, f_etudes, 'Lectures', '#0E9F9A', 'book-open',      1);

    -- Liste « Perso » ----------------------------------------------------------
    INSERT INTO task_groups (list_id, name, position) VALUES (l_perso, 'Courses', 0) RETURNING id INTO g;
    PERFORM pg_temp.add_task(u, g, 0, 'Acheter du pain',         today,     u_moyenne, ARRAY['courses']);
    PERFORM pg_temp.add_task(u, g, 1, 'Lait et œufs',            NULL,      u_basse,   ARRAY[]::TEXT[]);
    PERFORM pg_temp.add_task(u, g, 2, 'Cadeau anniversaire Léa', today + 2, u_haute,   ARRAY['cadeau'], 'Idée : un livre de cuisine.');

    INSERT INTO task_groups (list_id, name, position) VALUES (l_perso, 'Administratif', 1) RETURNING id INTO g;
    PERFORM pg_temp.add_task(u, g, 0, 'Payer la facture d''électricité', today + 5,  u_haute,   ARRAY['factures', 'maison'],
                             'Facture n° 2026-1142. À payer sur le site du fournisseur.');
    PERFORM pg_temp.add_task(u, g, 1, 'Renouveler la carte d''identité', today + 19, u_moyenne, ARRAY['papiers']);
    PERFORM pg_temp.add_task(u, g, 2, 'Résilier l''abonnement salle',    NULL,       u_basse,   ARRAY[]::TEXT[], '', true);

    INSERT INTO task_groups (list_id, name, position) VALUES (l_perso, 'Sport', 2) RETURNING id INTO g;
    PERFORM pg_temp.add_task(u, g, 0, 'Course 5 km',                  today + 1, u_basse,   ARRAY['santé']);
    PERFORM pg_temp.add_task(u, g, 1, 'Réserver le terrain de padel', today + 3, u_moyenne, ARRAY[]::TEXT[], 'Terrain 2, après 18 h.');

    INSERT INTO task_groups (list_id, name, position) VALUES (l_perso, 'Lectures', 3) RETURNING id INTO g;
    PERFORM pg_temp.add_task(u, g, 0, 'Finir « Dune »', NULL, u_basse, ARRAY['livres']);

    -- Liste « Travail » --------------------------------------------------------
    INSERT INTO task_groups (list_id, name, position) VALUES (l_travail, 'À faire', 0) RETURNING id INTO g;
    PERFORM pg_temp.add_task(u, g, 0, 'Préparer la démo client', today + 1, u_haute,   ARRAY['client'], 'Montrer la vue scindée.');
    PERFORM pg_temp.add_task(u, g, 1, 'Relire la PR #42',        today,     u_moyenne, ARRAY['dev']);

    INSERT INTO task_groups (list_id, name, position) VALUES (l_travail, 'En cours', 1) RETURNING id INTO g;
    PERFORM pg_temp.add_task(u, g, 0, 'Migration base de données', today + 7, u_haute, ARRAY['dev'], 'Passer à PostgreSQL 17.');
    PERFORM pg_temp.add_task(u, g, 1, 'Mettre à jour la doc API',  NULL,      u_basse, ARRAY['doc']);

    INSERT INTO task_groups (list_id, name, position) VALUES (l_travail, 'Terminé', 2) RETURNING id INTO g;
    PERFORM pg_temp.add_task(u, g, 0, 'Réunion d''équipe', NULL, u_basse, ARRAY[]::TEXT[], '', true);
END;
$$;
