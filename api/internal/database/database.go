// Package database gère la connexion à PostgreSQL et les migrations du schéma.
package database

import (
	"context"
	"fmt"

	"github.com/jackc/pgx/v5/pgxpool"
)

// Connect ouvre un pool de connexions vers PostgreSQL et vérifie qu'il répond.
//
// Un pool garde quelques connexions ouvertes et les prête aux requêtes HTTP :
// on ne paie pas le coût d'une nouvelle connexion à chaque requête.
func Connect(ctx context.Context, url string) (*pgxpool.Pool, error) {
	pool, err := pgxpool.New(ctx, url)
	if err != nil {
		return nil, fmt.Errorf("configuration de la connexion : %w", err)
	}

	if err := pool.Ping(ctx); err != nil {
		pool.Close()
		return nil, fmt.Errorf("connexion à PostgreSQL : %w", err)
	}

	return pool, nil
}
