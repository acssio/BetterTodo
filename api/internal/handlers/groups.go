package handlers

import (
	"net/http"

	"github.com/jackc/pgx/v5"
	"github.com/jackc/pgx/v5/pgxpool"

	"bettertodo/api/internal/auth"
	"bettertodo/api/internal/httpjson"
)

// GroupHandler gère les groupes de tâches d'une liste (les colonnes de la vue colonnes).
type GroupHandler struct {
	pool *pgxpool.Pool
}

// NewGroupHandler crée le handler des groupes.
func NewGroupHandler(pool *pgxpool.Pool) *GroupHandler {
	return &GroupHandler{pool: pool}
}

// group : un groupe de tâches tel que renvoyé au client.
type group struct {
	ID       string `json:"id"`
	ListID   string `json:"list_id"`
	Name     string `json:"name"`
	Position int    `json:"position"`
}

const groupSelect = `SELECT id, list_id, name, position FROM task_groups`

// Les groupes sont ordonnés dans leur liste ($1)
var groupOrdering = ordering{
	table:    "task_groups",
	column:   "position",
	siblings: `SELECT id FROM task_groups WHERE list_id = $1 ORDER BY position, created_at`,
}

func scanGroup(row pgx.Row) (group, error) {
	var g group
	err := row.Scan(&g.ID, &g.ListID, &g.Name, &g.Position)
	return g, err
}

type createGroupRequest struct {
	Name     string `json:"name"`
	Position *int   `json:"position"`
}

// Create : POST /api/v1/lists/{id}/groups
func (h *GroupHandler) Create(w http.ResponseWriter, r *http.Request) {
	listID, ok := pathID(w, r)
	if !ok {
		return
	}

	var req createGroupRequest
	if !httpjson.Read(w, r, &req) {
		return
	}
	if !requireOwned(w, r, h.pool, sqlOwnsList, listID) {
		return
	}

	name, ok := cleanText(req.Name, 1, maxNameLength)
	if !ok {
		badRequest(w, nameError("Le nom"))
		return
	}

	ctx := r.Context()
	var created group

	err := pgx.BeginFunc(ctx, h.pool, func(tx pgx.Tx) error {
		var id string
		err := tx.QueryRow(ctx,
			`INSERT INTO task_groups (list_id, name, position)
			 VALUES ($1, $2, (SELECT COALESCE(MAX(position) + 1, 0) FROM task_groups WHERE list_id = $1))
			 RETURNING id`, listID, name).Scan(&id)
		if err != nil {
			return err
		}

		if req.Position != nil {
			if err := groupOrdering.move(ctx, tx, []any{listID}, id, *req.Position); err != nil {
				return err
			}
		}

		created, err = scanGroup(tx.QueryRow(ctx, groupSelect+` WHERE id = $1`, id))
		return err
	})
	if err != nil {
		httpjson.ServerError(w, err)
		return
	}

	httpjson.Write(w, http.StatusCreated, created)
}

type updateGroupRequest struct {
	Name     Optional[string] `json:"name"`
	Position Optional[int]    `json:"position"`
}

// Update : PATCH /api/v1/groups/{id}
func (h *GroupHandler) Update(w http.ResponseWriter, r *http.Request) {
	id, ok := pathID(w, r)
	if !ok {
		return
	}

	var req updateGroupRequest
	if !httpjson.Read(w, r, &req) {
		return
	}
	if !requireOwned(w, r, h.pool, sqlOwnsGroup, id) {
		return
	}
	if req.Position.IsNull() {
		badRequest(w, "position ne peut pas être null")
		return
	}

	changes := newUpdate("task_groups")
	if req.Name.Set {
		name, ok := cleanText(deref(req.Name.Value), 1, maxNameLength)
		if !ok {
			badRequest(w, nameError("Le nom"))
			return
		}
		changes.set("name", name)
	}

	ctx := r.Context()
	err := pgx.BeginFunc(ctx, h.pool, func(tx pgx.Tx) error {
		if err := changes.exec(ctx, tx, id); err != nil {
			return err
		}
		if req.Position.Set {
			var listID string
			if err := tx.QueryRow(ctx, `SELECT list_id FROM task_groups WHERE id = $1`, id).Scan(&listID); err != nil {
				return err
			}
			return groupOrdering.move(ctx, tx, []any{listID}, id, *req.Position.Value)
		}
		return nil
	})
	if err != nil {
		httpjson.ServerError(w, err)
		return
	}

	updated, err := scanGroup(h.pool.QueryRow(ctx, groupSelect+` WHERE id = $1`, id))
	if err != nil {
		httpjson.ServerError(w, err)
		return
	}
	httpjson.Write(w, http.StatusOK, updated)
}

// Delete : DELETE /api/v1/groups/{id} — supprime aussi ses tâches.
func (h *GroupHandler) Delete(w http.ResponseWriter, r *http.Request) {
	id, ok := pathID(w, r)
	if !ok {
		return
	}
	if !requireOwned(w, r, h.pool, sqlOwnsGroup, id) {
		return
	}

	if _, err := h.pool.Exec(r.Context(), `DELETE FROM task_groups WHERE id = $1`, id); err != nil {
		httpjson.ServerError(w, err)
		return
	}
	w.WriteHeader(http.StatusNoContent)
}

// userOf retourne l'utilisateur connecté (raccourci).
func userOf(r *http.Request) string {
	return auth.UserID(r.Context())
}
