package auth

import (
	"context"
	"errors"

	"github.com/jackc/pgx/v5"
	"github.com/jackc/pgx/v5/pgxpool"
)

// APITokenPrefix : début de tous les jetons d'accès personnels.
// Il permet au middleware de savoir où chercher (api_tokens ou sessions),
// et à l'utilisateur de reconnaître un jeton BetterTodo s'il en trouve un qui traîne.
const APITokenPrefix = "btd_"

// NewAPIToken génère un jeton d'accès personnel : "btd_" + un jeton aléatoire de 256 bits.
func NewAPIToken() string {
	return APITokenPrefix + NewToken()
}

// APITokenStore vérifie les jetons d'accès personnels (table api_tokens).
// Leur création, liste et révocation sont dans handlers.TokenHandler.
type APITokenStore struct {
	pool *pgxpool.Pool
}

// NewAPITokenStore crée le vérificateur de jetons d'accès.
func NewAPITokenStore(pool *pgxpool.Pool) *APITokenStore {
	return &APITokenStore{pool: pool}
}

// UserID retourne l'utilisateur du jeton et note la date d'utilisation,
// ou ErrInvalidSession si le jeton n'existe pas (ou a été révoqué).
func (s *APITokenStore) UserID(ctx context.Context, token string) (string, error) {
	var userID string
	err := s.pool.QueryRow(ctx,
		`UPDATE api_tokens SET last_used_at = now() WHERE token_hash = $1 RETURNING user_id`,
		HashToken(token)).Scan(&userID)

	if errors.Is(err, pgx.ErrNoRows) {
		return "", ErrInvalidSession
	}
	return userID, err
}
