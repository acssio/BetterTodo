package auth

import (
	"crypto/rand"
	"crypto/sha256"
	"encoding/base64"
)

// NewToken génère un jeton de session aléatoire de 32 octets (256 bits), encodé en base64.
// Le jeton est donné au client ; la base ne garde que HashToken(jeton).
func NewToken() string {
	bytes := make([]byte, 32)
	rand.Read(bytes) // Depuis Go 1.24, ne retourne jamais d'erreur
	return base64.RawURLEncoding.EncodeToString(bytes)
}

// HashToken retourne le SHA-256 du jeton : c'est ce qui est stocké dans la table sessions.
//
// Pas besoin de bcrypt ici : le jeton est déjà totalement aléatoire et long,
// il ne peut pas être deviné comme un mot de passe.
func HashToken(token string) []byte {
	sum := sha256.Sum256([]byte(token))
	return sum[:]
}
