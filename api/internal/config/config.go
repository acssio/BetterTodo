// Package config lit la configuration du serveur dans les variables d'environnement.
package config

import (
	"errors"
	"os"
	"strconv"
	"time"
)

// Config regroupe tous les réglages du serveur.
type Config struct {
	DatabaseURL     string        // Adresse de PostgreSQL : postgres://user:mdp@hote:5432/base
	ListenAddr      string        // Adresse d'écoute HTTP, ex. ":8080"
	SessionDuration time.Duration // Durée de vie d'une session de connexion
	CookieSecure    bool          // true = le cookie de session n'est envoyé qu'en HTTPS
}

// Load lit la configuration. DATABASE_URL est obligatoire, le reste a une valeur par défaut.
func Load() (Config, error) {
	cfg := Config{
		DatabaseURL:  os.Getenv("DATABASE_URL"),
		ListenAddr:   getEnv("LISTEN_ADDR", ":8080"),
		CookieSecure: getEnv("COOKIE_SECURE", "false") == "true",
	}

	if cfg.DatabaseURL == "" {
		return cfg, errors.New("la variable DATABASE_URL est manquante")
	}

	days, err := strconv.Atoi(getEnv("SESSION_DAYS", "30"))
	if err != nil || days <= 0 {
		return cfg, errors.New("SESSION_DAYS doit être un nombre de jours positif")
	}
	cfg.SessionDuration = time.Duration(days) * 24 * time.Hour

	return cfg, nil
}

// getEnv retourne la variable d'environnement key, ou def si elle est vide.
func getEnv(key, def string) string {
	if value := os.Getenv(key); value != "" {
		return value
	}
	return def
}
