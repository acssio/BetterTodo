package handlers

import (
	"context"
	"fmt"
	"math"
	"net/http"
	"regexp"
	"slices"
	"strings"
	"unicode/utf8"

	"github.com/jackc/pgx/v5"
	"github.com/jackc/pgx/v5/pgconn"

	"bettertodo/api/internal/auth"
	"bettertodo/api/internal/httpjson"
)

// Limites de longueur des champs texte
const (
	maxNameLength  = 100   // Nom de liste, de groupe, de niveau…
	maxTitleLength = 500   // Titre d'une tâche
	maxNotesLength = 10000 // Notes d'une tâche
	maxTagLength   = 50
)

// endPosition : une position plus grande que tout, pour « mettre à la fin »
const endPosition = math.MaxInt32

var (
	uuidPattern  = regexp.MustCompile(`^[0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{12}$`)
	colorPattern = regexp.MustCompile(`^#[0-9A-Fa-f]{6}$`)
	iconPattern  = regexp.MustCompile(`^[a-z0-9-]{1,50}$`) // Nom d'icône Lucide, ex. "graduation-cap"
)

// querier : un pool ou une transaction. Les deux savent exécuter des requêtes.
type querier interface {
	Exec(ctx context.Context, sql string, args ...any) (pgconn.CommandTag, error)
	Query(ctx context.Context, sql string, args ...any) (pgx.Rows, error)
	QueryRow(ctx context.Context, sql string, args ...any) pgx.Row
}

// -----------------------------------------------------------------------------
// Appartenance : chaque requête vérifie que la ressource est à l'utilisateur connecté.
// Chaque requête SQL prend $1 = id de la ressource, $2 = id de l'utilisateur.
// -----------------------------------------------------------------------------

const (
	sqlOwnsFolder  = `SELECT EXISTS (SELECT 1 FROM list_folders WHERE id = $1 AND user_id = $2)`
	sqlOwnsList    = `SELECT EXISTS (SELECT 1 FROM lists WHERE id = $1 AND user_id = $2)`
	sqlOwnsUrgency = `SELECT EXISTS (SELECT 1 FROM urgency_levels WHERE id = $1 AND user_id = $2)`
	sqlOwnsTag     = `SELECT EXISTS (SELECT 1 FROM tags WHERE id = $1 AND user_id = $2)`
	sqlOwnsGroup   = `SELECT EXISTS (SELECT 1 FROM task_groups g JOIN lists l ON l.id = g.list_id
	                                 WHERE g.id = $1 AND l.user_id = $2)`
	sqlOwnsTask = `SELECT EXISTS (SELECT 1 FROM tasks t JOIN task_groups g ON g.id = t.group_id
	                              JOIN lists l ON l.id = g.list_id WHERE t.id = $1 AND l.user_id = $2)`
)

// owns indique si la ressource id appartient à userID (sqlOwns = une des constantes ci-dessus).
func owns(ctx context.Context, q querier, sqlOwns, id, userID string) (bool, error) {
	if !uuidPattern.MatchString(id) {
		return false, nil // Pas un UUID : ne peut appartenir à personne
	}
	var ok bool
	err := q.QueryRow(ctx, sqlOwns, id, userID).Scan(&ok)
	return ok, err
}

// requireOwned vérifie que la ressource de l'URL appartient à l'utilisateur.
// Sinon répond 404 (et non 403 : on ne révèle pas qu'elle existe chez quelqu'un d'autre).
func requireOwned(w http.ResponseWriter, r *http.Request, q querier, sqlOwns, id string) bool {
	ok, err := owns(r.Context(), q, sqlOwns, id, auth.UserID(r.Context()))
	if err != nil {
		httpjson.ServerError(w, err)
		return false
	}
	if !ok {
		notFound(w)
		return false
	}
	return true
}

// requireOwnedRef vérifie un identifiant envoyé dans le corps (folder_id, urgency_id…).
// Sinon répond 400 avec message.
func requireOwnedRef(w http.ResponseWriter, r *http.Request, q querier, sqlOwns, id, message string) bool {
	ok, err := owns(r.Context(), q, sqlOwns, id, auth.UserID(r.Context()))
	if err != nil {
		httpjson.ServerError(w, err)
		return false
	}
	if !ok {
		httpjson.Error(w, http.StatusBadRequest, message)
		return false
	}
	return true
}

// -----------------------------------------------------------------------------
// Réponses et lecture de l'URL
// -----------------------------------------------------------------------------

// pathID lit {id} dans l'URL. S'il n'a pas la forme d'un UUID, répond 404.
func pathID(w http.ResponseWriter, r *http.Request) (string, bool) {
	id := r.PathValue("id")
	if !uuidPattern.MatchString(id) {
		notFound(w)
		return "", false
	}
	return id, true
}

