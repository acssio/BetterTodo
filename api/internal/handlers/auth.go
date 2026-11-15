// Package handlers contient les routes HTTP de l'API, un fichier par ressource.
package handlers

import (
	"context"
	"errors"
	"net/http"
	"strings"
	"time"
	"unicode/utf8"

	"github.com/jackc/pgx/v5"
	"github.com/jackc/pgx/v5/pgconn"
	"github.com/jackc/pgx/v5/pgxpool"

	"bettertodo/api/internal/auth"
	"bettertodo/api/internal/httpjson"
)

// Niveaux d'urgence créés pour chaque nouveau compte (rank = position dans ce tableau)
var defaultUrgencyLevels = []struct{ name, color string }{
	{"Basse", "#3FA66B"},
	{"Moyenne", "#E8A33D"},
	{"Haute", "#E5484D"},
}

// Hash factice, comparé quand l'email n'existe pas : la réponse prend alors autant
// de temps que pour un vrai compte, et on ne peut pas deviner quels emails sont inscrits.
var dummyHash, _ = auth.HashPassword("mot-de-passe-factice")

// AuthHandler gère l'inscription, la connexion, la déconnexion et le profil.
type AuthHandler struct {
	pool         *pgxpool.Pool
	sessions     *auth.SessionStore
	cookieSecure bool // Cookie envoyé seulement en HTTPS
}

// NewAuthHandler crée le handler des comptes.
func NewAuthHandler(pool *pgxpool.Pool, sessions *auth.SessionStore, cookieSecure bool) *AuthHandler {
	return &AuthHandler{pool: pool, sessions: sessions, cookieSecure: cookieSecure}
}

// userResponse : un utilisateur tel que renvoyé au client (jamais le hash du mot de passe).
type userResponse struct {
	ID          string    `json:"id"`
	Email       string    `json:"email"`
	DisplayName string    `json:"display_name"`
	DefaultView string    `json:"default_view"` // Vue des nouvelles listes : "columns" ou "sections"
	CreatedAt   time.Time `json:"created_at"`
}

// userColumns : colonnes lues pour construire un userResponse (dans l'ordre de scanTargets).
const userColumns = `id, email, display_name, default_view, created_at`

// scanTargets : les champs de u, dans l'ordre de userColumns.
func (u *userResponse) scanTargets() []any {
	return []any{&u.ID, &u.Email, &u.DisplayName, &u.DefaultView, &u.CreatedAt}
}

// sessionResponse : réponse à une inscription ou une connexion réussie.
type sessionResponse struct {
	User      userResponse `json:"user"`
	Token     string       `json:"token"`      // Pour les apps natives (en-tête Authorization)
	ExpiresAt time.Time    `json:"expires_at"` // La page web, elle, utilise le cookie
}

type registerRequest struct {
	Email       string `json:"email"`
	Password    string `json:"password"`
	DisplayName string `json:"display_name"`
}

type loginRequest struct {
	Email    string `json:"email"`
	Password string `json:"password"`
}

// Register : POST /api/v1/auth/register
func (h *AuthHandler) Register(w http.ResponseWriter, r *http.Request) {
	var req registerRequest
	if !httpjson.Read(w, r, &req) {
		return
	}

	email := normalizeEmail(req.Email)
	name := strings.TrimSpace(req.DisplayName)
	if message := validateRegister(email, req.Password, name); message != "" {
		httpjson.Error(w, http.StatusBadRequest, message)
		return
	}

	hash, err := auth.HashPassword(req.Password)
	if err != nil {
		httpjson.ServerError(w, err)
		return
	}

	user, err := h.createUser(r.Context(), email, name, hash)
	if isUniqueViolation(err) {
		httpjson.Error(w, http.StatusConflict, "Un compte existe déjà avec cet email")
		return
	}
	if err != nil {
		httpjson.ServerError(w, err)
		return
	}

	h.startSession(w, r, user, http.StatusCreated)
}

// Login : POST /api/v1/auth/login
func (h *AuthHandler) Login(w http.ResponseWriter, r *http.Request) {
	var req loginRequest
	if !httpjson.Read(w, r, &req) {
		return
	}

	var user userResponse
	var hash string
	err := h.pool.QueryRow(r.Context(),
		`SELECT `+userColumns+`, password_hash FROM users WHERE email = $1`,
		normalizeEmail(req.Email)).Scan(append(user.scanTargets(), &hash)...)

	if errors.Is(err, pgx.ErrNoRows) {
		auth.CheckPassword(dummyHash, req.Password) // Même durée qu'avec un vrai compte
		httpjson.Error(w, http.StatusUnauthorized, "Email ou mot de passe incorrect")
		return
	}
	if err != nil {
		httpjson.ServerError(w, err)
		return
	}

	if !auth.CheckPassword(hash, req.Password) {
		httpjson.Error(w, http.StatusUnauthorized, "Email ou mot de passe incorrect")
		return
	}

	h.startSession(w, r, user, http.StatusOK)
}

// Logout : POST /api/v1/auth/logout (connexion requise)
func (h *AuthHandler) Logout(w http.ResponseWriter, r *http.Request) {
	if err := h.sessions.Delete(r.Context(), auth.TokenFromRequest(r)); err != nil {
		httpjson.ServerError(w, err)
		return
	}

	// Demande au navigateur d'effacer le cookie
	http.SetCookie(w, &http.Cookie{
		Name: auth.CookieName, Value: "", Path: "/api", MaxAge: -1,
		HttpOnly: true, Secure: h.cookieSecure, SameSite: http.SameSiteLaxMode,
	})
	w.WriteHeader(http.StatusNoContent)
}

