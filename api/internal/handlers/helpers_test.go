package handlers

import (
	"encoding/json"
	"reflect"
	"testing"
)

func TestOptionalTroisCas(t *testing.T) {
	var req struct {
		A Optional[string] `json:"a"`
		B Optional[string] `json:"b"`
		C Optional[string] `json:"c"`
	}
	if err := json.Unmarshal([]byte(`{"b": null, "c": "valeur"}`), &req); err != nil {
		t.Fatal(err)
	}

	if req.A.Set {
		t.Error("a absent : Set doit être false")
	}
	if !req.B.Set || req.B.Value != nil || !req.B.IsNull() {
		t.Error("b à null : Set = true, Value = nil")
	}
	if !req.C.Set || req.C.Value == nil || *req.C.Value != "valeur" {
		t.Error("c : Set = true, Value = \"valeur\"")
	}
}

func TestOptionalMauvaisType(t *testing.T) {
	var req struct {
		N Optional[int] `json:"n"`
	}
	if err := json.Unmarshal([]byte(`{"n": "pas un nombre"}`), &req); err == nil {
		t.Error("une chaîne dans un Optional[int] doit donner une erreur")
	}
}

func TestNormalizeTags(t *testing.T) {
	got, message := normalizeTags([]string{" courses ", "#maison", "courses", "Maison"})
	if message != "" {
		t.Fatal(message)
	}
	want := []string{"courses", "maison", "Maison"} // "#" et espaces enlevés, doublon exact retiré
	if !reflect.DeepEqual(got, want) {
		t.Errorf("obtenu %v, attendu %v", got, want)
	}

	if _, message := normalizeTags([]string{"   "}); message == "" {
		t.Error("un tag vide doit être refusé")
	}
	if got, _ := normalizeTags(nil); got == nil || len(got) != 0 {
		t.Error("aucun tag : liste vide (et non nil)")
	}
}

func TestValidDate(t *testing.T) {
	cases := map[string]bool{
		"2026-11-18": true,
		"2028-02-29": true,  // Année bissextile
		"2026-02-31": false, // N'existe pas
		"18.11.2026": false,
		"":           false,
	}
	for input, want := range cases {
		if got := validDate(input); got != want {
			t.Errorf("validDate(%q) = %v, attendu %v", input, got, want)
		}
	}
}

func TestUpdateBuilder(t *testing.T) {
	u := newUpdate("tasks")
	u.set("title", "x")
	u.set("done", true)
	u.setExpr("done_at = CASE WHEN $%d THEN now() END")

	want := []string{"title = $1", "done = $2", "done_at = CASE WHEN $2 THEN now() END"}
	if !reflect.DeepEqual(u.sets, want) {
		t.Errorf("obtenu %v, attendu %v", u.sets, want)
	}
}

func TestPatternsCouleurEtIcone(t *testing.T) {
	if !colorPattern.MatchString("#E5484D") || colorPattern.MatchString("rouge") || colorPattern.MatchString("#FFF") {
		t.Error("colorPattern")
	}
	if !iconPattern.MatchString("graduation-cap") || iconPattern.MatchString("Briefcase") || iconPattern.MatchString("a b") {
		t.Error("iconPattern")
	}
}
