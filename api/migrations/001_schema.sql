-- =============================================================================
-- BetterTodo : schéma de la base de données
--
-- Hiérarchie : utilisateur → groupe de listes → liste → groupe → tâche
--
-- Migration 001 : appliquée une seule fois par le serveur API au démarrage
-- (voir internal/database/migrator.go et docs/etapes/02a-api-fondations.md).
-- Ne jamais modifier une migration déjà appliquée : en ajouter une nouvelle.
--
-- Conventions :
--   - id en UUID, générés par gen_random_uuid() (intégré à PostgreSQL 13+)
--   - position : ordre d'affichage (0, 1, 2…), pour le glisser-déposer
--   - created_at / updated_at sur chaque table (updated_at mis à jour par trigger)
-- =============================================================================


-- -----------------------------------------------------------------------------
-- Trigger commun : met updated_at à l'heure actuelle à chaque UPDATE
-- -----------------------------------------------------------------------------
CREATE FUNCTION set_updated_at() RETURNS trigger AS $$
BEGIN
    NEW.updated_at = now();
    RETURN NEW;
END;
$$ LANGUAGE plpgsql;


-- -----------------------------------------------------------------------------
-- Utilisateurs
-- -----------------------------------------------------------------------------
CREATE TABLE users (
    id            UUID        PRIMARY KEY DEFAULT gen_random_uuid(),
    email         TEXT        NOT NULL UNIQUE CHECK (email = lower(email)), -- Toujours en minuscules
    display_name  TEXT        NOT NULL,
    password_hash TEXT        NOT NULL,                                     -- Jamais le mot de passe en clair
    created_at    TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at    TIMESTAMPTZ NOT NULL DEFAULT now()
);


-- -----------------------------------------------------------------------------
-- Groupes de listes (« Personnel », « Travail »… dans la barre latérale)
-- -----------------------------------------------------------------------------
CREATE TABLE list_folders (
    id         UUID        PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id    UUID        NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    name       TEXT        NOT NULL,
    position   INTEGER     NOT NULL DEFAULT 0,
    collapsed  BOOLEAN     NOT NULL DEFAULT false, -- Replié dans la barre latérale
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT now()
);


-- -----------------------------------------------------------------------------
-- Listes
-- -----------------------------------------------------------------------------
CREATE TABLE lists (
    id           UUID        PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id      UUID        NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    folder_id    UUID        REFERENCES list_folders(id) ON DELETE SET NULL, -- NULL = hors groupe
    name         TEXT        NOT NULL,
    color        TEXT        NOT NULL DEFAULT '#6B6B6B' CHECK (color ~ '^#[0-9A-Fa-f]{6}$'),
    icon         TEXT        NOT NULL DEFAULT 'list',     -- Nom d'une icône Lucide (ex. 'briefcase')
    default_view TEXT        NOT NULL DEFAULT 'columns' CHECK (default_view IN ('columns', 'sections')),
    position     INTEGER     NOT NULL DEFAULT 0,          -- Ordre dans son groupe de listes
    created_at   TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at   TIMESTAMPTZ NOT NULL DEFAULT now()
);


-- -----------------------------------------------------------------------------
-- Groupes de tâches dans une liste (« Courses », « Administratif »…)
-- -----------------------------------------------------------------------------
CREATE TABLE task_groups (
    id         UUID        PRIMARY KEY DEFAULT gen_random_uuid(),
    list_id    UUID        NOT NULL REFERENCES lists(id) ON DELETE CASCADE,
    name       TEXT        NOT NULL,
    position   INTEGER     NOT NULL DEFAULT 0,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT now()
);


-- -----------------------------------------------------------------------------
-- Niveaux d'urgence (3 par défaut, modifiables dans les paramètres)
-- rank : 0 = le moins urgent
-- -----------------------------------------------------------------------------
CREATE TABLE urgency_levels (
    id         UUID        PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id    UUID        NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    name       TEXT        NOT NULL,
    color      TEXT        NOT NULL CHECK (color ~ '^#[0-9A-Fa-f]{6}$'),
    rank       INTEGER     NOT NULL DEFAULT 0,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT now()
);


-- -----------------------------------------------------------------------------
-- Tâches
-- -----------------------------------------------------------------------------
CREATE TABLE tasks (
    id         UUID        PRIMARY KEY DEFAULT gen_random_uuid(),
    group_id   UUID        NOT NULL REFERENCES task_groups(id) ON DELETE CASCADE,
    title      TEXT        NOT NULL,
    notes      TEXT        NOT NULL DEFAULT '',
    due_date   DATE,                                                        -- NULL = pas d'échéance
    urgency_id UUID        REFERENCES urgency_levels(id) ON DELETE SET NULL, -- NULL = pas d'urgence
    done       BOOLEAN     NOT NULL DEFAULT false,
    done_at    TIMESTAMPTZ,                                                 -- Quand la tâche a été cochée
    position   INTEGER     NOT NULL DEFAULT 0,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT now()
);


-- -----------------------------------------------------------------------------
-- Tags (partagés par toutes les listes d'un utilisateur)
-- -----------------------------------------------------------------------------
CREATE TABLE tags (
    id         UUID        PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id    UUID        NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    name       TEXT        NOT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    UNIQUE (user_id, name) -- Pas deux fois le même tag pour un utilisateur
);

-- Liaison tâche ↔ tag (une tâche a plusieurs tags, un tag est sur plusieurs tâches)
CREATE TABLE task_tags (
    task_id UUID NOT NULL REFERENCES tasks(id) ON DELETE CASCADE,
    tag_id  UUID NOT NULL REFERENCES tags(id)  ON DELETE CASCADE,
    PRIMARY KEY (task_id, tag_id)
);


-- -----------------------------------------------------------------------------
-- Index sur les clés étrangères (PostgreSQL ne les crée pas tout seul)
-- -----------------------------------------------------------------------------
CREATE INDEX ON list_folders   (user_id);
CREATE INDEX ON lists          (user_id);
CREATE INDEX ON lists          (folder_id);
CREATE INDEX ON task_groups    (list_id);
CREATE INDEX ON urgency_levels (user_id);
CREATE INDEX ON tasks          (group_id);
CREATE INDEX ON tasks          (urgency_id);
CREATE INDEX ON tags           (user_id);
CREATE INDEX ON task_tags      (tag_id);


-- -----------------------------------------------------------------------------
-- Triggers updated_at
-- -----------------------------------------------------------------------------
CREATE TRIGGER users_updated_at          BEFORE UPDATE ON users          FOR EACH ROW EXECUTE FUNCTION set_updated_at();
CREATE TRIGGER list_folders_updated_at   BEFORE UPDATE ON list_folders   FOR EACH ROW EXECUTE FUNCTION set_updated_at();
CREATE TRIGGER lists_updated_at          BEFORE UPDATE ON lists          FOR EACH ROW EXECUTE FUNCTION set_updated_at();
CREATE TRIGGER task_groups_updated_at    BEFORE UPDATE ON task_groups    FOR EACH ROW EXECUTE FUNCTION set_updated_at();
CREATE TRIGGER urgency_levels_updated_at BEFORE UPDATE ON urgency_levels FOR EACH ROW EXECUTE FUNCTION set_updated_at();
CREATE TRIGGER tasks_updated_at          BEFORE UPDATE ON tasks          FOR EACH ROW EXECUTE FUNCTION set_updated_at();
CREATE TRIGGER tags_updated_at           BEFORE UPDATE ON tags           FOR EACH ROW EXECUTE FUNCTION set_updated_at();
