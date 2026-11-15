package handlers

import (
	"net/http"
	"time"

	"github.com/jackc/pgx/v5"
	"github.com/jackc/pgx/v5/pgxpool"

	"bettertodo/api/internal/auth"
	"bettertodo/api/internal/httpjson"
)

// maxAPITokens : nombre maximum de jetons d'accès par compte (évite d'en accumuler sans fin)
const maxAPITokens = 20

// TokenHandler gère les jetons d'accès personnels de l'utilisateur (écran Paramètres).
// Un jeton permet à un programme (serveur MCP pour Claude…) d'utiliser l'API à sa place.
// La vérification d'un jeton à chaque requête est dans auth.APITokenStore.
//
// Ses routes passent par RequireSession : on ne peut pas gérer les jetons… avec un jeton.
type TokenHandler struct {
	pool *pgxpool.Pool
}

// NewTokenHandler crée le handler des jetons d'accès.
func NewTokenHandler(pool *pgxpool.Pool) *TokenHandler {
	return &TokenHandler{pool: pool}
}

// apiToken : un jeton tel que renvoyé au client (jamais le jeton lui-même, sauf à la création).
type apiToken struct {
	ID         string     `json:"id"`
	Name       string     `json:"name"`
	CreatedAt  time.Time  `json:"created_at"`
	LastUsedAt *time.Time `json:"last_used_at"` // null = jamais utilisé
}

// createdAPIToken : réponse de la création, la seule qui contient le jeton en clair.
type createdAPIToken struct {
	apiToken
	Token string `json:"token"`
}

const apiTokenSelect = `SELECT id, name, created_at, last_used_at FROM api_tokens`

func scanAPIToken(row pgx.Row) (apiToken, error) {
	var t apiToken
	err := row.Scan(&t.ID, &t.Name, &t.CreatedAt, &t.LastUsedAt)
	return t, err
}

// List : GET /api/v1/me/tokens → les jetons du compte, du plus récent au plus ancien.
func (h *TokenHandler) List(w http.ResponseWriter, r *http.Request) {
	rows, err := h.pool.Query(r.Context(), apiTokenSelect+` WHERE user_id = $1 ORDER BY created_at DESC`, userOf(r))
	if err != nil {
		httpjson.ServerError(w, err)
		return
	}

	tokens, err := collect(rows, scanAPIToken)
	if err != nil {
		httpjson.ServerError(w, err)
		return
	}
	httpjson.Write(w, http.StatusOK, tokens)
}

type createTokenRequest struct {
	Name string `json:"name"`
}

// Create : POST /api/v1/me/tokens {"name": "..."} → 201 avec le jeton en clair ("token").
// C'est la seule fois où il est envoyé : la base ne garde que son hash.
func (h *TokenHandler) Create(w http.ResponseWriter, r *http.Request) {
	var req createTokenRequest
	if !httpjson.Read(w, r, &req) {
		return
	}

	name, ok := cleanText(req.Name, 1, maxNameLength)
	if !ok {
		badRequest(w, nameError("Le nom du jeton"))
		return
	}

	ctx := r.Context()
	userID := userOf(r)

	var count int
	if err := h.pool.QueryRow(ctx, `SELECT count(*) FROM api_tokens WHERE user_id = $1`, userID).Scan(&count); err != nil {
		httpjson.ServerError(w, err)
		return
	}
	if count >= maxAPITokens {
		badRequest(w, "Trop de jetons d'accès : révoque ceux qui ne servent plus")
		return
	}

	token := auth.NewAPIToken()
	created := createdAPIToken{Token: token}
	err := h.pool.QueryRow(ctx,
		`INSERT INTO api_tokens (user_id, name, token_hash) VALUES ($1, $2, $3)
		 RETURNING id, name, created_at, last_used_at`,
		userID, name, auth.HashToken(token)).
		Scan(&created.ID, &created.Name, &created.CreatedAt, &created.LastUsedAt)
	if err != nil {
		httpjson.ServerError(w, err)
		return
	}
	httpjson.Write(w, http.StatusCreated, created)
}

// Delete : DELETE /api/v1/me/tokens/{id} → révoque le jeton (refusé dès la requête suivante).
func (h *TokenHandler) Delete(w http.ResponseWriter, r *http.Request) {
	id, ok := pathID(w, r)
	if !ok {
		return
	}

	result, err := h.pool.Exec(r.Context(), `DELETE FROM api_tokens WHERE id = $1 AND user_id = $2`, id, userOf(r))
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
