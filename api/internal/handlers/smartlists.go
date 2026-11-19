package handlers

import (
	"net/http"

	"github.com/jackc/pgx/v5"
	"github.com/jackc/pgx/v5/pgxpool"

	"bettertodo/api/internal/httpjson"
)

// SmartListHandler gère les listes intelligentes personnalisées (étape 7-5) :
// des vues sur les tâches à faire de toutes les listes, filtrées par tags ou par urgence.
// (« Aujourd'hui » et « Planifié » sont à part : voir DueHandler.)
type SmartListHandler struct {
	pool *pgxpool.Pool
}

// NewSmartListHandler crée le handler des listes intelligentes.
func NewSmartListHandler(pool *pgxpool.Pool) *SmartListHandler {
	return &SmartListHandler{pool: pool}
}

const (
	smartKindTags    = "tags"
	smartKindUrgency = "urgency"
)

// smartList : une liste intelligente telle que renvoyée au client.
type smartList struct {
	ID         string   `json:"id"`
	Name       string   `json:"name"`
	Color      string   `json:"color"`
	Icon       string   `json:"icon"`
	Kind       string   `json:"kind"`        // "tags" ou "urgency"
	TagMatch   string   `json:"tag_match"`   // "any" (au moins un tag) ou "all" (tous)
	TagIDs     []string `json:"tag_ids"`     // Tags qui existent encore, par nom
	UrgencyIDs []string `json:"urgency_ids"` // Niveaux qui existent encore, du plus urgent au moins urgent
	Labels     []string `json:"labels"`      // Pour l'affichage : "#boulot", … ou "Haute", …
	Visible    bool     `json:"visible"`
	Position   int      `json:"position"`
	OpenTasks  int      `json:"open_tasks"` // Tâches à faire qui correspondent (compteur de la barre latérale)
}

// sqlSmartMatch : vrai si la tâche t correspond à la liste intelligente s.
// Les tags ou niveaux supprimés depuis sont ignorés (on ne garde que ceux qui existent).
const sqlSmartMatch = `(
	(s.kind = 'urgency' AND t.urgency_id = ANY(s.urgency_ids))
	OR (s.kind = 'tags' AND s.tag_match = 'any' AND EXISTS (
		SELECT 1 FROM task_tags m WHERE m.task_id = t.id AND m.tag_id = ANY(s.tag_ids)))
	OR (s.kind = 'tags' AND s.tag_match = 'all'
		AND EXISTS (SELECT 1 FROM tags x WHERE x.id = ANY(s.tag_ids))
		AND NOT EXISTS (SELECT 1 FROM tags x WHERE x.id = ANY(s.tag_ids)
			AND NOT EXISTS (SELECT 1 FROM task_tags m WHERE m.task_id = t.id AND m.tag_id = x.id)))
)`

const smartListSelect = `
	SELECT s.id, s.name, s.color, s.icon, s.kind, s.tag_match,
	       ARRAY(SELECT x.id::text FROM tags x WHERE x.id = ANY(s.tag_ids) ORDER BY x.name),
	       ARRAY(SELECT u.id::text FROM urgency_levels u WHERE u.id = ANY(s.urgency_ids) ORDER BY u.rank DESC),
	       CASE WHEN s.kind = 'tags'
	            THEN ARRAY(SELECT '#' || x.name FROM tags x WHERE x.id = ANY(s.tag_ids) ORDER BY x.name)
	            ELSE ARRAY(SELECT u.name FROM urgency_levels u WHERE u.id = ANY(s.urgency_ids) ORDER BY u.rank DESC)
	       END,
	       s.visible, s.position,
	       (SELECT count(*) FROM tasks t
	        JOIN task_groups g ON g.id = t.group_id
	        JOIN lists l       ON l.id = g.list_id
	        WHERE l.user_id = s.user_id AND NOT t.done AND ` + sqlSmartMatch + `)
	FROM smart_lists s`

// Les listes intelligentes sont ordonnées par utilisateur ($1)
var smartListOrdering = ordering{
	table:    "smart_lists",
	column:   "position",
	siblings: `SELECT id FROM smart_lists WHERE user_id = $1 ORDER BY position, created_at`,
}

const sqlOwnsSmartList = `SELECT EXISTS (SELECT 1 FROM smart_lists WHERE id = $1 AND user_id = $2)`

