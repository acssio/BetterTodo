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

// errSetupDone : l'installation a déjà eu lieu (un compte existe), POST /auth/setup est refusé.
var errSetupDone = errors.New("installation déjà faite")

// AuthHandler gère l'installation (premier compte), la connexion, la déconnexion et le profil.
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
	ID                 string    `json:"id"`
	Email              string    `json:"email"`
	DisplayName        string    `json:"display_name"`
	DefaultView        string    `json:"default_view"`         // Vue des nouvelles listes : "columns" ou "sections"
	Language           string    `json:"language"`             // "fr" ou "en"
	QuickTaskDue       string    `json:"quick_task_due"`       // Échéance proposée par la Quick task : "none", "today" ou "tomorrow"
	ShowToday          bool      `json:"show_today"`           // « Aujourd'hui » affiché dans la barre latérale
	ShowScheduled      bool      `json:"show_scheduled"`       // « Planifié » affiché dans la barre latérale
	IsAdmin            bool      `json:"is_admin"`             // Accès à la console admin
	MustChangePassword bool      `json:"must_change_password"` // Mot de passe donné par l'admin : à changer
	CreatedAt          time.Time `json:"created_at"`
}

// userColumns : colonnes lues pour construire un userResponse (dans l'ordre de scanTargets).
const userColumns = `id, email, display_name, default_view, language, quick_task_due, show_today, show_scheduled,
	is_admin, must_change_password, created_at`

// scanTargets : les champs de u, dans l'ordre de userColumns.
func (u *userResponse) scanTargets() []any {
	return []any{&u.ID, &u.Email, &u.DisplayName, &u.DefaultView, &u.Language, &u.QuickTaskDue, &u.ShowToday, &u.ShowScheduled,
		&u.IsAdmin, &u.MustChangePassword, &u.CreatedAt}
}

// sessionResponse : réponse à une installation ou une connexion réussie.
type sessionResponse struct {
	User      userResponse `json:"user"`
	Token     string       `json:"token"`      // Pour les apps natives (en-tête Authorization)
	ExpiresAt time.Time    `json:"expires_at"` // La page web, elle, utilise le cookie
}

// newUserRequest : création d'un compte (installation ou console admin).
type newUserRequest struct {
	Email       string `json:"email"`
	Password    string `json:"password"`
	DisplayName string `json:"display_name"`
}

type loginRequest struct {
	Email    string `json:"email"`
	Password string `json:"password"`
}

// SetupStatus : GET /api/v1/auth/setup → {"needs_setup": true} tant qu'aucun compte n'existe.
// La page web affiche alors « Créer le compte administrateur » au lieu de « Connexion ».
func (h *AuthHandler) SetupStatus(w http.ResponseWriter, r *http.Request) {
	var needsSetup bool
	if err := h.pool.QueryRow(r.Context(), `SELECT NOT EXISTS (SELECT 1 FROM users)`).Scan(&needsSetup); err != nil {
		httpjson.ServerError(w, err)
		return
	}
	httpjson.Write(w, http.StatusOK, map[string]bool{"needs_setup": needsSetup})
}

