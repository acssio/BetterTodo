package handlers

import (
	"net/http"

	"github.com/jackc/pgx/v5"
	"github.com/jackc/pgx/v5/pgxpool"

	"bettertodo/api/internal/httpjson"
)

// UrgencyHandler gère les niveaux d'urgence de l'utilisateur (écran Paramètres).
type UrgencyHandler struct {
	pool *pgxpool.Pool
}

// NewUrgencyHandler crée le handler des niveaux d'urgence.
func NewUrgencyHandler(pool *pgxpool.Pool) *UrgencyHandler {
	return &UrgencyHandler{pool: pool}
}

// urgencyLevel : un niveau d'urgence. rank 0 = le moins urgent.
type urgencyLevel struct {
	ID    string `json:"id"`
	Name  string `json:"name"`
	Color string `json:"color"`
	Rank  int    `json:"rank"`
}

const urgencySelect = `SELECT id, name, color, rank FROM urgency_levels`

// Les niveaux sont ordonnés par rank, par utilisateur ($1)
var urgencyOrdering = ordering{
	table:    "urgency_levels",
	column:   "rank",
	siblings: `SELECT id FROM urgency_levels WHERE user_id = $1 ORDER BY rank, created_at`,
}

func scanUrgency(row pgx.Row) (urgencyLevel, error) {
	var u urgencyLevel
	err := row.Scan(&u.ID, &u.Name, &u.Color, &u.Rank)
	return u, err
}

// List : GET /api/v1/urgency-levels
func (h *UrgencyHandler) List(w http.ResponseWriter, r *http.Request) {
	rows, err := h.pool.Query(r.Context(), urgencySelect+` WHERE user_id = $1 ORDER BY rank, created_at`, userOf(r))
	if err != nil {
		httpjson.ServerError(w, err)
		return
	}

	levels, err := collect(rows, scanUrgency)
	if err != nil {
		httpjson.ServerError(w, err)
		return
	}
	httpjson.Write(w, http.StatusOK, levels)
}

type createUrgencyRequest struct {
	ID    *string `json:"id"` // Facultatif : id choisi par l'app (création hors ligne, 8-4)
	Name  string  `json:"name"`
	Color string  `json:"color"`
	Rank  *int    `json:"rank"` // Facultatif : le plus urgent (à la fin) par défaut
}

// Create : POST /api/v1/urgency-levels
func (h *UrgencyHandler) Create(w http.ResponseWriter, r *http.Request) {
	var req createUrgencyRequest
	if !httpjson.Read(w, r, &req) {
		return
	}

	name, ok := cleanText(req.Name, 1, maxNameLength)
	if !ok {
		badRequest(w, nameError("Le nom"))
		return
	}
	if !colorPattern.MatchString(req.Color) {
		badRequest(w, "La couleur doit être au format #RRGGBB")
		return
	}

	ctx := r.Context()
	userID := userOf(r)

	replay, ok := checkClientID(w, r, h.pool, "urgency_levels", sqlOwnsUrgency, req.ID)
	if !ok {
		return
	}
	if replay {
		existing, err := scanUrgency(h.pool.QueryRow(ctx, urgencySelect+` WHERE id = $1`, *req.ID))
		if err != nil {
			httpjson.ServerError(w, err)
			return
		}
		httpjson.Write(w, http.StatusOK, existing)
		return
	}

	var created urgencyLevel
	err := pgx.BeginFunc(ctx, h.pool, func(tx pgx.Tx) error {
		var id string
		err := tx.QueryRow(ctx,
			`INSERT INTO urgency_levels (id, user_id, name, color, rank)
			 VALUES (COALESCE($4::uuid, gen_random_uuid()), $1, $2, $3,
			         (SELECT COALESCE(MAX(rank) + 1, 0) FROM urgency_levels WHERE user_id = $1))
			 RETURNING id`, userID, name, req.Color, req.ID).Scan(&id)
		if err != nil {
			return err
		}

		if req.Rank != nil {
			if err := urgencyOrdering.move(ctx, tx, []any{userID}, id, *req.Rank); err != nil {
				return err
			}
		}

		created, err = scanUrgency(tx.QueryRow(ctx, urgencySelect+` WHERE id = $1`, id))
		return err
	})
	if err != nil {
		httpjson.ServerError(w, err)
		return
	}

	httpjson.Write(w, http.StatusCreated, created)
}

type updateUrgencyRequest struct {
	Name  Optional[string] `json:"name"`
	Color Optional[string] `json:"color"`
	Rank  Optional[int]    `json:"rank"`
}

// Update : PATCH /api/v1/urgency-levels/{id}
func (h *UrgencyHandler) Update(w http.ResponseWriter, r *http.Request) {
	id, ok := pathID(w, r)
	if !ok {
		return
	}

	var req updateUrgencyRequest
	if !httpjson.Read(w, r, &req) {
		return
	}
	if !requireOwned(w, r, h.pool, sqlOwnsUrgency, id) {
		return
	}
	if req.Color.IsNull() || req.Rank.IsNull() {
		badRequest(w, "color et rank ne peuvent pas être null")
		return
	}

	changes := newUpdate("urgency_levels")
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

	ctx := r.Context()
	err := pgx.BeginFunc(ctx, h.pool, func(tx pgx.Tx) error {
		if err := changes.exec(ctx, tx, id); err != nil {
			return err
		}
		if req.Rank.Set {
			return urgencyOrdering.move(ctx, tx, []any{userOf(r)}, id, *req.Rank.Value)
		}
		return nil
	})
	if err != nil {
		httpjson.ServerError(w, err)
		return
	}

	updated, err := scanUrgency(h.pool.QueryRow(ctx, urgencySelect+` WHERE id = $1`, id))
	if err != nil {
		httpjson.ServerError(w, err)
		return
	}
	httpjson.Write(w, http.StatusOK, updated)
}

// Delete : DELETE /api/v1/urgency-levels/{id} — les tâches de ce niveau passent « sans urgence ».
func (h *UrgencyHandler) Delete(w http.ResponseWriter, r *http.Request) {
	id, ok := pathID(w, r)
	if !ok {
		return
	}

	if !requireOwned(w, r, h.pool, sqlOwnsUrgency, id) {
		return
	}
	if err := deleteRecorded(r.Context(), h.pool, `DELETE FROM urgency_levels WHERE id = $1`, entityUrgencyLevel, id); err != nil {
		httpjson.ServerError(w, err)
		return
	}
	w.WriteHeader(http.StatusNoContent)
}