func scanSmartList(row pgx.Row) (smartList, error) {
	var s smartList
	err := row.Scan(&s.ID, &s.Name, &s.Color, &s.Icon, &s.Kind, &s.TagMatch, &s.TagIDs, &s.UrgencyIDs, &s.Labels,
		&s.Visible, &s.Position, &s.OpenTasks)
	return s, err
}

// List : GET /api/v1/smart-lists
func (h *SmartListHandler) List(w http.ResponseWriter, r *http.Request) {
	rows, err := h.pool.Query(r.Context(), smartListSelect+` WHERE s.user_id = $1 ORDER BY s.position, s.created_at`, userOf(r))
	if err != nil {
		httpjson.ServerError(w, err)
		return
	}
	lists, err := collect(rows, scanSmartList)
	if err != nil {
		httpjson.ServerError(w, err)
		return
	}
	httpjson.Write(w, http.StatusOK, lists)
}

type createSmartListRequest struct {
	ID         *string  `json:"id"` // Facultatif : id choisi par l'app (création hors ligne, 8-4)
	Name       string   `json:"name"`
	Color      *string  `json:"color"`
	Icon       *string  `json:"icon"`
	Kind       string   `json:"kind"`
	TagMatch   *string  `json:"tag_match"`
	TagIDs     []string `json:"tag_ids"`
	UrgencyIDs []string `json:"urgency_ids"`
	Visible    *bool    `json:"visible"`
}

// Create : POST /api/v1/smart-lists
func (h *SmartListHandler) Create(w http.ResponseWriter, r *http.Request) {
	var req createSmartListRequest
	if !httpjson.Read(w, r, &req) {
		return
	}

	name, ok := cleanText(req.Name, 1, maxNameLength)
	if !ok {
		badRequest(w, nameError("Le nom"))
		return
	}
	color, icon, tagMatch, visible := defaultListColor, defaultListIcon, "any", true
	if req.Color != nil {
		color = *req.Color
	}
	if req.Icon != nil {
		icon = *req.Icon
	}
	if req.TagMatch != nil {
		tagMatch = *req.TagMatch
	}
	if req.Visible != nil {
		visible = *req.Visible
	}
	if message := validateSmartLook(color, icon); message != "" {
		badRequest(w, message)
		return
	}
	if !validSmartKind(req.Kind) {
		badRequest(w, "kind doit valoir tags ou urgency")
		return
	}
	if !validTagMatch(tagMatch) {
		badRequest(w, "tag_match doit valoir any ou all")
		return
	}

	// Le filtre du type choisi doit avoir au moins un élément, et chaque id doit être au compte
	tagIDs, urgencyIDs := unique(req.TagIDs), unique(req.UrgencyIDs)
	if message := missingFilter(req.Kind, tagIDs, urgencyIDs); message != "" {
		badRequest(w, message)
		return
	}
	if !h.requireOwnedIDs(w, r, tagIDs, urgencyIDs) {
		return
	}

	ctx := r.Context()
	userID := userOf(r)

	replay, ok := checkClientID(w, r, h.pool, "smart_lists", sqlOwnsSmartList, req.ID)
	if !ok {
		return
	}
	if replay {
		existing, err := scanSmartList(h.pool.QueryRow(ctx, smartListSelect+` WHERE s.id = $1`, *req.ID))
		if err != nil {
			httpjson.ServerError(w, err)
			return
		}
		httpjson.Write(w, http.StatusOK, existing)
		return
	}

	var created smartList
	err := pgx.BeginFunc(ctx, h.pool, func(tx pgx.Tx) error {
		var id string
		err := tx.QueryRow(ctx,
			`INSERT INTO smart_lists (id, user_id, name, color, icon, kind, tag_match, tag_ids, urgency_ids, visible, position)
			 VALUES (COALESCE($10::uuid, gen_random_uuid()), $1, $2, $3, $4, $5, $6, $7::uuid[], $8::uuid[], $9,
			         (SELECT COALESCE(MAX(position) + 1, 0) FROM smart_lists WHERE user_id = $1))
			 RETURNING id`,
			userID, name, color, icon, req.Kind, tagMatch, tagIDs, urgencyIDs, visible, req.ID).Scan(&id)
		if err != nil {
			return err
		}
		created, err = scanSmartList(tx.QueryRow(ctx, smartListSelect+` WHERE s.id = $1`, id))
		return err
	})
	if err != nil {
		httpjson.ServerError(w, err)
		return
	}

	httpjson.Write(w, http.StatusCreated, created)
}

