package handlers

import (
	"net/http"

	"github.com/jackc/pgx/v5"
	"github.com/jackc/pgx/v5/pgxpool"

	"bettertodo/api/internal/auth"
	"bettertodo/api/internal/httpjson"
)

// Valeurs par défaut d'une nouvelle liste (les mêmes que dans 001_schema.sql)
const (
	defaultListColor = "#6B6B6B"
	defaultListIcon  = "list"
	defaultListView  = "columns"
)

// Nom du groupe créé avec chaque nouvelle liste, selon la langue du compte.
// Sert aussi à vérifier qu'une langue est connue (voir UpdateMe).
var defaultGroupName = map[string]string{
	"fr": "Tâches",
	"en": "Tasks",
}

// ListHandler gère les listes, et la vue complète d'une liste (board).
type ListHandler struct {
	pool *pgxpool.Pool
}

// NewListHandler crée le handler des listes.
func NewListHandler(pool *pgxpool.Pool) *ListHandler {
	return &ListHandler{pool: pool}
}

// list : une liste telle que renvoyée au client.
type list struct {
	ID          string  `json:"id"`
	FolderID    *string `json:"folder_id"` // null = hors groupe de listes
	Name        string  `json:"name"`
	Color       string  `json:"color"`
	Icon        string  `json:"icon"`
	DefaultView string  `json:"default_view"`
	ShowDone    bool    `json:"show_done"` // false = tâches faites masquées dans cette liste
	Position    int     `json:"position"`
	OpenTasks   int     `json:"open_tasks"` // Nombre de tâches pas encore faites (pour la barre latérale)
}

const listSelect = `
	SELECT l.id, l.folder_id, l.name, l.color, l.icon, l.default_view, l.show_done, l.position,
	       (SELECT count(*) FROM tasks t JOIN task_groups g ON g.id = t.group_id
	        WHERE g.list_id = l.id AND NOT t.done)
	FROM lists l`

// Les listes sont ordonnées dans leur groupe de listes : $1 = utilisateur, $2 = folder_id (ou NULL)
var listOrdering = ordering{
	table:  "lists",
	column: "position",
	siblings: `SELECT id FROM lists WHERE user_id = $1 AND folder_id IS NOT DISTINCT FROM $2
	           ORDER BY position, created_at`,
}

func scanList(row pgx.Row) (list, error) {
	var l list
	err := row.Scan(&l.ID, &l.FolderID, &l.Name, &l.Color, &l.Icon, &l.DefaultView, &l.ShowDone, &l.Position, &l.OpenTasks)
	return l, err
}

// List : GET /api/v1/lists
func (h *ListHandler) List(w http.ResponseWriter, r *http.Request) {
	// Ordre de la barre latérale : listes hors groupe d'abord, puis par groupe de listes, puis par position
	rows, err := h.pool.Query(r.Context(),
		listSelect+` WHERE l.user_id = $1
		ORDER BY (SELECT f.position FROM list_folders f WHERE f.id = l.folder_id) NULLS FIRST,
		         l.folder_id, l.position, l.created_at`, auth.UserID(r.Context()))
	if err != nil {
		httpjson.ServerError(w, err)
		return
	}

	lists, err := collect(rows, scanList)
	if err != nil {
		httpjson.ServerError(w, err)
		return
	}
	httpjson.Write(w, http.StatusOK, lists)
}

type createListRequest struct {
	Name        string  `json:"name"`
	FolderID    *string `json:"folder_id"`
	Color       *string `json:"color"`
	Icon        *string `json:"icon"`
	DefaultView *string `json:"default_view"`
	Position    *int    `json:"position"`
}

