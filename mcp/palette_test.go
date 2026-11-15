package main

import (
	"os"
	"regexp"
	"testing"
)

// TestPaletteSynchroniseeAvecLaPageWeb vérifie que palette.go propose exactement
// les mêmes couleurs et icônes que la page web (web/src/components/listIcons.ts).
func TestPaletteSynchroniseeAvecLaPageWeb(t *testing.T) {
	source, err := os.ReadFile("../web/src/components/listIcons.ts")
	if err != nil {
		t.Skipf("page web introuvable (%v)", err)
	}

	// Icônes : lignes "    'graduation-cap': { icon: GraduationCap, label: '…' },"
	webIcons := regexp.MustCompile(`'([a-z0-9-]+)': \{ icon: `).FindAllSubmatch(source, -1)
	if len(webIcons) != len(listIcons) {
		t.Errorf("la page web propose %d icônes, palette.go %d", len(webIcons), len(listIcons))
	}
	for _, match := range webIcons {
		if !isListIcon(string(match[1])) {
			t.Errorf("icône « %s » de la page web absente de palette.go", match[1])
		}
	}

	// Couleurs : les "#XXXXXX" du fichier (seul LIST_COLORS en contient)
	webColors := regexp.MustCompile(`'(#[0-9A-Fa-f]{6})'`).FindAllSubmatch(source, -1)
	if len(webColors) != len(listColors) {
		t.Errorf("la page web propose %d couleurs, palette.go %d", len(webColors), len(listColors))
	}
	for i, match := range webColors {
		if i < len(listColors) && listColors[i].Hex != string(match[1]) {
			t.Errorf("couleur %d : page web %s, palette.go %s", i, match[1], listColors[i].Hex)
		}
	}
}

func TestFindColor(t *testing.T) {
	if hex, ok := findColor("Bleu"); !ok || hex != "#2F6FEB" {
		t.Errorf("« Bleu » : obtenu %q, %v", hex, ok)
	}
	if hex, ok := findColor("#2f6feb"); !ok || hex != "#2F6FEB" {
		t.Errorf("code en minuscules : obtenu %q, %v", hex, ok)
	}
	if _, ok := findColor("fuchsia"); ok {
		t.Error("une couleur hors palette doit être refusée")
	}
}
