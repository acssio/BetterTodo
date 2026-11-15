package handlers

import (
	"net/http"
	"strings"

	"github.com/jackc/pgx/v5"
	"github.com/jackc/pgx/v5/pgxpool"

	"bettertodo/api/internal/httpjson"
)

// TagHandler gère les tags de l'utilisateur (écran Paramètres).
// Pour mettre des tags sur une tâche, voir le champ "tags" de TaskHandler.
type TagHandler struct {
	pool *pgxpool.Pool
}

// NewTagHandler crée le handler des tags.
func NewTagHandler(pool *pgxpool.Pool) *TagHandler {
	return &TagHandler{pool: pool}
}

// tag : un tag et le nombre de tâches qui l'utilisent.
type tag struct {
	ID        string `json:"id"`
	Name      string `json:"name"`
	TaskCount int    `json:"task_count"`
}

const tagSelect = `SELECT t.id, t.name, (SELECT count(*) FROM task_tags tt WHERE tt.tag_id = t.id) FROM tags t`

func scanTag(row pgx.Row) (tag, error) {
	var t tag
	err := row.Scan(&t.ID, &t.Name, &t.TaskCount)
	return t, err
}

// List : GET /api/v1/tags
func (h *TagHandler) List(w http.ResponseWriter, r *http.Request) {
	rows, err := h.pool.Query(r.Context(), tagSelect+` WHERE t.user_id = $1 ORDER BY t.name`, userOf(r))
	if err != nil {
		httpjson.ServerError(w, err)
		return
	}

	tags, err := collect(rows, scanTag)
	if err != nil {
		httpjson.ServerError(w, err)
		return
	}
	httpjson.Write(w, http.StatusOK, tags)
}

type tagRequest struct {
	Name string `json:"name"`
}

// Create : POST /api/v1/tags
func (h *TagHandler) Create(w http.ResponseWriter, r *http.Request) {
	var req tagRequest
	if !httpjson.Read(w, r, &req) {
		return
	}

	name, ok := cleanTagName(req.Name)
	if !ok {
		badRequest(w, "Un tag doit faire entre 1 et 50 caractères")
		return
	}

	var id string
	err := h.pool.QueryRow(r.Context(),
		`INSERT INTO tags (user_id, name) VALUES ($1, $2) RETURNING id`, userOf(r), name).Scan(&id)
	if isUniqueViolation(err) {
		httpjson.Error(w, http.StatusConflict, "Ce tag existe déjà")
		return
	}
	if err != nil {
		httpjson.ServerError(w, err)
		return
	}

	created, err := scanTag(h.pool.QueryRow(r.Context(), tagSelect+` WHERE t.id = $1`, id))
	if err != nil {
		httpjson.ServerError(w, err)
		return
	}
	httpjson.Write(w, http.StatusCreated, created)
}

// Update : PATCH /api/v1/tags/{id} — renomme le tag (sur toutes les tâches à la fois).
func (h *TagHandler) Update(w http.ResponseWriter, r *http.Request) {
	id, ok := pathID(w, r)
	if !ok {
		return
	}

	var req tagRequest
	if !httpjson.Read(w, r, &req) {
		return
	}
	if !requireOwned(w, r, h.pool, sqlOwnsTag, id) {
		return
	}

	name, ok := cleanTagName(req.Name)
	if !ok {
		badRequest(w, "Un tag doit faire entre 1 et 50 caractères")
		return
	}

	_, err := h.pool.Exec(r.Context(), `UPDATE tags SET name = $1 WHERE id = $2`, name, id)
	if isUniqueViolation(err) {
		httpjson.Error(w, http.StatusConflict, "Ce tag existe déjà")
		return
	}
	if err != nil {
		httpjson.ServerError(w, err)
		return
	}

	updated, err := scanTag(h.pool.QueryRow(r.Context(), tagSelect+` WHERE t.id = $1`, id))
	if err != nil {
		httpjson.ServerError(w, err)
		return
	}
	httpjson.Write(w, http.StatusOK, updated)
}

// Delete : DELETE /api/v1/tags/{id} — l'enlève aussi de toutes les tâches.
func (h *TagHandler) Delete(w http.ResponseWriter, r *http.Request) {
	id, ok := pathID(w, r)
	if !ok {
		return
	}

	result, err := h.pool.Exec(r.Context(), `DELETE FROM tags WHERE id = $1 AND user_id = $2`, id, userOf(r))
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

// cleanTagName enlève les espaces et le "#" devant (comme pour les tags d'une tâche).
func cleanTagName(name string) (string, bool) {
	return cleanText(strings.TrimPrefix(strings.TrimSpace(name), "#"), 1, maxTagLength)
}