// Create : POST /api/v1/lists
func (h *ListHandler) Create(w http.ResponseWriter, r *http.Request) {
	var req createListRequest
	if !httpjson.Read(w, r, &req) {
		return
	}

	name, ok := cleanText(req.Name, 1, maxNameLength)
	if !ok {
		badRequest(w, nameError("Le nom"))
		return
	}

	color := defaultListColor
	if req.Color != nil {
		color = *req.Color
	}
	icon := defaultListIcon
	if req.Icon != nil {
		icon = *req.Icon
	}
	// Sans default_view, la nouvelle liste prend la vue par défaut du compte (voir INSERT plus bas)
	view := defaultListView
	if req.DefaultView != nil {
		view = *req.DefaultView
	}
	if message := validateListLook(color, icon, view); message != "" {
		badRequest(w, message)
		return
	}

	if req.FolderID != nil && !requireOwnedRef(w, r, h.pool, sqlOwnsFolder, *req.FolderID, "Groupe de listes inconnu") {
		return
	}

	ctx := r.Context()
	userID := auth.UserID(ctx)
	var created list

	err := pgx.BeginFunc(ctx, h.pool, func(tx pgx.Tx) error {
		var id string
		err := tx.QueryRow(ctx,
			`INSERT INTO lists (user_id, folder_id, name, color, icon, default_view, position)
			 VALUES ($1, $2, $3, $4, $5,
			         COALESCE($6, (SELECT default_view FROM users WHERE id = $1)),
			         (SELECT COALESCE(MAX(position) + 1, 0) FROM lists
			          WHERE user_id = $1 AND folder_id IS NOT DISTINCT FROM $2))
			 RETURNING id`,
			userID, req.FolderID, name, color, icon, req.DefaultView).Scan(&id)
		if err != nil {
			return err
		}

		// Groupe par défaut, nommé dans la langue du compte
		var language string
		if err := tx.QueryRow(ctx, `SELECT language FROM users WHERE id = $1`, userID).Scan(&language); err != nil {
			return err
		}
		_, err = tx.Exec(ctx, `INSERT INTO task_groups (list_id, name, position) VALUES ($1, $2, 0)`,
			id, defaultGroupName[language])
		if err != nil {
			return err
		}

		if req.Position != nil {
			if err := listOrdering.move(ctx, tx, []any{userID, req.FolderID}, id, *req.Position); err != nil {
				return err
			}
		}

		created, err = scanList(tx.QueryRow(ctx, listSelect+` WHERE l.id = $1`, id))
		return err
	})
	if err != nil {
		httpjson.ServerError(w, err)
		return
	}

	httpjson.Write(w, http.StatusCreated, created)
}

type updateListRequest struct {
	Name        Optional[string] `json:"name"`
	FolderID    Optional[string] `json:"folder_id"` // null = sortir du groupe de listes
	Color       Optional[string] `json:"color"`
	Icon        Optional[string] `json:"icon"`
	DefaultView Optional[string] `json:"default_view"`
	ShowDone    Optional[bool]   `json:"show_done"`
	Position    Optional[int]    `json:"position"`
}

// Update : PATCH /api/v1/lists/{id}
func (h *ListHandler) Update(w http.ResponseWriter, r *http.Request) {
	id, ok := pathID(w, r)
	if !ok {
		return
	}

	var req updateListRequest
	if !httpjson.Read(w, r, &req) {
		return
	}
	if !requireOwned(w, r, h.pool, sqlOwnsList, id) {
		return
	}

	// Ces champs ne peuvent pas être null
	if req.Color.IsNull() || req.Icon.IsNull() || req.DefaultView.IsNull() || req.ShowDone.IsNull() || req.Position.IsNull() {
		badRequest(w, "color, icon, default_view, show_done et position ne peuvent pas être null")
		return
	}

	changes := newUpdate("lists")
	if req.Name.Set {
		name, ok := cleanText(deref(req.Name.Value), 1, maxNameLength)
		if !ok {
			badRequest(w, nameError("Le nom"))
			return
		}
		changes.set("name", name)
	}
	if req.Color.Set {
		if !colorPattern.MatchString(*req.Color.Value) {
			badRequest(w, "La couleur doit être au format #RRGGBB")
			return
		}
		changes.set("color", *req.Color.Value)
	}
	if req.Icon.Set {
		if !iconPattern.MatchString(*req.Icon.Value) {
			badRequest(w, "Nom d'icône invalide (lettres minuscules, chiffres et tirets)")
			return
		}
		changes.set("icon", *req.Icon.Value)
	}
	if req.DefaultView.Set {
		if !validView(*req.DefaultView.Value) {
			badRequest(w, "default_view doit valoir columns ou sections")
			return
		}
		changes.set("default_view", *req.DefaultView.Value)
	}
	if req.ShowDone.Set {
		changes.set("show_done", *req.ShowDone.Value)
	}
	if req.FolderID.Set {
		if req.FolderID.Value != nil &&
			!requireOwnedRef(w, r, h.pool, sqlOwnsFolder, *req.FolderID.Value, "Groupe de listes inconnu") {
			return
		}
		changes.set("folder_id", req.FolderID.Value)
	}

	ctx := r.Context()
	userID := auth.UserID(ctx)

	err := pgx.BeginFunc(ctx, h.pool, func(tx pgx.Tx) error {
		if err := changes.exec(ctx, tx, id); err != nil {
			return err
		}

		// Changement de place : nouvelle position, ou à la fin du nouveau groupe de listes
		if req.Position.Set || req.FolderID.Set {
			position := endPosition
			if req.Position.Set {
				position = *req.Position.Value
			}

			var folderID *string
			if err := tx.QueryRow(ctx, `SELECT folder_id FROM lists WHERE id = $1`, id).Scan(&folderID); err != nil {
				return err
			}
			return listOrdering.move(ctx, tx, []any{userID, folderID}, id, position)
		}
		return nil
	})
	if err != nil {
		httpjson.ServerError(w, err)
		return
	}

	updated, err := scanList(h.pool.QueryRow(ctx, listSelect+` WHERE l.id = $1`, id))
	if err != nil {
		httpjson.ServerError(w, err)
		return
	}
	httpjson.Write(w, http.StatusOK, updated)
}

