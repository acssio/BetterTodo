// Package httpjson regroupe les petites fonctions pour lire et écrire du JSON en HTTP.
package httpjson

import (
	"encoding/json"
	"log"
	"net/http"
	"strings"
)

const maxBodySize = 1 << 20 // 1 Mo : largement assez pour une tâche, et protège le serveur

// Write envoie v en JSON avec le code HTTP status.
func Write(w http.ResponseWriter, status int, v any) {
	w.Header().Set("Content-Type", "application/json; charset=utf-8")
	w.WriteHeader(status)
	if err := json.NewEncoder(w).Encode(v); err != nil {
		log.Printf("écriture de la réponse JSON : %v", err)
	}
}

// Error envoie une erreur au format {"error": "message"}.
func Error(w http.ResponseWriter, status int, message string) {
	Write(w, status, map[string]string{"error": message})
}

// ServerError journalise err et répond 500 sans donner de détail au client.
func ServerError(w http.ResponseWriter, err error) {
	log.Printf("erreur interne : %v", err)
	Error(w, http.StatusInternalServerError, "Erreur interne du serveur")
}

// Read décode le corps JSON de la requête dans v.
// En cas de problème, répond directement au client et retourne false.
//
// Exiger "Content-Type: application/json" protège aussi contre les formulaires
// HTML envoyés depuis un autre site (attaque CSRF) : un formulaire ne peut pas
// envoyer ce type de contenu.
func Read(w http.ResponseWriter, r *http.Request, v any) bool {
	if !strings.HasPrefix(r.Header.Get("Content-Type"), "application/json") {
		Error(w, http.StatusUnsupportedMediaType, "Le corps doit être en JSON (Content-Type: application/json)")
		return false
	}

	r.Body = http.MaxBytesReader(w, r.Body, maxBodySize)
	decoder := json.NewDecoder(r.Body)
	decoder.DisallowUnknownFields() // Un champ mal orthographié donne une erreur au lieu d'être ignoré

	if err := decoder.Decode(v); err != nil {
		Error(w, http.StatusBadRequest, "JSON invalide : "+err.Error())
		return false
	}
	return true
}
