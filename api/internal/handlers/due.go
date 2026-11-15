package handlers

import (
	"net/http"

	"github.com/jackc/pgx/v5"
	"github.com/jackc/pgx/v5/pgxpool"

	"bettertodo/api/internal/httpjson"
)

// DueHandler sert les listes intelligentes « Aujourd'hui » et « Planifié » :
// les tâches pas encore faites qui ont une échéance, toutes listes confondues.
//
// Le serveur ne décide pas de la date du jour : c'est le client qui l'envoie
// (?until= ou ?today=), dans SON fuseau horaire. Sinon, avec un serveur en UTC,
// une tâche du jour basculerait au lendemain en pleine nuit, heure suisse.
type DueHandler struct {
	pool *pgxpool.Pool
}

// NewDueHandler crée le handler des échéances.
func NewDueHandler(pool *pgxpool.Pool) *DueHandler {
	return &DueHandler{pool: pool}
}

// dueList : la liste d'origine d'une tâche, pour l'afficher à côté du titre.
type dueList struct {
	ID    string `json:"id"`
	Name  string `json:"name"`
	Color string `json:"color"`
	Icon  string `json:"icon"`
}

// dueTask : une tâche + sa liste et le nom de son groupe (les champs de task sont au même niveau en JSON).
type dueTask struct {
	task
	List      dueList `json:"list"`
	GroupName string  `json:"group_name"`
}

const dueSelect = `
	SELECT t.id, t.group_id, t.title, t.notes, t.due_date::text, t.urgency_id, t.done, t.done_at, t.position,
	       COALESCE(array_agg(tg.name ORDER BY tg.name) FILTER (WHERE tg.id IS NOT NULL), '{}'),
	       t.created_at, t.updated_at,
	       l.id, l.name, l.color, l.icon, g.name
	FROM tasks t
	JOIN task_groups g     ON g.id = t.group_id
	JOIN lists l           ON l.id = g.list_id
	LEFT JOIN task_tags tt ON tt.task_id = t.id
	LEFT JOIN tags tg      ON tg.id = tt.tag_id
	WHERE l.user_id = $1 AND NOT t.done AND t.due_date IS NOT NULL
	  AND ($2::date IS NULL OR t.due_date <= $2::date)
	GROUP BY t.id, l.id, g.id
	ORDER BY t.due_date, l.position, g.position, t.position`

func scanDueTask(row pgx.Row) (dueTask, error) {
	var d dueTask
	err := row.Scan(&d.ID, &d.GroupID, &d.Title, &d.Notes, &d.DueDate, &d.UrgencyID, &d.Done, &d.DoneAt,
		&d.Position, &d.Tags, &d.CreatedAt, &d.UpdatedAt,
		&d.List.ID, &d.List.Name, &d.List.Color, &d.List.Icon, &d.GroupName)
	return d, err
}

// List : GET /api/v1/tasks/due[?until=AAAA-MM-JJ]
// Tâches pas faites avec une échéance (jusqu'à until incluse si donné), triées par date.
//
//	« Aujourd'hui » = ?until=<date du jour>   « Planifié » = sans until
func (h *DueHandler) List(w http.ResponseWriter, r *http.Request) {
	var until *string
	if value := r.URL.Query().Get("until"); value != "" {
		if !validDate(value) {
			badRequest(w, "until doit être une date au format AAAA-MM-JJ")
			return
		}
		until = &value
	}

	rows, err := h.pool.Query(r.Context(), dueSelect, userOf(r), until)
	if err != nil {
		httpjson.ServerError(w, err)
		return
	}
	tasks, err := collect(rows, scanDueTask)
	if err != nil {
		httpjson.ServerError(w, err)
		return
	}
	httpjson.Write(w, http.StatusOK, tasks)
}

// Counts : GET /api/v1/tasks/due/counts?today=AAAA-MM-JJ → {"today": n, "scheduled": n}
// Les compteurs de la barre latérale. « today » compte aussi les tâches en retard.
func (h *DueHandler) Counts(w http.ResponseWriter, r *http.Request) {
	today := r.URL.Query().Get("today")
	if !validDate(today) {
		badRequest(w, "today est obligatoire, au format AAAA-MM-JJ")
		return
	}

	var todayCount, scheduledCount int
	err := h.pool.QueryRow(r.Context(), `
		SELECT count(*) FILTER (WHERE t.due_date <= $2::date), count(*)
		FROM tasks t
		JOIN task_groups g ON g.id = t.group_id
		JOIN lists l       ON l.id = g.list_id
		WHERE l.user_id = $1 AND NOT t.done AND t.due_date IS NOT NULL`,
		userOf(r), today).Scan(&todayCount, &scheduledCount)
	if err != nil {
		httpjson.ServerError(w, err)
		return
	}

	httpjson.Write(w, http.StatusOK, map[string]int{"today": todayCount, "scheduled": scheduledCount})
}
