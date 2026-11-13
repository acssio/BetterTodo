package handlers

import (
	"log"
	"net/http"
	"time"
)

// statusRecorder retient le code HTTP écrit par le handler, pour le journaliser.
type statusRecorder struct {
	http.ResponseWriter
	status int
}

func (s *statusRecorder) WriteHeader(status int) {
	s.status = status
	s.ResponseWriter.WriteHeader(status)
}

// LogRequests affiche une ligne par requête : méthode, chemin, code HTTP, durée.
// Exemple : "POST /api/v1/auth/login 200 64ms"
func LogRequests(next http.Handler) http.Handler {
	return http.HandlerFunc(func(w http.ResponseWriter, r *http.Request) {
		start := time.Now()
		recorder := &statusRecorder{ResponseWriter: w, status: http.StatusOK}

		next.ServeHTTP(recorder, r)

		log.Printf("%s %s %d %s", r.Method, r.URL.Path, recorder.status, time.Since(start).Round(time.Millisecond))
	})
}
