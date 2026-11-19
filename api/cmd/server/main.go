// Point d'entrée du serveur API de BetterTodo.
//
// Au démarrage : lecture de la config → connexion à PostgreSQL → migrations →
// création des routes → écoute HTTP. S'arrête proprement sur Ctrl+C ou "docker stop".
//
// "bettertodo-api healthcheck" sert au healthcheck Docker : il interroge le serveur
// local et quitte avec le code 0 (OK) ou 1 (problème).
package main

import (
	"context"
	"errors"
	"log"
	"net/http"
	"os"
	"os/signal"
	"syscall"
	"time"

	"bettertodo/api/internal/auth"
	"bettertodo/api/internal/config"
	"bettertodo/api/internal/database"
	"bettertodo/api/internal/handlers"
	"bettertodo/api/migrations"
)

func main() {
	if len(os.Args) > 1 && os.Args[1] == "healthcheck" {
		os.Exit(healthcheck())
	}

	cfg, err := config.Load()
	if err != nil {
		log.Fatalf("configuration : %v", err)
	}

	ctx := context.Background()

	// Base de données ----------------------------------------------------------
	pool, err := database.Connect(ctx, cfg.DatabaseURL)
	if err != nil {
		log.Fatalf("base de données : %v", err)
	}
	defer pool.Close()

	applied, err := database.NewMigrator(pool, migrations.Files).Run(ctx)
	if err != nil {
		log.Fatalf("migrations : %v", err)
	}
	if len(applied) > 0 {
		log.Printf("migrations appliquées : %v", applied)
	} else {
		log.Printf("base de données à jour")
	}

	// Objets de l'application --------------------------------------------------
	sessions := auth.NewSessionStore(pool, cfg.SessionDuration)
	authMiddleware := auth.NewMiddleware(sessions, auth.NewAPITokenStore(pool))
	requireUser := authMiddleware.RequireUser       // Session ou jeton d'accès personnel
	requireSession := authMiddleware.RequireSession // Session seulement (routes sensibles)

	healthHandler := handlers.NewHealthHandler(pool)
	authHandler := handlers.NewAuthHandler(pool, sessions, cfg.CookieSecure)
	folderHandler := handlers.NewFolderHandler(pool)
	listHandler := handlers.NewListHandler(pool)
	groupHandler := handlers.NewGroupHandler(pool)
	taskHandler := handlers.NewTaskHandler(pool)
	urgencyHandler := handlers.NewUrgencyHandler(pool)
	tagHandler := handlers.NewTagHandler(pool)
	dueHandler := handlers.NewDueHandler(pool)
	adminHandler := handlers.NewAdminHandler(pool)
	tokenHandler := handlers.NewTokenHandler(pool)
	smartListHandler := handlers.NewSmartListHandler(pool)

	// Routes -------------------------------------------------------------------
	mux := http.NewServeMux()

	// Publiques
	mux.HandleFunc("GET /api/v1/health", healthHandler.Check)
	mux.HandleFunc("GET /api/v1/auth/setup", authHandler.SetupStatus) // Premier lancement ?
	mux.HandleFunc("POST /api/v1/auth/setup", authHandler.Setup)      // Crée le premier compte (admin)
	mux.HandleFunc("POST /api/v1/auth/login", authHandler.Login)

	// Connexion requise : private(route, handler) passe d'abord par requireUser.
	// Session ou jeton d'accès personnel (serveur MCP, scripts…).
	private := func(route string, handler http.HandlerFunc) {
		mux.Handle(route, requireUser(handler))
	}

	// Routes sensibles : session seulement, un jeton d'accès est refusé (403)
	sessionOnly := func(route string, handler http.HandlerFunc) {
		mux.Handle(route, requireSession(handler))
	}

	sessionOnly("POST /api/v1/auth/logout", authHandler.Logout)
	private("GET /api/v1/me", authHandler.Me)
	private("PATCH /api/v1/me", authHandler.UpdateMe)
	sessionOnly("POST /api/v1/me/password", authHandler.ChangePassword)

	sessionOnly("GET /api/v1/me/tokens", tokenHandler.List)
	sessionOnly("POST /api/v1/me/tokens", tokenHandler.Create)
	sessionOnly("DELETE /api/v1/me/tokens/{id}", tokenHandler.Delete)

	private("GET /api/v1/folders", folderHandler.List)
	private("POST /api/v1/folders", folderHandler.Create)
	private("PATCH /api/v1/folders/{id}", folderHandler.Update)
	private("DELETE /api/v1/folders/{id}", folderHandler.Delete)

	private("GET /api/v1/lists", listHandler.List)
	private("POST /api/v1/lists", listHandler.Create)
	private("PATCH /api/v1/lists/{id}", listHandler.Update)
	private("DELETE /api/v1/lists/{id}", listHandler.Delete)
	private("GET /api/v1/lists/{id}/board", listHandler.Board)
	private("DELETE /api/v1/lists/{id}/done-tasks", listHandler.ClearDone)

	private("POST /api/v1/lists/{id}/groups", groupHandler.Create)
	private("PATCH /api/v1/groups/{id}", groupHandler.Update)
	private("DELETE /api/v1/groups/{id}", groupHandler.Delete)

	private("POST /api/v1/groups/{id}/tasks", taskHandler.Create)
	// Listes intelligentes (plus précises que /tasks/{id} : le routeur les choisit en priorité)
	private("GET /api/v1/tasks/due", dueHandler.List)
	private("GET /api/v1/tasks/due/counts", dueHandler.Counts)
	private("GET /api/v1/tasks/{id}", taskHandler.Get)
	private("PATCH /api/v1/tasks/{id}", taskHandler.Update)
	private("DELETE /api/v1/tasks/{id}", taskHandler.Delete)

	private("GET /api/v1/urgency-levels", urgencyHandler.List)
	private("POST /api/v1/urgency-levels", urgencyHandler.Create)
	private("PATCH /api/v1/urgency-levels/{id}", urgencyHandler.Update)
	private("DELETE /api/v1/urgency-levels/{id}", urgencyHandler.Delete)

	// Listes intelligentes personnalisées (par tags ou par urgence)
	private("GET /api/v1/smart-lists", smartListHandler.List)
	private("POST /api/v1/smart-lists", smartListHandler.Create)
	private("PATCH /api/v1/smart-lists/{id}", smartListHandler.Update)
	private("DELETE /api/v1/smart-lists/{id}", smartListHandler.Delete)
	private("GET /api/v1/smart-lists/{id}/tasks", smartListHandler.Tasks)

	private("GET /api/v1/tags", tagHandler.List)
	private("POST /api/v1/tags", tagHandler.Create)
	private("PATCH /api/v1/tags/{id}", tagHandler.Update)
	private("DELETE /api/v1/tags/{id}", tagHandler.Delete)

	// Console admin : session requise (pas de jeton d'accès), puis compte admin requis
	admin := func(route string, handler http.HandlerFunc) {
		mux.Handle(route, requireSession(adminHandler.RequireAdmin(handler)))
	}

	admin("GET /api/v1/admin/users", adminHandler.ListUsers)
	admin("POST /api/v1/admin/users", adminHandler.CreateUser)
	admin("PATCH /api/v1/admin/users/{id}", adminHandler.ResetPassword)
	admin("DELETE /api/v1/admin/users/{id}", adminHandler.DeleteUser)

	server := &http.Server{
		Addr:              cfg.ListenAddr,
		Handler:           handlers.LogRequests(mux),
		ReadHeaderTimeout: 5 * time.Second, // Évite qu'un client lent bloque une connexion
	}

	// Démarrage et arrêt propre ------------------------------------------------
	go func() {
		log.Printf("serveur API à l'écoute sur %s", cfg.ListenAddr)
		if err := server.ListenAndServe(); err != nil && !errors.Is(err, http.ErrServerClosed) {
			log.Fatalf("serveur HTTP : %v", err)
		}
	}()

	stop := make(chan os.Signal, 1)
	signal.Notify(stop, os.Interrupt, syscall.SIGTERM) // Ctrl+C ou "docker stop"
	<-stop

	log.Printf("arrêt du serveur…")
	shutdownCtx, cancel := context.WithTimeout(ctx, 10*time.Second)
	defer cancel()
	if err := server.Shutdown(shutdownCtx); err != nil { // Laisse finir les requêtes en cours
		log.Printf("arrêt : %v", err)
	}
}

// healthcheck interroge /api/v1/health sur ce conteneur. Retourne 0 si OK, 1 sinon.
func healthcheck() int {
	addr := os.Getenv("LISTEN_ADDR")
	if addr == "" {
		addr = ":8080"
	}

	client := http.Client{Timeout: 3 * time.Second}
	resp, err := client.Get("http://127.0.0.1" + addr + "/api/v1/health")
	if err != nil {
		return 1
	}
	defer resp.Body.Close()

	if resp.StatusCode != http.StatusOK {
		return 1
	}
	return 0
}
