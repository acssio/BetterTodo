package database

import (
	"context"
	"fmt"
	"io/fs"
	"sort"

	"github.com/jackc/pgx/v5/pgxpool"
)

// Migrator applique les fichiers de migration SQL qui ne l'ont pas encore été.
//
// Fonctionnement :
//  1. la table schema_migrations retient le nom des fichiers déjà appliqués ;
//  2. les fichiers *.sql sont triés par nom (001_…, 002_…) ;
//  3. chaque fichier pas encore appliqué est exécuté dans une transaction,
//     puis son nom est ajouté à schema_migrations.
type Migrator struct {
	pool  *pgxpool.Pool
	files fs.FS // Dossier contenant les *.sql (en pratique migrations.Files)
}

// NewMigrator crée un gestionnaire de migrations.
func NewMigrator(pool *pgxpool.Pool, files fs.FS) *Migrator {
	return &Migrator{pool: pool, files: files}
}

// Run applique les migrations en attente, dans l'ordre.
// Retourne le nom des fichiers appliqués lors de cet appel (vide si la base est à jour).
func (m *Migrator) Run(ctx context.Context) ([]string, error) {
	_, err := m.pool.Exec(ctx, `
		CREATE TABLE IF NOT EXISTS schema_migrations (
			version    TEXT        PRIMARY KEY,
			applied_at TIMESTAMPTZ NOT NULL DEFAULT now()
		)`)
	if err != nil {
		return nil, fmt.Errorf("création de schema_migrations : %w", err)
	}

	names, err := listMigrations(m.files)
	if err != nil {
		return nil, err
	}

	applied := []string{}
	for _, name := range names {
		done, err := m.isApplied(ctx, name)
		if err != nil {
			return applied, err
		}
		if done {
			continue
		}

		if err := m.apply(ctx, name); err != nil {
			return applied, fmt.Errorf("migration %s : %w", name, err)
		}
		applied = append(applied, name)
	}

	return applied, nil
}

// isApplied indique si la migration name est déjà dans schema_migrations.
func (m *Migrator) isApplied(ctx context.Context, name string) (bool, error) {
	var exists bool
	err := m.pool.QueryRow(ctx,
		`SELECT EXISTS (SELECT 1 FROM schema_migrations WHERE version = $1)`, name).Scan(&exists)
	return exists, err
}

// apply exécute une migration dans une transaction : soit tout passe, soit rien n'est modifié.
func (m *Migrator) apply(ctx context.Context, name string) error {
	sql, err := fs.ReadFile(m.files, name)
	if err != nil {
		return err
	}

	tx, err := m.pool.Begin(ctx)
	if err != nil {
		return err
	}
	defer tx.Rollback(ctx) // Sans effet si le Commit a réussi

	// Sans paramètre, pgx utilise le "simple protocol" : un fichier peut contenir plusieurs requêtes
	if _, err := tx.Exec(ctx, string(sql)); err != nil {
		return err
	}
	if _, err := tx.Exec(ctx, `INSERT INTO schema_migrations (version) VALUES ($1)`, name); err != nil {
		return err
	}

	return tx.Commit(ctx)
}

// listMigrations retourne les fichiers *.sql de files, triés par nom.
func listMigrations(files fs.FS) ([]string, error) {
	names, err := fs.Glob(files, "*.sql")
	if err != nil {
		return nil, err
	}
	sort.Strings(names)
	return names, nil
}
