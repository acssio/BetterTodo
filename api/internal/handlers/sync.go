package handlers

import (
	"context"
	"net/http"
	"time"

	"github.com/jackc/pgx/v5"
	"github.com/jackc/pgx/v5/pgxpool"

	"bettertodo/api/internal/auth"
	"bettertodo/api/internal/httpjson"
)

// -----------------------------------------------------------------------------
// Synchronisation de l'app Android « local d'abord » (étape 8-4)
//
// L'app crée ses éléments hors ligne, avec ses propres UUID. Quand elle retrouve
// le serveur, elle rejoue ses requêtes dans l'ordre (POST avec "id", PATCH, DELETE),
// puis demande ce qui a changé : GET /sync?since=<curseur>.
//
//   - Création avec "id" : si cet id existe déjà chez l'utilisateur, c'est un rejeu
//     (la réponse précédente s'est perdue) → on renvoie l'élément existant (200).
//     S'il existe chez un autre compte → 409.
//   - Suppression : notée dans sync_deletions, pour que les autres appareils l'apprennent.
// -----------------------------------------------------------------------------

// Types d'éléments notés dans sync_deletions (colonne entity)
const (
	entityFolder       = "folder"
	entityList         = "list"
	entityGroup        = "group"
	entityTask         = "task"
	entityUrgencyLevel = "urgency_level"
	entityTag          = "tag"
	entitySmartList    = "smart_list"
)

// syncOverlap : la synchronisation renvoie aussi ce qui a changé un peu AVANT le curseur.
// updated_at vaut l'heure du DÉBUT de la transaction qui a modifié la ligne : une transaction
// commencée avant le curseur mais validée après serait sinon manquée. Renvoyer deux fois
// une ligne n'a pas d'importance (le téléphone l'écrase avec la même valeur).
const syncOverlap = time.Minute

// checkClientID vérifie l'id facultatif envoyé par l'app à la création.
//
//	ok = false     → réponse déjà écrite (400 : pas un UUID ; 409 : id d'un autre compte)
//	replay = true  → l'élément existe déjà chez cet utilisateur : le handler renvoie l'existant
func checkClientID(w http.ResponseWriter, r *http.Request, q querier, table, sqlOwns string, id *string) (replay, ok bool) {
	if id == nil {
		return false, true
	}
	if !uuidPattern.MatchString(*id) {
		badRequest(w, "id doit être un UUID")
		return false, false
	}

	ctx := r.Context()
	var exists bool
	if err := q.QueryRow(ctx, `SELECT EXISTS (SELECT 1 FROM `+table+` WHERE id = $1)`, *id).Scan(&exists); err != nil {
		httpjson.ServerError(w, err)
		return false, false
	}
	if !exists {
		return false, true
	}

	mine, err := owns(ctx, q, sqlOwns, *id, auth.UserID(ctx))
	if err != nil {
		httpjson.ServerError(w, err)
		return false, false
	}
	if !mine {
		httpjson.Error(w, http.StatusConflict, "Cet id est déjà utilisé")
		return false, false
	}
	return true, true
}

// recordDeletion note la suppression de l'élément id (à appeler dans la même transaction que le DELETE).
func recordDeletion(ctx context.Context, q querier, userID, entity, id string) error {
	_, err := q.Exec(ctx, `INSERT INTO sync_deletions (user_id, entity, entity_id) VALUES ($1, $2, $3)`, userID, entity, id)
	return err
}

// deleteRecorded supprime l'élément id (sqlDelete, $1 = id) et note la suppression, dans une transaction.
// L'appartenance doit avoir été vérifiée avant (requireOwned).
func deleteRecorded(ctx context.Context, pool *pgxpool.Pool, sqlDelete, entity, id string) error {
	return pgx.BeginFunc(ctx, pool, func(tx pgx.Tx) error {
		if _, err := tx.Exec(ctx, sqlDelete, id); err != nil {
			return err
		}
		return recordDeletion(ctx, tx, auth.UserID(ctx), entity, id)
	})
}

// SyncHandler : GET /api/v1/sync
type SyncHandler struct {
	pool *pgxpool.Pool
}

// NewSyncHandler crée le handler de la synchronisation.
func NewSyncHandler(pool *pgxpool.Pool) *SyncHandler {
	return &SyncHandler{pool: pool}
}

// deletion : un élément supprimé depuis la dernière synchronisation.
type deletion struct {
	Type string `json:"type"` // folder, list, group, task, urgency_level, tag, smart_list
	ID   string `json:"id"`
}

