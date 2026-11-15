// Package auth gère les mots de passe, les sessions et la vérification de l'utilisateur connecté.
package auth

import "golang.org/x/crypto/bcrypt"

const (
	MinPasswordLength = 8  // Longueur minimale d'un mot de passe
	MaxPasswordBytes  = 72 // Limite de bcrypt : les octets au-delà seraient ignorés
)

// HashPassword retourne le hash bcrypt du mot de passe (sel aléatoire inclus dans le hash).
func HashPassword(password string) (string, error) {
	hash, err := bcrypt.GenerateFromPassword([]byte(password), bcrypt.DefaultCost)
	return string(hash), err
}

// CheckPassword indique si password correspond au hash.
func CheckPassword(hash, password string) bool {
	return bcrypt.CompareHashAndPassword([]byte(hash), []byte(password)) == nil
}
