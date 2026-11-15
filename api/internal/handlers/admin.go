package handlers

import (
	"net/http"
	"strings"

	"github.com/jackc/pgx/v5"
	"github.com/jackc/pgx/v5/pgxpool"

	"bettertodo/api/internal/auth"
	"bettertodo/api/internal/httpjson"
)

// AdminHandler gère la console admin : lister, créer, supprimer des comptes
// et réinitialiser un mot de passe. Toutes ses routes passent par RequireAdmin.
type AdminHandler struct {
	pool *pgxpool.Pool
}

// NewAdminHandler crée le handler de la console admin.
func NewAdminHandler(pool *pgxpool.Pool) *AdminHandler {
	return &AdminHandler{pool: pool}
}

// RequireAdmin ne laisse passer que l'admin (403 sinon).
// À placer après auth.RequireUser, qui a rangé l'utilisateur dans le contexte.
func (h *AdminHandler) RequireAdmin(next http.Handler) http.Handler {
	return http.HandlerFunc(func(w http.ResponseWriter, r *http.Request) {
		var isAdmin bool
		err := h.pool.QueryRow(r.Context(), `SELECT is_admin FROM users WHERE id = $1`,
			auth.UserID(r.Context())).Scan(&isAdmin)
		if err != nil {
			httpjson.ServerError(w, err)
			return
		}
		if !isAdmin {
			httpjson.Error(w, http.StatusForbidden, "Réservé à l'administrateur")
			return
		}
		next.ServeHTTP(w, r)
	})
}

// ListUsers : GET /api/v1/admin/users → tous les comptes, du plus ancien au plus récent.
func (h *AdminHandler) ListUsers(w http.ResponseWriter, r *http.Request) {
	rows, err := h.pool.Query(r.Context(), `SELECT `+userColumns+` FROM users ORDER BY created_at, id`)
	if err != nil {
		httpjson.ServerError(w, err)
		return
	}
	users, err := collect(rows, func(row pgx.Row) (userResponse, error) {
		var u userResponse
		return u, row.Scan(u.scanTargets()...)
	})
	if err != nil {
		httpjson.ServerError(w, err)
		return
	}
	httpjson.Write(w, http.StatusOK, users)
}

// CreateUser : POST /api/v1/admin/users → crée un compte normal (pas admin).
func (h *AdminHandler) CreateUser(w http.ResponseWriter, r *http.Request) {
	var req newUserRequest
	if !httpjson.Read(w, r, &req) {
		return
	}

	email := normalizeEmail(req.Email)
	name := strings.TrimSpace(req.DisplayName)
	if message := validateNewUser(email, req.Password, name); message != "" {
		badRequest(w, message)
		return
	}

	hash, err := auth.HashPassword(req.Password)
	if err != nil {
		httpjson.ServerError(w, err)
		return
	}

	user, err := createUser(r.Context(), h.pool, email, name, hash, false)
	if isUniqueViolation(err) {
		httpjson.Error(w, http.StatusConflict, "Un compte existe déjà avec cet email")
		return
	}
	if err != nil {
		httpjson.ServerError(w, err)
		return
	}
	httpjson.Write(w, http.StatusCreated, user)
}

type resetPasswordRequest struct {
	Password string `json:"password"`
}

// ResetPassword : PATCH /api/v1/admin/users/{id} → nouveau mot de passe.
// Les sessions ouvertes du compte sont fermées (sauf si l'admin change le sien).
func (h *AdminHandler) ResetPassword(w http.ResponseWriter, r *http.Request) {
	id, ok := pathID(w, r)
	if !ok {
		return
	}
	var req resetPasswordRequest
	if !httpjson.Read(w, r, &req) {
		return
	}
	if message := validatePassword(req.Password); message != "" {
		badRequest(w, message)
		return
	}

	hash, err := auth.HashPassword(req.Password)
	if err != nil {
		httpjson.ServerError(w, err)
		return
	}

	ctx := r.Context()
	tag, err := h.pool.Exec(ctx, `UPDATE users SET password_hash = $1, updated_at = now() WHERE id = $2`, hash, id)
	if err != nil {
		httpjson.ServerError(w, err)
		return
	}
	if tag.RowsAffected() == 0 {
		notFound(w)
		return
	}

	if !strings.EqualFold(id, auth.UserID(ctx)) { // L'admin qui change son propre mot de passe reste connecté
		if _, err := h.pool.Exec(ctx, `DELETE FROM sessions WHERE user_id = $1`, id); err != nil {
			httpjson.ServerError(w, err)
			return
		}
	}
	w.WriteHeader(http.StatusNoContent)
}

// DeleteUser : DELETE /api/v1/admin/users/{id} → supprime le compte et toutes ses données
// (listes, tâches, tags, sessions… grâce aux ON DELETE CASCADE). L'admin ne peut pas se supprimer.
func (h *AdminHandler) DeleteUser(w http.ResponseWriter, r *http.Request) {
	id, ok := pathID(w, r)
	if !ok {
		return
	}
	if strings.EqualFold(id, auth.UserID(r.Context())) {
		badRequest(w, "Tu ne peux pas supprimer ton propre compte")
		return
	}

	tag, err := h.pool.Exec(r.Context(), `DELETE FROM users WHERE id = $1`, id)
	if err != nil {
		httpjson.ServerError(w, err)
		return
	}
	if tag.RowsAffected() == 0 {
		notFound(w)
		return
	}
	w.WriteHeader(http.StatusNoContent)
}