// Delete : DELETE /api/v1/lists/{id} — supprime aussi ses groupes et ses tâches.
func (h *ListHandler) Delete(w http.ResponseWriter, r *http.Request) {
	id, ok := pathID(w, r)
	if !ok {
		return
	}

	result, err := h.pool.Exec(r.Context(),
		`DELETE FROM lists WHERE id = $1 AND user_id = $2`, id, auth.UserID(r.Context()))
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

// ClearDone : DELETE /api/v1/lists/{id}/done-tasks → supprime toutes les tâches faites de la liste.
// Une seule requête SQL : soit tout est supprimé, soit rien. Réponse : {"deleted": n}.
func (h *ListHandler) ClearDone(w http.ResponseWriter, r *http.Request) {
	id, ok := pathID(w, r)
	if !ok {
		return
	}
	if !requireOwned(w, r, h.pool, sqlOwnsList, id) {
		return
	}

	result, err := h.pool.Exec(r.Context(),
		`DELETE FROM tasks WHERE done AND group_id IN (SELECT id FROM task_groups WHERE list_id = $1)`, id)
	if err != nil {
		httpjson.ServerError(w, err)
		return
	}
	httpjson.Write(w, http.StatusOK, map[string]int64{"deleted": result.RowsAffected()})
}

// board : tout ce qu'il faut pour afficher une liste, en un seul appel.
type board struct {
	List   list         `json:"list"`
	Groups []boardGroup `json:"groups"`
}

// boardGroup : un groupe et ses tâches (les champs de group sont mis au même niveau en JSON).
type boardGroup struct {
	group
	Tasks []task `json:"tasks"`
}

// Board : GET /api/v1/lists/{id}/board
//
// 3 requêtes au lieu d'une par groupe : la liste, ses groupes, puis toutes ses tâches,
// qu'on range ensuite dans leur groupe en Go.
func (h *ListHandler) Board(w http.ResponseWriter, r *http.Request) {
	id, ok := pathID(w, r)
	if !ok {
		return
	}
	if !requireOwned(w, r, h.pool, sqlOwnsList, id) {
		return
	}

	ctx := r.Context()
	result := board{Groups: []boardGroup{}}

	var err error
	result.List, err = scanList(h.pool.QueryRow(ctx, listSelect+` WHERE l.id = $1`, id))
	if err != nil {
		httpjson.ServerError(w, err)
		return
	}

	rows, err := h.pool.Query(ctx, groupSelect+` WHERE list_id = $1 ORDER BY position, created_at`, id)
	if err != nil {
		httpjson.ServerError(w, err)
		return
	}
	groups, err := collect(rows, scanGroup)
	if err != nil {
		httpjson.ServerError(w, err)
		return
	}

	rows, err = h.pool.Query(ctx, taskSelect+` WHERE g.list_id = $1 `+taskGroupBy+` ORDER BY t.position, t.created_at`, id)
	if err != nil {
		httpjson.ServerError(w, err)
		return
	}
	tasks, err := collect(rows, scanTask)
	if err != nil {
		httpjson.ServerError(w, err)
		return
	}

	// Range chaque tâche dans son groupe
	tasksByGroup := map[string][]task{}
	for _, t := range tasks {
		tasksByGroup[t.GroupID] = append(tasksByGroup[t.GroupID], t)
	}
	for _, g := range groups {
		groupTasks := tasksByGroup[g.ID]
		if groupTasks == nil {
			groupTasks = []task{}
		}
		result.Groups = append(result.Groups, boardGroup{group: g, Tasks: groupTasks})
	}

	httpjson.Write(w, http.StatusOK, result)
}

// validateListLook vérifie couleur, icône et vue. Retourne un message, ou "" si tout va bien.
func validateListLook(color, icon, view string) string {
	switch {
	case !colorPattern.MatchString(color):
		return "La couleur doit être au format #RRGGBB"
	case !iconPattern.MatchString(icon):
		return "Nom d'icône invalide (lettres minuscules, chiffres et tirets)"
	case !validView(view):
		return "default_view doit valoir columns ou sections"
	}
	return ""
}

func validView(view string) bool {
	return view == "columns" || view == "sections"
}
