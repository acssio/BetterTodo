package handlers

import (
	"net/http"

	"github.com/jackc/pgx/v5"
	"github.com/jackc/pgx/v5/pgxpool"

	"bettertodo/api/internal/auth"
	"bettertodo/api/internal/httpjson"
)

// FolderHandler gère les groupes de listes (« Personnel », « Travail »… dans la barre latérale).
type FolderHandler struct {
	pool *pgxpool.Pool
}

// NewFolderHandler crée le handler des groupes de listes.
func NewFolderHandler(pool *pgxpool.Pool) *FolderHandler {
	return &FolderHandler{pool: pool}
}

// folder : un groupe de listes tel que renvoyé au client.
type folder struct {
	ID        string `json:"id"`
	Name      string `json:"name"`
	Position  int    `json:"position"`
	Collapsed bool   `json:"collapsed"`
}

const folderSelect = `SELECT id, name, position, collapsed FROM list_folders`

// Les groupes de listes sont ordonnés entre eux, par utilisateur ($1)
var folderOrdering = ordering{
	table:    "list_folders",
	column:   "position",
	siblings: `SELECT id FROM list_folders WHERE user_id = $1 ORDER BY position, created_at`,
}

func scanFolder(row pgx.Row) (folder, error) {
	var f folder
	err := row.Scan(&f.ID, &f.Name, &f.Position, &f.Collapsed)
	return f, err
}

// List : GET /api/v1/folders
func (h *FolderHandler) List(w http.ResponseWriter, r *http.Request) {
	rows, err := h.pool.Query(r.Context(),
		folderSelect+` WHERE user_id = $1 ORDER BY position, created_at`, auth.UserID(r.Context()))
	if err != nil {
		httpjson.ServerError(w, err)
		return
	}

	folders, err := collect(rows, scanFolder)
	if err != nil {
		httpjson.ServerError(w, err)
		return
	}
	httpjson.Write(w, http.StatusOK, folders)
}

type createFolderRequest struct {
	Name     string `json:"name"`
	Position *int   `json:"position"` // Facultatif : à la fin par défaut
}

// Create : POST /api/v1/folders
func (h *FolderHandler) Create(w http.ResponseWriter, r *http.Request) {
	var req createFolderRequest
	if !httpjson.Read(w, r, &req) {
		return
	}

	name, ok := cleanText(req.Name, 1, maxNameLength)
	if !ok {
		badRequest(w, nameError("Le nom"))
		return
	}

	ctx := r.Context()
	userID := auth.UserID(ctx)
	var created folder

	err := pgx.BeginFunc(ctx, h.pool, func(tx pgx.Tx) error {
		var id string
		err := tx.QueryRow(ctx,
			`INSERT INTO list_folders (user_id, name, position)
			 VALUES ($1, $2, (SELECT COALESCE(MAX(position) + 1, 0) FROM list_folders WHERE user_id = $1))
			 RETURNING id`, userID, name).Scan(&id)
		if err != nil {
			return err
		}

		if req.Position != nil {
			if err := folderOrdering.move(ctx, tx, []any{userID}, id, *req.Position); err != nil {
				return err
			}
		}

		created, err = scanFolder(tx.QueryRow(ctx, folderSelect+` WHERE id = $1`, id))
		return err
	})
	if err != nil {
		httpjson.ServerError(w, err)
		return
	}

	httpjson.Write(w, http.StatusCreated, created)
}

type updateFolderRequest struct {
	Name      Optional[string] `json:"name"`
	Position  Optional[int]    `json:"position"`
	Collapsed Optional[bool]   `json:"collapsed"`
}

// Update : PATCH /api/v1/folders/{id}
func (h *FolderHandler) Update(w http.ResponseWriter, r *http.Request) {
	id, ok := pathID(w, r)
	if !ok {
		return
	}

	var req updateFolderRequest
	if !httpjson.Read(w, r, &req) {
		return
	}
	if !requireOwned(w, r, h.pool, sqlOwnsFolder, id) {
		return
	}

	// Validation et construction de l'UPDATE
	changes := newUpdate("list_folders")
	if req.Name.Set {
		name, ok := cleanText(deref(req.Name.Value), 1, maxNameLength)
		if !ok {
			badRequest(w, nameError("Le nom"))
			return
		}
		changes.set("name", name)
	}
	if req.Collapsed.Set {
		if req.Collapsed.IsNull() {
			badRequest(w, "collapsed ne peut pas être null")
			return
		}
		changes.set("collapsed", *req.Collapsed.Value)
	}
	if req.Position.IsNull() {
		badRequest(w, "position ne peut pas être null")
		return
	}

	ctx := r.Context()
	err := pgx.BeginFunc(ctx, h.pool, func(tx pgx.Tx) error {
		if err := changes.exec(ctx, tx, id); err != nil {
			return err
		}
		if req.Position.Set {
			return folderOrdering.move(ctx, tx, []any{auth.UserID(ctx)}, id, *req.Position.Value)
		}
		return nil
	})
	if err != nil {
		httpjson.ServerError(w, err)
		return
	}

	updated, err := scanFolder(h.pool.QueryRow(ctx, folderSelect+` WHERE id = $1`, id))
	if err != nil {
		httpjson.ServerError(w, err)
		return
	}
	httpjson.Write(w, http.StatusOK, updated)
}

// Delete : DELETE /api/v1/folders/{id}
// Les listes du groupe ne sont pas supprimées : elles passent « hors groupe » (folder_id = NULL).
func (h *FolderHandler) Delete(w http.ResponseWriter, r *http.Request) {
	id, ok := pathID(w, r)
	if !ok {
		return
	}

	result, err := h.pool.Exec(r.Context(),
		`DELETE FROM list_folders WHERE id = $1 AND user_id = $2`, id, auth.UserID(r.Context()))
	if err != nil {
		httpjson.ServerError(w, err)
		return
	}
	if result.RowsAffected() == 0 {
		notFound(w)
		return
	}
	w.WriteHeader(http.StatusNoContent)
}

// deref retourne la valeur pointée, ou la valeur zéro du type si le pointeur est nil.
func deref[T any](p *T) T {
	var zero T
	if p == nil {
		return zero
	}
	return *p
}
