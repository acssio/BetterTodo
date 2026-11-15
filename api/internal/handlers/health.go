package handlers

import (
	"net/http"

	"github.com/jackc/pgx/v5/pgxpool"

	"bettertodo/api/internal/httpjson"
)

// HealthHandler répond si le serveur et la base fonctionnent.
type HealthHandler struct {
	pool *pgxpool.Pool
}

// NewHealthHandler crée le handler de santé.
func NewHealthHandler(pool *pgxpool.Pool) *HealthHandler {
	return &HealthHandler{pool: pool}
}

// Check : GET /api/v1/health → {"status": "ok"}, ou 503 si la base ne répond pas.
func (h *HealthHandler) Check(w http.ResponseWriter, r *http.Request) {
	if err := h.pool.Ping(r.Context()); err != nil {
		httpjson.Error(w, http.StatusServiceUnavailable, "Base de données injoignable")
		return
	}
	httpjson.Write(w, http.StatusOK, map[string]string{"status": "ok"})
}