// Setup : POST /api/v1/auth/setup → crée le premier compte, qui est admin.
// Refusé (403) dès qu'un compte existe : les comptes suivants sont créés par l'admin.
func (h *AuthHandler) Setup(w http.ResponseWriter, r *http.Request) {
	var req newUserRequest
	if !httpjson.Read(w, r, &req) {
		return
	}

	email := normalizeEmail(req.Email)
	name := strings.TrimSpace(req.DisplayName)
	if message := validateNewUser(email, req.Password, name); message != "" {
		httpjson.Error(w, http.StatusBadRequest, message)
		return
	}

	hash, err := auth.HashPassword(req.Password)
	if err != nil {
		httpjson.ServerError(w, err)
		return
	}

	user, err := createUser(r.Context(), h.pool, email, name, hash, true)
	if errors.Is(err, errSetupDone) {
		httpjson.Error(w, http.StatusForbidden, "L'installation est déjà faite : demande un compte à l'administrateur")
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
	Language    Optional[string] `json:"language"`
	QuickTask   Optional[string] `json:"quick_task_due"` // "none", "today" ou "tomorrow"
	ShowToday   Optional[bool]   `json:"show_today"`
	ShowSched   Optional[bool]   `json:"show_scheduled"`
}

// UpdateMe : PATCH /api/v1/me (connexion requise) → modifie le nom affiché, la vue par défaut,
// la langue, l'échéance proposée par la Quick task et/ou l'affichage d'Aujourd'hui / Planifié
func (h *AuthHandler) UpdateMe(w http.ResponseWriter, r *http.Request) {
	var req updateMeRequest
	if !httpjson.Read(w, r, &req) {
		return
	}
	if req.DisplayName.IsNull() || req.DefaultView.IsNull() || req.Language.IsNull() || req.QuickTask.IsNull() ||
		req.ShowToday.IsNull() || req.ShowSched.IsNull() {
		badRequest(w, "Aucun champ ne peut être null")
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
	if req.Language.Set {
		if _, ok := defaultGroupName[*req.Language.Value]; !ok {
			badRequest(w, "language doit valoir fr ou en")
			return
		}
		changes.set("language", *req.Language.Value)
	}
	if req.QuickTask.Set {
		if !validQuickTaskDue(*req.QuickTask.Value) {
			badRequest(w, "quick_task_due doit valoir none, today ou tomorrow")
			return
		}
		changes.set("quick_task_due", *req.QuickTask.Value)
	}
	if req.ShowToday.Set {
		changes.set("show_today", *req.ShowToday.Value)
	}
	if req.ShowSched.Set {
		changes.set("show_scheduled", *req.ShowSched.Value)
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

type changePasswordRequest struct {
	CurrentPassword string `json:"current_password"`
	NewPassword     string `json:"new_password"`
}

// ChangePassword : POST /api/v1/me/password (connexion requise) → change son propre mot de passe.
// Il faut l'ancien. Les autres sessions du compte sont fermées, celle-ci reste ouverte.
func (h *AuthHandler) ChangePassword(w http.ResponseWriter, r *http.Request) {
	var req changePasswordRequest
	if !httpjson.Read(w, r, &req) {
		return
	}

	ctx := r.Context()
	userID := auth.UserID(ctx)

	var hash string
	if err := h.pool.QueryRow(ctx, `SELECT password_hash FROM users WHERE id = $1`, userID).Scan(&hash); err != nil {
		httpjson.ServerError(w, err)
		return
	}
	if !auth.CheckPassword(hash, req.CurrentPassword) {
		badRequest(w, "Mot de passe actuel incorrect")
		return
	}
	if message := validatePassword(req.NewPassword); message != "" {
		badRequest(w, message)
		return
	}
	if req.NewPassword == req.CurrentPassword {
		badRequest(w, "Le nouveau mot de passe doit être différent de l'actuel")
		return
	}

	newHash, err := auth.HashPassword(req.NewPassword)
	if err != nil {
		httpjson.ServerError(w, err)
		return
	}

	var user userResponse
	err = h.pool.QueryRow(ctx,
		`UPDATE users SET password_hash = $1, must_change_password = false, updated_at = now()
		 WHERE id = $2 RETURNING `+userColumns,
		newHash, userID).Scan(user.scanTargets()...)
	if err != nil {
		httpjson.ServerError(w, err)
		return
	}

	// Ferme les autres sessions (un autre navigateur, un téléphone…), garde celle-ci
	_, err = h.pool.Exec(ctx, `DELETE FROM sessions WHERE user_id = $1 AND token_hash <> $2`,
		userID, auth.HashToken(auth.TokenFromRequest(r)))
	if err != nil {
		httpjson.ServerError(w, err)
		return
	}
	httpjson.Write(w, http.StatusOK, user)
}

// createUser crée l'utilisateur et ses niveaux d'urgence par défaut, dans une seule transaction.
//
// firstAdmin = true (installation) : le compte n'est créé que si la table users est vide
// (sinon errSetupDone), et il est admin. Sinon (console admin) : compte normal, qui devra
// changer le mot de passe choisi par l'admin à sa première connexion.
func createUser(ctx context.Context, pool *pgxpool.Pool, email, name, hash string, firstAdmin bool) (userResponse, error) {
	var user userResponse

	tx, err := pool.Begin(ctx)
	if err != nil {
		return user, err
	}
	defer tx.Rollback(ctx) // Sans effet si le Commit a réussi

	if firstAdmin {
		// Verrou jusqu'à la fin de la transaction : si deux installations arrivent en même temps,
		// la seconde attend que la première soit finie, puis voit qu'un compte existe.
		if _, err = tx.Exec(ctx, `LOCK TABLE users IN EXCLUSIVE MODE`); err != nil {
			return user, err
		}
		var exists bool
		if err = tx.QueryRow(ctx, `SELECT EXISTS (SELECT 1 FROM users)`).Scan(&exists); err != nil {
			return user, err
		}
		if exists {
			return user, errSetupDone
		}
	}

	err = tx.QueryRow(ctx,
		`INSERT INTO users (email, display_name, password_hash, is_admin, must_change_password)
		 VALUES ($1, $2, $3, $4, $5)
		 RETURNING `+userColumns,
		email, name, hash, firstAdmin, !firstAdmin).Scan(user.scanTargets()...)
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

// validateNewUser retourne un message d'erreur, ou "" si tout est valide.
func validateNewUser(email, password, name string) string {
	switch {
	case !strings.Contains(email, "@") || len(email) > 254:
		return "Email invalide"
	case name == "" || utf8.RuneCountInString(name) > 100:
		return "Le nom affiché doit faire entre 1 et 100 caractères"
	}
	return validatePassword(password)
}

// validatePassword retourne un message d'erreur, ou "" si le mot de passe est valide.
func validatePassword(password string) string {
	switch {
	case utf8.RuneCountInString(password) < auth.MinPasswordLength:
		return "Le mot de passe doit faire au moins 8 caractères"
	case len(password) > auth.MaxPasswordBytes:
		return "Le mot de passe est trop long (72 octets maximum)"
	}
	return ""
}

// isUniqueViolation indique si err vient d'une contrainte UNIQUE (code PostgreSQL 23505).
func isUniqueViolation(err error) bool {
	var pgErr *pgconn.PgError
	return errors.As(err, &pgErr) && pgErr.Code == "23505"
}

// validQuickTaskDue : échéance proposée par la Quick task (voir migration 010).
func validQuickTaskDue(due string) bool {
	return due == "none" || due == "today" || due == "tomorrow"
}
