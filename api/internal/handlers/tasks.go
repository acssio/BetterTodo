package handlers

import (
	"context"
	"net/http"
	"time"
	"unicode/utf8"

	"github.com/jackc/pgx/v5"
	"github.com/jackc/pgx/v5/pgxpool"

	"bettertodo/api/internal/httpjson"
)

// TaskHandler gère les tâches et leurs tags.
type TaskHandler struct {
	pool *pgxpool.Pool
}

// NewTaskHandler crée le handler des tâches.
func NewTaskHandler(pool *pgxpool.Pool) *TaskHandler {
	return &TaskHandler{pool: pool}
}

// task : une tâche telle que renvoyée au client.
type task struct {
	ID        string     `json:"id"`
	GroupID   string     `json:"group_id"`
	Title     string     `json:"title"`
	Notes     string     `json:"notes"`
	DueDate   *string    `json:"due_date"`   // "2026-11-18", ou null
	DueTime   *string    `json:"due_time"`   // "14:30", ou null (jamais d'heure sans date)
	UrgencyID *string    `json:"urgency_id"` // null = pas d'urgence
	Done      bool       `json:"done"`
	DoneAt    *time.Time `json:"done_at"`
	Position  int        `json:"position"`
	Tags      []string   `json:"tags"` // Noms des tags, triés
	CreatedAt time.Time  `json:"created_at"`
	UpdatedAt time.Time  `json:"updated_at"`
}

// taskSelect lit les tâches avec leurs tags regroupés dans un tableau.
// Toujours suivi d'un WHERE puis de taskGroupBy.
const taskSelect = `
	SELECT t.id, t.group_id, t.title, t.notes, t.due_date::text, to_char(t.due_time, 'HH24:MI'),
	       t.urgency_id, t.done, t.done_at, t.position,
	       COALESCE(array_agg(tg.name ORDER BY tg.name) FILTER (WHERE tg.id IS NOT NULL), '{}'),
	       t.created_at, t.updated_at
	FROM tasks t
	JOIN task_groups g     ON g.id = t.group_id
	LEFT JOIN task_tags tt ON tt.task_id = t.id
	LEFT JOIN tags tg      ON tg.id = tt.tag_id`

const taskGroupBy = ` GROUP BY t.id`

// Les tâches sont ordonnées dans leur groupe ($1)
var taskOrdering = ordering{
	table:    "tasks",
	column:   "position",
	siblings: `SELECT id FROM tasks WHERE group_id = $1 ORDER BY position, created_at`,
}

func scanTask(row pgx.Row) (task, error) {
	var t task
	err := row.Scan(&t.ID, &t.GroupID, &t.Title, &t.Notes, &t.DueDate, &t.DueTime, &t.UrgencyID, &t.Done, &t.DoneAt,
		&t.Position, &t.Tags, &t.CreatedAt, &t.UpdatedAt)
	return t, err
}

// get lit une tâche par son id.
func (h *TaskHandler) get(ctx context.Context, q querier, id string) (task, error) {
	return scanTask(q.QueryRow(ctx, taskSelect+` WHERE t.id = $1`+taskGroupBy, id))
}

type createTaskRequest struct {
	Title     string   `json:"title"`
	Notes     string   `json:"notes"`
	DueDate   *string  `json:"due_date"`
	DueTime   *string  `json:"due_time"` // "14:30" ; demande une due_date
	UrgencyID *string  `json:"urgency_id"`
	Tags      []string `json:"tags"`
	Position  *int     `json:"position"`
}

// Create : POST /api/v1/groups/{id}/tasks
func (h *TaskHandler) Create(w http.ResponseWriter, r *http.Request) {
	groupID, ok := pathID(w, r)
	if !ok {
		return
	}

	var req createTaskRequest
	if !httpjson.Read(w, r, &req) {
		return
	}
	if !requireOwned(w, r, h.pool, sqlOwnsGroup, groupID) {
		return
	}

	// Validation
	title, ok := cleanText(req.Title, 1, maxTitleLength)
	if !ok {
		badRequest(w, "Le titre doit faire entre 1 et 500 caractères")
		return
	}
	if utf8.RuneCountInString(req.Notes) > maxNotesLength {
		badRequest(w, "Les notes sont trop longues (10 000 caractères maximum)")
		return
	}
	if req.DueDate != nil && !validDate(*req.DueDate) {
		badRequest(w, "due_date doit être une date au format AAAA-MM-JJ")
		return
	}
	if req.DueTime != nil && !validTime(*req.DueTime) {
		badRequest(w, "due_time doit être une heure au format HH:MM")
		return
	}
	if req.DueTime != nil && req.DueDate == nil {
		badRequest(w, "due_time demande une due_date")
		return
	}
	tags, message := normalizeTags(req.Tags)
	if message != "" {
		badRequest(w, message)
		return
	}
	if req.UrgencyID != nil && !requireOwnedRef(w, r, h.pool, sqlOwnsUrgency, *req.UrgencyID, "Niveau d'urgence inconnu") {
		return
	}

	ctx := r.Context()
	var created task

	err := pgx.BeginFunc(ctx, h.pool, func(tx pgx.Tx) error {
		var id string
		err := tx.QueryRow(ctx,
			`INSERT INTO tasks (group_id, title, notes, due_date, due_time, urgency_id, position)
			 VALUES ($1, $2, $3, $4, $5, $6, (SELECT COALESCE(MAX(position) + 1, 0) FROM tasks WHERE group_id = $1))
			 RETURNING id`,
			groupID, title, req.Notes, req.DueDate, req.DueTime, req.UrgencyID).Scan(&id)
		if err != nil {
			return err
		}

		if err := setTaskTags(ctx, tx, userOf(r), id, tags); err != nil {
			return err
		}
		if req.Position != nil {
			if err := taskOrdering.move(ctx, tx, []any{groupID}, id, *req.Position); err != nil {
				return err
			}
		}

		created, err = h.get(ctx, tx, id)
		return err
	})
	if err != nil {
		httpjson.ServerError(w, err)
		return
	}

	httpjson.Write(w, http.StatusCreated, created)
}

