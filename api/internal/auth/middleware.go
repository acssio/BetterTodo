package auth

import (
	"context"
	"errors"
	"net/http"
	"strings"

	"bettertodo/api/internal/httpjson"
)

// CookieName : nom du cookie de session utilisé par la page web.
const CookieName = "bettertodo_session"

// contextKey : clé privée pour ranger l'identifiant de l'utilisateur dans le contexte de la requête.
type contextKey struct{}

// Middleware vérifie qu'une requête vient d'un utilisateur connecté.
type Middleware struct {
	sessions *SessionStore
}

// NewMiddleware crée le middleware d'authentification.
func NewMiddleware(sessions *SessionStore) *Middleware {
	return &Middleware{sessions: sessions}
}

// RequireUser ne laisse passer la requête que si elle porte une session valide.
// Le handler suivant récupère l'utilisateur avec UserID(r.Context()).
func (m *Middleware) RequireUser(next http.Handler) http.Handler {
	return http.HandlerFunc(func(w http.ResponseWriter, r *http.Request) {
		token := TokenFromRequest(r)
		if token == "" {
			httpjson.Error(w, http.StatusUnauthorized, "Connexion requise")
			return
		}

		userID, err := m.sessions.UserID(r.Context(), token)
		if errors.Is(err, ErrInvalidSession) {
			httpjson.Error(w, http.StatusUnauthorized, "Session invalide ou expirée")
			return
		}
		if err != nil {
			httpjson.ServerError(w, err)
			return
		}

		ctx := context.WithValue(r.Context(), contextKey{}, userID)
		next.ServeHTTP(w, r.WithContext(ctx))
	})
}

// TokenFromRequest lit le jeton de session :
//   - dans l'en-tête "Authorization: Bearer <jeton>" (apps natives, Postman) ;
//   - sinon dans le cookie de session (page web).
func TokenFromRequest(r *http.Request) string {
	header := r.Header.Get("Authorization")
	if token, found := strings.CutPrefix(header, "Bearer "); found {
		return token
	}

	if cookie, err := r.Cookie(CookieName); err == nil {
		return cookie.Value
	}
	return ""
}

// UserID retourne l'identifiant de l'utilisateur connecté (rangé par RequireUser).
func UserID(ctx context.Context) string {
	userID, _ := ctx.Value(contextKey{}).(string)
	return userID
}