type updateSmartListRequest struct {
	Name       Optional[string]   `json:"name"`
	Color      Optional[string]   `json:"color"`
	Icon       Optional[string]   `json:"icon"`
	Kind       Optional[string]   `json:"kind"` // Changer de type demande d'envoyer aussi ses ids
	TagMatch   Optional[string]   `json:"tag_match"`
	TagIDs     Optional[[]string] `json:"tag_ids"`
	UrgencyIDs Optional[[]string] `json:"urgency_ids"`
	Visible    Optional[bool]     `json:"visible"`
	Position   Optional[int]      `json:"position"`
}

// Update : PATCH /api/v1/smart-lists/{id}
func (h *SmartListHandler) Update(w http.ResponseWriter, r *http.Request) {
	id, ok := pathID(w, r)
	if !ok {
		return
	}

	var req updateSmartListRequest
	if !httpjson.Read(w, r, &req) {
		return
	}
	if !requireOwned(w, r, h.pool, sqlOwnsSmartList, id) {
		return
	}
	if req.Name.IsNull() || req.Color.IsNull() || req.Icon.IsNull() || req.Kind.IsNull() || req.TagMatch.IsNull() ||
		req.TagIDs.IsNull() || req.UrgencyIDs.IsNull() || req.Visible.IsNull() || req.Position.IsNull() {
		badRequest(w, "Aucun champ ne peut être null")
		return
	}

	changes := newUpdate("smart_lists")
	if req.Name.Set {
		name, ok := cleanText(*req.Name.Value, 1, maxNameLength)
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
	if req.TagMatch.Set {
		if !validTagMatch(*req.TagMatch.Value) {
			badRequest(w, "tag_match doit valoir any ou all")
			return
		}
		changes.set("tag_match", *req.TagMatch.Value)
	}

	// Filtres : une liste d'ids envoyée ne peut pas être vide ; un nouveau type demande ses ids
	var tagIDs, urgencyIDs []string
	if req.TagIDs.Set {
		if tagIDs = unique(*req.TagIDs.Value); len(tagIDs) == 0 {
			badRequest(w, "Choisir au moins un tag")
			return
		}
		changes.args = append(changes.args, tagIDs) // setExpr : %d = numéro de ce dernier argument
		changes.setExpr("tag_ids = $%d::uuid[]")
	}
	if req.UrgencyIDs.Set {
		if urgencyIDs = unique(*req.UrgencyIDs.Value); len(urgencyIDs) == 0 {
			badRequest(w, "Choisir au moins un niveau d'urgence")
			return
		}
		changes.args = append(changes.args, urgencyIDs)
		changes.setExpr("urgency_ids = $%d::uuid[]")
	}
	if req.Kind.Set {
		kind := *req.Kind.Value
		if !validSmartKind(kind) {
			badRequest(w, "kind doit valoir tags ou urgency")
			return
		}
		if (kind == smartKindTags && !req.TagIDs.Set) || (kind == smartKindUrgency && !req.UrgencyIDs.Set) {
			badRequest(w, "Changer de type demande d'envoyer aussi les tags ou les niveaux choisis")
			return
		}
		changes.set("kind", kind)
	}
	if !h.requireOwnedIDs(w, r, tagIDs, urgencyIDs) {
		return
	}
	if req.Visible.Set {
		changes.set("visible", *req.Visible.Value)
	}

	ctx := r.Context()
	var updated smartList

	err := pgx.BeginFunc(ctx, h.pool, func(tx pgx.Tx) error {
		if err := changes.exec(ctx, tx, id); err != nil {
			return err
		}
		if req.Position.Set {
			if err := smartListOrdering.move(ctx, tx, []any{userOf(r)}, id, *req.Position.Value); err != nil {
				return err
			}
		}
		var err error
		updated, err = scanSmartList(tx.QueryRow(ctx, smartListSelect+` WHERE s.id = $1`, id))
		return err
	})
	if err != nil {
		httpjson.ServerError(w, err)
		return
	}

	httpjson.Write(w, http.StatusOK, updated)
}

// Delete : DELETE /api/v1/smart-lists/{id} (les tâches ne sont pas touchées)
func (h *SmartListHandler) Delete(w http.ResponseWriter, r *http.Request) {
	id, ok := pathID(w, r)
	if !ok {
		return
	}
	if !requireOwned(w, r, h.pool, sqlOwnsSmartList, id) {
		return
	}
	if err := deleteRecorded(r.Context(), h.pool, `DELETE FROM smart_lists WHERE id = $1`, entitySmartList, id); err != nil {
		httpjson.ServerError(w, err)
		return
	}
	w.WriteHeader(http.StatusNoContent)
}

// smartTasksSelect : les tâches à faire d'une liste intelligente ($2) de l'utilisateur ($1),
// au même format que /tasks/due (tâche + liste d'origine + nom du groupe).
// Par urgence : du plus urgent au moins urgent, puis par échéance. Par tags : par échéance.
// Les tâches sans échéance viennent après celles qui en ont une.
const smartTasksSelect = `
	SELECT t.id, t.group_id, t.title, t.notes, t.due_date::text, to_char(t.due_time, 'HH24:MI'),
	       t.urgency_id, t.done, t.done_at, t.position,
	       COALESCE(array_agg(tg.name ORDER BY tg.name) FILTER (WHERE tg.id IS NOT NULL), '{}'),
	       t.created_at, t.updated_at,
	       l.id, l.name, l.color, l.icon, g.name
	FROM tasks t
	JOIN task_groups g     ON g.id = t.group_id
	JOIN lists l           ON l.id = g.list_id
	JOIN smart_lists s     ON s.id = $2 AND s.user_id = $1
	LEFT JOIN urgency_levels ul ON ul.id = t.urgency_id
	LEFT JOIN task_tags tt ON tt.task_id = t.id
	LEFT JOIN tags tg      ON tg.id = tt.tag_id
	WHERE l.user_id = $1 AND NOT t.done AND ` + sqlSmartMatch + `
	GROUP BY t.id, l.id, g.id, s.id, ul.id
	ORDER BY CASE WHEN s.kind = 'urgency' THEN ul.rank END DESC NULLS LAST,
	         t.due_date NULLS LAST, t.due_time NULLS LAST, l.position, g.position, t.position`

// Tasks : GET /api/v1/smart-lists/{id}/tasks
func (h *SmartListHandler) Tasks(w http.ResponseWriter, r *http.Request) {
	id, ok := pathID(w, r)
	if !ok {
		return
	}
	if !requireOwned(w, r, h.pool, sqlOwnsSmartList, id) {
		return
	}

	rows, err := h.pool.Query(r.Context(), smartTasksSelect, userOf(r), id)
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

// requireOwnedIDs vérifie que chaque tag et chaque niveau envoyés appartiennent au compte (sinon 400).
func (h *SmartListHandler) requireOwnedIDs(w http.ResponseWriter, r *http.Request, tagIDs, urgencyIDs []string) bool {
	for _, id := range tagIDs {
		if !requireOwnedRef(w, r, h.pool, sqlOwnsTag, id, "Tag inconnu") {
			return false
		}
	}
	for _, id := range urgencyIDs {
		if !requireOwnedRef(w, r, h.pool, sqlOwnsUrgency, id, "Niveau d'urgence inconnu") {
			return false
		}
	}
	return true
}

// -----------------------------------------------------------------------------
// Validation (fonctions pures, testées dans helpers_test.go)
// -----------------------------------------------------------------------------

func validSmartKind(kind string) bool {
	return kind == smartKindTags || kind == smartKindUrgency
}

func validTagMatch(match string) bool {
	return match == "any" || match == "all"
}

func validateSmartLook(color, icon string) string {
	switch {
	case !colorPattern.MatchString(color):
		return "La couleur doit être au format #RRGGBB"
	case !iconPattern.MatchString(icon):
		return "Nom d'icône invalide (lettres minuscules, chiffres et tirets)"
	}
	return ""
}

// missingFilter : message d'erreur si le type choisi n'a aucun tag ou aucun niveau ("" si tout va bien).
func missingFilter(kind string, tagIDs, urgencyIDs []string) string {
	if kind == smartKindTags && len(tagIDs) == 0 {
		return "Choisir au moins un tag"
	}
	if kind == smartKindUrgency && len(urgencyIDs) == 0 {
		return "Choisir au moins un niveau d'urgence"
	}
	return ""
}

// unique retire les doublons en gardant l'ordre (jamais nil : "[]" en JSON, '{}' en SQL).
func unique(ids []string) []string {
	result := []string{}
	seen := map[string]bool{}
	for _, id := range ids {
		if !seen[id] {
			seen[id] = true
			result = append(result, id)
		}
	}
	return result
}
