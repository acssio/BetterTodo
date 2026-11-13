package database

import (
	"context"
	"os"
	"reflect"
	"testing"
	"testing/fstest"

	"bettertodo/api/migrations"
)

func TestListMigrationsTrieEtFiltre(t *testing.T) {
	files := fstest.MapFS{
		"002_b.sql": {},
		"001_a.sql": {},
		"010_c.sql": {},
		"notes.txt": {}, // Ignoré : pas un .sql
		"embed.go":  {}, // Ignoré
	}

	got, err := listMigrations(files)
	if err != nil {
		t.Fatal(err)
	}

	want := []string{"001_a.sql", "002_b.sql", "010_c.sql"}
	if !reflect.DeepEqual(got, want) {
		t.Errorf("obtenu %v, attendu %v", got, want)
	}
}

// TestMigratorSurVraieBase applique les vraies migrations sur une base de test VIDÉE.
// Lancé seulement si TEST_DATABASE_URL est définie (voir docs/etapes/02a-api-fondations.md).
func TestMigratorSurVraieBase(t *testing.T) {
	url := os.Getenv("TEST_DATABASE_URL")
	if url == "" {
		t.Skip("TEST_DATABASE_URL non définie")
	}

	ctx := context.Background()
	pool, err := Connect(ctx, url)
	if err != nil {
		t.Fatal(err)
	}
	defer pool.Close()

	// Repart d'une base vide
	if _, err := pool.Exec(ctx, `DROP SCHEMA public CASCADE; CREATE SCHEMA public;`); err != nil {
		t.Fatal(err)
	}

	migrator := NewMigrator(pool, migrations.Files)

	applied, err := migrator.Run(ctx)
	if err != nil {
		t.Fatalf("1er passage : %v", err)
	}
	if len(applied) < 2 {
		t.Fatalf("1er passage : %v appliquées, attendu au moins 001 et 002", applied)
	}

	// Un 2e passage ne doit rien refaire
	applied, err = migrator.Run(ctx)
	if err != nil {
		t.Fatalf("2e passage : %v", err)
	}
	if len(applied) != 0 {
		t.Errorf("2e passage : %v appliquées, attendu aucune", applied)
	}

	// Les tables principales existent
	for _, table := range []string{"users", "lists", "tasks", "sessions"} {
		var exists bool
		pool.QueryRow(ctx, `SELECT to_regclass($1) IS NOT NULL`, table).Scan(&exists)
		if !exists {
			t.Errorf("table %s absente", table)
		}
	}
}
