package handlers

import "encoding/json"

// Optional distingue les trois cas d'un champ JSON dans une requête PATCH :
//
//	champ absent        → Set = false              (on ne touche pas à la valeur)
//	"champ": null       → Set = true, Value = nil  (on efface la valeur)
//	"champ": 42         → Set = true, Value = &42  (on change la valeur)
//
// Avec un simple pointeur, "absent" et "null" donneraient tous les deux nil.
type Optional[T any] struct {
	Set   bool
	Value *T
}

// UnmarshalJSON n'est appelé que si le champ est présent dans le JSON (même s'il vaut null).
func (o *Optional[T]) UnmarshalJSON(data []byte) error {
	o.Set = true
	if string(data) == "null" {
		o.Value = nil
		return nil
	}

	var value T
	if err := json.Unmarshal(data, &value); err != nil {
		return err
	}
	o.Value = &value
	return nil
}

// IsNull indique que le champ a été envoyé explicitement à null.
func (o Optional[T]) IsNull() bool {
	return o.Set && o.Value == nil
}
