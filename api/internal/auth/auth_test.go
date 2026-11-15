package auth

import (
	"bytes"
	"net/http/httptest"
	"strings"
	"testing"
)

func TestHashPasswordEtCheckPassword(t *testing.T) {
	hash, err := HashPassword("correct-horse")
	if err != nil {
		t.Fatalf("HashPassword : %v", err)
	}
	if hash == "correct-horse" {
		t.Fatal("le hash ne doit pas être le mot de passe en clair")
	}
	if !CheckPassword(hash, "correct-horse") {
		t.Error("le bon mot de passe doit être accepté")
	}
	if CheckPassword(hash, "mauvais") {
		t.Error("un mauvais mot de passe doit être refusé")
	}
}

func TestHashPasswordSelAleatoire(t *testing.T) {
	// Deux hash du même mot de passe doivent différer (sel aléatoire)
	a, _ := HashPassword("identique")
	b, _ := HashPassword("identique")
	if a == b {
		t.Error("deux hash du même mot de passe ne doivent pas être identiques")
	}
}

func TestNewTokenUniqueEtLong(t *testing.T) {
	seen := map[string]bool{}
	for i := 0; i < 100; i++ {
		token := NewToken()
		if len(token) != 43 { // 32 octets en base64 sans padding
			t.Fatalf("longueur du jeton : %d, attendu 43", len(token))
		}
		if seen[token] {
			t.Fatal("deux jetons identiques générés")
		}
		seen[token] = true
	}
}

func TestHashToken(t *testing.T) {
	if !bytes.Equal(HashToken("abc"), HashToken("abc")) {
		t.Error("le même jeton doit donner le même hash")
	}
	if bytes.Equal(HashToken("abc"), HashToken("abd")) {
		t.Error("deux jetons différents doivent donner des hash différents")
	}
	if len(HashToken("abc")) != 32 {
		t.Error("un SHA-256 fait 32 octets")
	}
}

func TestTokenFromRequest(t *testing.T) {
	// En-tête Authorization
	r := httptest.NewRequest("GET", "/", nil)
	r.Header.Set("Authorization", "Bearer jeton-entete")
	if got := TokenFromRequest(r); got != "jeton-entete" {
		t.Errorf("en-tête : obtenu %q", got)
	}

	// Cookie
	r = httptest.NewRequest("GET", "/", nil)
	r.Header.Set("Cookie", CookieName+"=jeton-cookie")
	if got := TokenFromRequest(r); got != "jeton-cookie" {
		t.Errorf("cookie : obtenu %q", got)
	}

	// Rien
	r = httptest.NewRequest("GET", "/", nil)
	if got := TokenFromRequest(r); got != "" {
		t.Errorf("sans jeton : obtenu %q", got)
	}
}

func TestNewAPIToken(t *testing.T) {
	token := NewAPIToken()
	if !strings.HasPrefix(token, APITokenPrefix) {
		t.Errorf("le jeton d'accès doit commencer par %q : %q", APITokenPrefix, token)
	}
	if len(token) != len(APITokenPrefix)+43 {
		t.Errorf("longueur du jeton d'accès : %d", len(token))
	}
	if NewAPIToken() == token {
		t.Error("deux jetons d'accès identiques générés")
	}
}