// Get : GET /api/v1/tasks/{id}
func (h *TaskHandler) Get(w http.ResponseWriter, r *http.Request) {
	id, ok := pathID(w, r)
	if !ok {
		return
	}
	if !requireOwned(w, r, h.pool, sqlOwnsTask, id) {
		return
	}

	t, err := h.get(r.Context(), h.pool, id)
	if err != nil {
		httpjson.ServerError(w, err)
		return
	}
	httpjson.Write(w, http.StatusOK, t)
}

type updateTaskRequest struct {
	Title     Optional[string]   `json:"title"`
	Notes     Optional[string]   `json:"notes"`
	DueDate   Optional[string]   `json:"due_date"`   // null = enlever l'échéance (et l'heure)
	DueTime   Optional[string]   `json:"due_time"`   // null = enlever l'heure ; demande une échéance
	UrgencyID Optional[string]   `json:"urgency_id"` // null = enlever l'urgence
	Done      Optional[bool]     `json:"done"`
	GroupID   Optional[string]   `json:"group_id"` // Déplacer dans un autre groupe (même d'une autre liste)
	Position  Optional[int]      `json:"position"`
	Tags      Optional[[]string] `json:"tags"` // Remplace TOUS les tags de la tâche
}

// Update : PATCH /api/v1/tasks/{id}
func (h *TaskHandler) Update(w http.ResponseWriter, r *http.Request) {
	id, ok := pathID(w, r)
	if !ok {
		return
	}

	var req updateTaskRequest
	if !httpjson.Read(w, r, &req) {
		return
	}
	if !requireOwned(w, r, h.pool, sqlOwnsTask, id) {
		return
	}

	if req.Title.IsNull() || req.Notes.IsNull() || req.Done.IsNull() || req.GroupID.IsNull() || req.Position.IsNull() {
		badRequest(w, "title, notes, done, group_id et position ne peuvent pas être null")
		return
	}

	// Validation et construction de l'UPDATE
	changes := newUpdate("tasks")
	if req.Title.Set {
		title, ok := cleanText(*req.Title.Value, 1, maxTitleLength)
		if !ok {
			badRequest(w, "Le titre doit faire entre 1 et 500 caractères")
			return
		}
		changes.set("title", title)
	}
	if req.Notes.Set {
		if utf8.RuneCountInString(*req.Notes.Value) > maxNotesLength {
			badRequest(w, "Les notes sont trop longues (10 000 caractères maximum)")
			return
		}
		changes.set("notes", *req.Notes.Value)
	}
	if req.DueDate.Set {
		if req.DueDate.Value != nil && !validDate(*req.DueDate.Value) {
			badRequest(w, "due_date doit être une date au format AAAA-MM-JJ")
			return
		}
		changes.set("due_date", req.DueDate.Value)
	}
	if req.DueTime.Set {
		if req.DueTime.Value != nil {
			if !validTime(*req.DueTime.Value) {
				badRequest(w, "due_time doit être une heure au format HH:MM")
				return
			}
			// Une heure demande une date : celle envoyée, sinon celle déjà sur la tâche
			hasDate := req.DueDate.Set && req.DueDate.Value != nil
			if !req.DueDate.Set {
				if err := h.pool.QueryRow(r.Context(), `SELECT due_date IS NOT NULL FROM tasks WHERE id = $1`, id).Scan(&hasDate); err != nil {
					httpjson.ServerError(w, err)
					return
				}
			}
			if !hasDate {
				badRequest(w, "due_time demande une due_date")
				return
			}
		}
		changes.set("due_time", req.DueTime.Value)
	} else if req.DueDate.IsNull() {
		changes.set("due_time", nil) // Plus d'échéance : plus d'heure non plus
	}
	if req.UrgencyID.Set {
		if req.UrgencyID.Value != nil &&
			!requireOwnedRef(w, r, h.pool, sqlOwnsUrgency, *req.UrgencyID.Value, "Niveau d'urgence inconnu") {
			return
		}
		changes.set("urgency_id", req.UrgencyID.Value)
	}
	if req.Done.Set {
		changes.set("done", *req.Done.Value)
		// done_at : rempli quand on coche (gardé si déjà cochée), vidé quand on décoche
		changes.setExpr("done_at = CASE WHEN $%d THEN COALESCE(done_at, now()) ELSE NULL END")
	}
	if req.GroupID.Set {
		if !requireOwnedRef(w, r, h.pool, sqlOwnsGroup, *req.GroupID.Value, "Groupe inconnu") {
			return
		}
		changes.set("group_id", *req.GroupID.Value)
	}
	var tags []string
	if req.Tags.Set {
		var message string
		if tags, message = normalizeTags(deref(req.Tags.Value)); message != "" {
			badRequest(w, message)
			return
		}
	}

	ctx := r.Context()
	var updated task

	err := pgx.BeginFunc(ctx, h.pool, func(tx pgx.Tx) error {
		if err := changes.exec(ctx, tx, id); err != nil {
			return err
		}
		if req.Tags.Set {
			if err := setTaskTags(ctx, tx, userOf(r), id, tags); err != nil {
				return err
			}
		}

		// Changement de place : nouvelle position, ou à la fin du nouveau groupe
		if req.Position.Set || req.GroupID.Set {
			position := endPosition
			if req.Position.Set {
				position = *req.Position.Value
			}

			var groupID string
			if err := tx.QueryRow(ctx, `SELECT group_id FROM tasks WHERE id = $1`, id).Scan(&groupID); err != nil {
				return err
			}
			if err := taskOrdering.move(ctx, tx, []any{groupID}, id, position); err != nil {
				return err
			}
		}

		var err error
		updated, err = h.get(ctx, tx, id)
		return err
	})
	if err != nil {
		httpjson.ServerError(w, err)
		return
	}

	httpjson.Write(w, http.StatusOK, updated)
}