// Me : GET /api/v1/me (connexion requise) → l'utilisateur connecté
func (h *AuthHandler) Me(w http.ResponseWriter, r *http.Request) {
	var user userResponse
	err := h.pool.QueryRow(r.Context(),
		`SELECT `+userColumns+` FROM users WHERE id = $1`,
		auth.UserID(r.Context())).Scan(user.scanTargets()...)
	if err != nil {
		httpjson.ServerError(w, err)
		return
	}

	httpjson.Write(w, http.StatusOK, user)
}

type updateMeRequest struct {
	DisplayName Optional[string] `json:"display_name"`
	DefaultView Optional[string] `json:"default_view"`
}

// UpdateMe : PATCH /api/v1/me (connexion requise) → modifie le nom affiché et/ou la vue par défaut
func (h *AuthHandler) UpdateMe(w http.ResponseWriter, r *http.Request) {
	var req updateMeRequest
	if !httpjson.Read(w, r, &req) {
		return
	}
	if req.DisplayName.IsNull() || req.DefaultView.IsNull() {
		badRequest(w, "display_name et default_view ne peuvent pas être null")
		return
	}

	changes := newUpdate("users")
	if req.DisplayName.Set {
		name := strings.TrimSpace(*req.DisplayName.Value)
		if name == "" || utf8.RuneCountInString(name) > 100 {
			badRequest(w, "Le nom affiché doit faire entre 1 et 100 caractères")
			return
		}
		changes.set("display_name", name)
	}
	if req.DefaultView.Set {
		if !validView(*req.DefaultView.Value) {
			badRequest(w, "default_view doit valoir columns ou sections")
			return
		}
		changes.set("default_view", *req.DefaultView.Value)
	}

	ctx := r.Context()
	userID := auth.UserID(ctx)
	if err := changes.exec(ctx, h.pool, userID); err != nil {
		httpjson.ServerError(w, err)
		return
	}

	var user userResponse
	if err := h.pool.QueryRow(ctx, `SELECT `+userColumns+` FROM users WHERE id = $1`, userID).Scan(user.scanTargets()...); err != nil {
		httpjson.ServerError(w, err)
		return
	}
	httpjson.Write(w, http.StatusOK, user)
}

// createUser crée l'utilisateur et ses niveaux d'urgence par défaut, dans une seule transaction.
func (h *AuthHandler) createUser(ctx context.Context, email, name, hash string) (userResponse, error) {
	var user userResponse

	tx, err := h.pool.Begin(ctx)
	if err != nil {
		return user, err
	}
	defer tx.Rollback(ctx) // Sans effet si le Commit a réussi

	err = tx.QueryRow(ctx,
		`INSERT INTO users (email, display_name, password_hash) VALUES ($1, $2, $3)
		 RETURNING `+userColumns,
		email, name, hash).Scan(user.scanTargets()...)
	if err != nil {
		return user, err
	}

	for rank, level := range defaultUrgencyLevels {
		_, err = tx.Exec(ctx,
			`INSERT INTO urgency_levels (user_id, name, color, rank) VALUES ($1, $2, $3, $4)`,
			user.ID, level.name, level.color, rank)
		if err != nil {
			return user, err
		}
	}

	return user, tx.Commit(ctx)
}

// startSession ouvre une session, pose le cookie et renvoie l'utilisateur et le jeton.
func (h *AuthHandler) startSession(w http.ResponseWriter, r *http.Request, user userResponse, status int) {
	token, expiresAt, err := h.sessions.Create(r.Context(), user.ID)
	if err != nil {
		httpjson.ServerError(w, err)
		return
	}

	http.SetCookie(w, &http.Cookie{
		Name:     auth.CookieName,
		Value:    token,
		Path:     "/api",
		Expires:  expiresAt,
		HttpOnly: true,                 // Illisible par le JavaScript de la page
		Secure:   h.cookieSecure,       // HTTPS uniquement (en production)
		SameSite: http.SameSiteLaxMode, // Pas envoyé par les requêtes venant d'autres sites
	})

	httpjson.Write(w, status, sessionResponse{User: user, Token: token, ExpiresAt: expiresAt})
}

// normalizeEmail enlève les espaces et met en minuscules (comme l'exige la table users).
func normalizeEmail(email string) string {
	return strings.ToLower(strings.TrimSpace(email))
}

// validateRegister retourne un message d'erreur, ou "" si tout est valide.
func validateRegister(email, password, name string) string {
	switch {
	case !strings.Contains(email, "@") || len(email) > 254:
		return "Email invalide"
	case utf8.RuneCountInString(password) < auth.MinPasswordLength:
		return "Le mot de passe doit faire au moins 8 caractères"
	case len(password) > auth.MaxPasswordBytes:
		return "Le mot de passe est trop long (72 octets maximum)"
	case name == "" || utf8.RuneCountInString(name) > 100:
		return "Le nom affiché doit faire entre 1 et 100 caractères"
	}
	return ""
}

// isUniqueViolation indique si err vient d'une contrainte UNIQUE (code PostgreSQL 23505).
func isUniqueViolation(err error) bool {
	var pgErr *pgconn.PgError
	return errors.As(err, &pgErr) && pgErr.Code == "23505"
}
