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
	sessions  *SessionStore
	apiTokens *APITokenStore
}

// NewMiddleware crée le middleware d'authentification.
func NewMiddleware(sessions *SessionStore, apiTokens *APITokenStore) *Middleware {
	return &Middleware{sessions: sessions, apiTokens: apiTokens}
}

// RequireUser ne laisse passer la requête que si elle porte une session valide
// ou un jeton d'accès personnel valide.
// Le handler suivant récupère l'utilisateur avec UserID(r.Context()).
func (m *Middleware) RequireUser(next http.Handler) http.Handler {
	return m.require(next, true)
}

// RequireSession est comme RequireUser, mais refuse les jetons d'accès personnels (403).
// Pour les routes sensibles : mot de passe, jetons d'accès, console admin.
// Ainsi, un jeton qui fuite ne permet pas de prendre le contrôle du compte.
func (m *Middleware) RequireSession(next http.Handler) http.Handler {
	return m.require(next, false)
}

// require : le travail commun de RequireUser et RequireSession.
func (m *Middleware) require(next http.Handler, allowAPIToken bool) http.Handler {
	return http.HandlerFunc(func(w http.ResponseWriter, r *http.Request) {
		token := TokenFromRequest(r)
		if token == "" {
			httpjson.Error(w, http.StatusUnauthorized, "Connexion requise")
			return
		}

		var userID string
		var err error
		isAPIToken := strings.HasPrefix(token, APITokenPrefix)

		if isAPIToken {
			if !allowAPIToken {
				httpjson.Error(w, http.StatusForbidden, "Un jeton d'accès ne peut pas utiliser cette route")
				return
			}
			userID, err = m.apiTokens.UserID(r.Context(), token)
		} else {
			userID, err = m.sessions.UserID(r.Context(), token)
		}

		if errors.Is(err, ErrInvalidSession) {
			message := "Session invalide ou expirée"
			if isAPIToken {
				message = "Jeton d'accès invalide ou révoqué"
			}
			httpjson.Error(w, http.StatusUnauthorized, message)
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
//   - dans l'en-tête "Authorization: Bearer <jeton>" (apps natives, Postman, serveur MCP) ;
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