// Delete : DELETE /api/v1/tasks/{id}
func (h *TaskHandler) Delete(w http.ResponseWriter, r *http.Request) {
	id, ok := pathID(w, r)
	if !ok {
		return
	}
	if !requireOwned(w, r, h.pool, sqlOwnsTask, id) {
		return
	}

	if _, err := h.pool.Exec(r.Context(), `DELETE FROM tasks WHERE id = $1`, id); err != nil {
		httpjson.ServerError(w, err)
		return
	}
	w.WriteHeader(http.StatusNoContent)
}

// setTaskTags remplace les tags de la tâche par names. Les tags inconnus sont créés.
func setTaskTags(ctx context.Context, tx pgx.Tx, userID, taskID string, names []string) error {
	if _, err := tx.Exec(ctx, `DELETE FROM task_tags WHERE task_id = $1`, taskID); err != nil {
		return err
	}

	for _, name := range names {
		_, err := tx.Exec(ctx,
			`INSERT INTO tags (user_id, name) VALUES ($1, $2) ON CONFLICT (user_id, name) DO NOTHING`,
			userID, name)
		if err != nil {
			return err
		}

		_, err = tx.Exec(ctx,
			`INSERT INTO task_tags (task_id, tag_id) SELECT $1, id FROM tags WHERE user_id = $2 AND name = $3`,
			taskID, userID, name)
		if err != nil {
			return err
		}
	}

	// Les tags ont changé : la tâche est considérée comme modifiée (utile pour la synchronisation)
	_, err := tx.Exec(ctx, `UPDATE tasks SET updated_at = now() WHERE id = $1`, taskID)
	return err
}

// normalizeTags nettoie les noms (espaces, "#" devant) et enlève les doublons.
// Retourne un message d'erreur si un nom est vide ou trop long.
func normalizeTags(names []string) ([]string, string) {
	seen := map[string]bool{}
	result := []string{}

	for _, name := range names {
		name, ok := cleanTagName(name)
		if !ok {
			return nil, "Un tag doit faire entre 1 et 50 caractères"
		}
		if !seen[name] {
			seen[name] = true
			result = append(result, name)
		}
	}
	return result, ""
}

// validDate vérifie le format AAAA-MM-JJ et que la date existe (pas de 31 février).
func validDate(s string) bool {
	_, err := time.Parse(time.DateOnly, s)
	return err == nil
}

// validTime vérifie le format HH:MM (00:00 à 23:59).
func validTime(s string) bool {
	_, err := time.Parse("15:04", s)
	return err == nil && len(s) == 5
}
