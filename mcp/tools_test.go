package main

import "testing"

func TestFindUrgency(t *testing.T) {
	levels := []apiUrgencyLevel{{ID: "a", Name: "Basse"}, {ID: "b", Name: "Haute"}}

	if id, err := findUrgency(levels, "haute"); err != nil || id != "b" {
		t.Errorf("« haute » : obtenu %q, %v", id, err)
	}
	if id, err := findUrgency(levels, " Basse "); err != nil || id != "a" {
		t.Errorf("« Basse » avec espaces : obtenu %q, %v", id, err)
	}
	if _, err := findUrgency(levels, "Critique"); err == nil {
		t.Error("un niveau inconnu doit donner une erreur")
	}
}

func TestFindGroup(t *testing.T) {
	groups := []string{"Tâches", "Plus tard"}

	if got := findGroup(groups, ""); got != 0 {
		t.Errorf("sans nom : obtenu %d, attendu 0 (premier groupe)", got)
	}
	if got := findGroup(groups, "plus tard"); got != 1 {
		t.Errorf("« plus tard » : obtenu %d", got)
	}
	if got := findGroup(groups, "Inconnu"); got != -1 {
		t.Errorf("groupe inconnu : obtenu %d", got)
	}
	if got := findGroup(nil, ""); got != -1 {
		t.Errorf("liste sans groupe : obtenu %d", got)
	}
}

func TestEmptyToNull(t *testing.T) {
	if emptyToNull("") != nil {
		t.Error("\"\" doit devenir nil")
	}
	if emptyToNull("2026-11-20") != "2026-11-20" {
		t.Error("une valeur doit rester la même")
	}
}

func TestToTaskOut(t *testing.T) {
	due := "2026-11-20"
	urgency := "u1"
	task := apiTask{ID: "t1", Title: "Pain", DueDate: &due, UrgencyID: &urgency, Tags: []string{"courses"}}

	out := toTaskOut(task, map[string]string{"u1": "Haute"})
	if out.DueDate != due || out.Urgency != "Haute" || out.Title != "Pain" || len(out.Tags) != 1 {
		t.Errorf("conversion incorrecte : %+v", out)
	}
}

func TestFindFolder(t *testing.T) {
	folders := []apiFolder{{ID: "f1", Name: "Maison"}, {ID: "f2", Name: "Travail"}}

	if folder, err := findFolder(folders, " travail "); err != nil || folder.ID != "f2" || folder.Name != "Travail" {
		t.Errorf("« travail » : obtenu %+v, %v", folder, err)
	}
	if _, err := findFolder(folders, "Loisirs"); err == nil {
		t.Error("un groupe de listes inconnu doit donner une erreur")
	}
	if _, err := findFolder(nil, "Maison"); err == nil {
		t.Error("sans groupe de listes, il doit y avoir une erreur")
	}
}