// syncResponse : tout ce qui a changé depuis since (tout, à la première synchronisation).
type syncResponse struct {
	Cursor        string         `json:"cursor"` // À renvoyer dans ?since= la prochaine fois
	User          userResponse   `json:"user"`
	Folders       []folder       `json:"folders"`
	Lists         []list         `json:"lists"`
	Groups        []group        `json:"groups"`
	Tasks         []task         `json:"tasks"`
	UrgencyLevels []urgencyLevel `json:"urgency_levels"`
	Tags          []tag          `json:"tags"`
	SmartLists    []smartList    `json:"smart_lists"`
	Deleted       []deletion     `json:"deleted"`
}

// Get : GET /api/v1/sync?since=<curseur>
// Sans since : tout (première synchronisation). Avec : ce qui a changé ou disparu depuis.
func (h *SyncHandler) Get(w http.ResponseWriter, r *http.Request) {
	since := time.Time{} // Tout
	if value := r.URL.Query().Get("since"); value != "" {
		parsed, err := time.Parse(time.RFC3339Nano, value)
		if err != nil {
			badRequest(w, "since doit être le curseur renvoyé par la synchronisation précédente")
			return
		}
		since = parsed.Add(-syncOverlap)
	}

	ctx := r.Context()
	userID := auth.UserID(ctx)
	var result syncResponse

	// REPEATABLE READ : toutes les lectures voient la base au même instant
	err := pgx.BeginTxFunc(ctx, h.pool, pgx.TxOptions{IsoLevel: pgx.RepeatableRead, AccessMode: pgx.ReadOnly}, func(tx pgx.Tx) error {
		// Le curseur est pris AVANT les lectures : ce qui change pendant sera renvoyé la fois suivante
		var cursor time.Time
		if err := tx.QueryRow(ctx, `SELECT clock_timestamp()`).Scan(&cursor); err != nil {
			return err
		}
		result.Cursor = cursor.UTC().Format(time.RFC3339Nano)

		if err := tx.QueryRow(ctx, `SELECT `+userColumns+` FROM users WHERE id = $1`, userID).Scan(result.User.scanTargets()...); err != nil {
			return err
		}

		var err error
		if result.Folders, err = query(ctx, tx, scanFolder,
			folderSelect+` WHERE user_id = $1 AND updated_at > $2 ORDER BY position, created_at`, userID, since); err != nil {
			return err
		}
		if result.Lists, err = query(ctx, tx, scanList,
			listSelect+` WHERE l.user_id = $1 AND l.updated_at > $2 ORDER BY l.position, l.created_at`, userID, since); err != nil {
			return err
		}
		if result.Groups, err = query(ctx, tx, scanGroup,
			groupSelect+` WHERE list_id IN (SELECT id FROM lists WHERE user_id = $1) AND updated_at > $2
			              ORDER BY position, created_at`, userID, since); err != nil {
			return err
		}
		if result.Tasks, err = query(ctx, tx, scanTask,
			taskSelect+` WHERE g.list_id IN (SELECT id FROM lists WHERE user_id = $1) AND t.updated_at > $2`+taskGroupBy+`
			             ORDER BY t.position, t.created_at`, userID, since); err != nil {
			return err
		}
		if result.UrgencyLevels, err = query(ctx, tx, scanUrgency,
			urgencySelect+` WHERE user_id = $1 AND updated_at > $2 ORDER BY rank, created_at`, userID, since); err != nil {
			return err
		}
		if result.Tags, err = query(ctx, tx, scanTag,
			tagSelect+` WHERE t.user_id = $1 AND t.updated_at > $2 ORDER BY t.name`, userID, since); err != nil {
			return err
		}
		if result.SmartLists, err = query(ctx, tx, scanSmartList,
			smartListSelect+` WHERE s.user_id = $1 AND s.updated_at > $2 ORDER BY s.position, s.created_at`, userID, since); err != nil {
			return err
		}
		result.Deleted, err = query(ctx, tx, func(row pgx.Row) (deletion, error) {
			var d deletion
			err := row.Scan(&d.Type, &d.ID)
			return d, err
		}, `SELECT entity, entity_id::text FROM sync_deletions WHERE user_id = $1 AND deleted_at > $2 ORDER BY deleted_at`, userID, since)
		return err
	})
	if err != nil {
		httpjson.ServerError(w, err)
		return
	}

	httpjson.Write(w, http.StatusOK, result)
}

// query exécute sql et lit toutes les lignes avec scan (liste vide s'il n'y en a pas).
func query[T any](ctx context.Context, q querier, scan func(pgx.Row) (T, error), sql string, args ...any) ([]T, error) {
	rows, err := q.Query(ctx, sql, args...)
	if err != nil {
		return nil, err
	}
	return collect(rows, scan)
}