func notFound(w http.ResponseWriter) {
	httpjson.Error(w, http.StatusNotFound, "Introuvable")
}

func badRequest(w http.ResponseWriter, message string) {
	httpjson.Error(w, http.StatusBadRequest, message)
}

// -----------------------------------------------------------------------------
// Validation
// -----------------------------------------------------------------------------

// cleanText enlève les espaces autour et vérifie la longueur (en caractères, pas en octets).
func cleanText(s string, min, max int) (string, bool) {
	s = strings.TrimSpace(s)
	n := utf8.RuneCountInString(s)
	return s, n >= min && n <= max
}

// nameError : message d'erreur commun pour les noms
func nameError(field string) string {
	return fmt.Sprintf("%s doit faire entre 1 et %d caractères", field, maxNameLength)
}

// -----------------------------------------------------------------------------
// Lecture de plusieurs lignes
// -----------------------------------------------------------------------------

// collect lit toutes les lignes avec scan. Retourne une liste vide (et non nil → "null" en JSON) s'il n'y en a pas.
func collect[T any](rows pgx.Rows, scan func(pgx.Row) (T, error)) ([]T, error) {
	defer rows.Close()

	items := []T{}
	for rows.Next() {
		item, err := scan(rows)
		if err != nil {
			return nil, err
		}
		items = append(items, item)
	}
	return items, rows.Err()
}

// -----------------------------------------------------------------------------
// UPDATE partiel (pour les PATCH)
// -----------------------------------------------------------------------------

// update construit un "UPDATE table SET col1 = $1, col2 = $2 WHERE id = $3"
// avec seulement les colonnes envoyées par le client.
type update struct {
	table string
	sets  []string // "name = $1", …
	args  []any
}

func newUpdate(table string) *update {
	return &update{table: table}
}

// set ajoute "column = valeur".
func (u *update) set(column string, value any) {
	u.args = append(u.args, value)
	u.sets = append(u.sets, fmt.Sprintf("%s = $%d", column, len(u.args)))
}

// setExpr ajoute une expression SQL libre. %d est remplacé par le numéro du DERNIER argument ajouté.
func (u *update) setExpr(expr string) {
	u.sets = append(u.sets, fmt.Sprintf(expr, len(u.args)))
}

// exec exécute l'UPDATE sur la ligne id. Ne fait rien si aucune colonne n'a été ajoutée.
func (u *update) exec(ctx context.Context, q querier, id string) error {
	if len(u.sets) == 0 {
		return nil
	}

	args := append(u.args, id)
	sql := fmt.Sprintf("UPDATE %s SET %s WHERE id = $%d", u.table, strings.Join(u.sets, ", "), len(args))
	_, err := q.Exec(ctx, sql, args...)
	return err
}

// -----------------------------------------------------------------------------
// Ordre d'affichage (glisser-déposer)
// -----------------------------------------------------------------------------

// ordering décrit comment sont ordonnés les éléments d'une table.
type ordering struct {
	table    string // Table, ex. "tasks"
	column   string // Colonne d'ordre : "position" (ou "rank" pour les niveaux d'urgence)
	siblings string // Requête qui retourne les id des « frères », triés dans l'ordre actuel
}

// move place l'élément movedID à l'index position parmi ses frères, puis renumérote 0, 1, 2…
// Une position trop grande (ex. endPosition) le met à la fin.
//
// Exemple : [A, B, C, D], on déplace A en position 2 :
//
//	sans A    → [B, C, D]
//	insertion → [B, C, A, D]   → positions B=0, C=1, A=2, D=3
func (o ordering) move(ctx context.Context, tx pgx.Tx, siblingsArgs []any, movedID string, position int) error {
	rows, err := tx.Query(ctx, o.siblings, siblingsArgs...)
	if err != nil {
		return err
	}
	ids, err := pgx.CollectRows(rows, pgx.RowTo[string])
	if err != nil {
		return err
	}

	// Retire l'élément déplacé…
	others := make([]string, 0, len(ids))
	for _, id := range ids {
		if id != movedID {
			others = append(others, id)
		}
	}

	// …puis le réinsère à la bonne place
	position = max(0, min(position, len(others)))
	ordered := slices.Insert(others, position, movedID)

	// Renumérote seulement les lignes qui changent (évite de toucher updated_at pour rien)
	sql := fmt.Sprintf("UPDATE %s SET %s = $1 WHERE id = $2 AND %s <> $1", o.table, o.column, o.column)
	for index, id := range ordered {
		if _, err := tx.Exec(ctx, sql, index, id); err != nil {
			return err
		}
	}
	return nil
}
