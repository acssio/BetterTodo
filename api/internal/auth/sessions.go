package auth

import (
	"context"
	"errors"
	"strings"
	"time"

	"github.com/jackc/pgx/v5"
	"github.com/jackc/pgx/v5/pgxpool"
)

// ErrInvalidSession : le jeton n'existe pas ou a expiré.
var ErrInvalidSession = errors.New("session invalide ou expirée")

// SessionStore crée, vérifie et supprime les sessions dans la table sessions.
type SessionStore struct {
	pool     *pgxpool.Pool
	duration time.Duration // Durée de vie d'une nouvelle session
}

// NewSessionStore crée un gestionnaire de sessions.
func NewSessionStore(pool *pgxpool.Pool, duration time.Duration) *SessionStore {
	return &SessionStore{pool: pool, duration: duration}
}

// Create ouvre une session pour userID et retourne le jeton à donner au client.
func (s *SessionStore) Create(ctx context.Context, userID string) (string, time.Time, error) {
	token := NewToken()
	for strings.HasPrefix(token, APITokenPrefix) { // Très rare, mais serait pris pour un jeton d'accès
		token = NewToken()
	}
	expiresAt := time.Now().Add(s.duration)

	// Au passage, on fait le ménage dans les sessions expirées de cet utilisateur
	_, err := s.pool.Exec(ctx, `DELETE FROM sessions WHERE user_id = $1 AND expires_at < now()`, userID)
	if err != nil {
		return "", time.Time{}, err
	}

	_, err = s.pool.Exec(ctx,
		`INSERT INTO sessions (user_id, token_hash, expires_at) VALUES ($1, $2, $3)`,
		userID, HashToken(token), expiresAt)
	if err != nil {
		return "", time.Time{}, err
	}

	return token, expiresAt, nil
}

// UserID retourne l'utilisateur de la session, ou ErrInvalidSession.
func (s *SessionStore) UserID(ctx context.Context, token string) (string, error) {
	var userID string
	err := s.pool.QueryRow(ctx,
		`SELECT user_id FROM sessions WHERE token_hash = $1 AND expires_at > now()`,
		HashToken(token)).Scan(&userID)

	if errors.Is(err, pgx.ErrNoRows) {
		return "", ErrInvalidSession
	}
	return userID, err
}

// Delete ferme la session (déconnexion). Sans effet si elle n'existe pas.
func (s *SessionStore) Delete(ctx context.Context, token string) error {
	_, err := s.pool.Exec(ctx, `DELETE FROM sessions WHERE token_hash = $1`, HashToken(token))
	return err
}
