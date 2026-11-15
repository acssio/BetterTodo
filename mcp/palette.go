package main

import "strings"

// Apparence d'une liste : copie des valeurs proposées par la page web
// (web/src/components/listIcons.ts : LIST_COLORS et LIST_ICONS).
//
// Si on ajoute une icône ou une couleur dans la page web, il faut l'ajouter ici aussi :
// TestPaletteSynchroniseeAvecLaPageWeb (palette_test.go) le rappelle.

// listColor : une couleur de la palette, avec un nom pour Claude.
type listColor struct {
	Name string `json:"name"`
	Hex  string `json:"hex"`
}

// listColors : même ordre que LIST_COLORS dans la page web.
var listColors = []listColor{
	{"rouge", "#E5484D"},
	{"orange", "#E8A33D"},
	{"vert", "#3FA66B"},
	{"turquoise", "#0E9F9A"},
	{"bleu", "#2F6FEB"},
	{"violet", "#8E4EC6"},
	{"rose", "#D6409F"},
	{"gris", "#6B6B6B"},
}

// listIcon : une icône Lucide (nom stocké en base) et ses mots-clés en français.
type listIcon struct {
	Name  string `json:"name"`
	Label string `json:"label"`
}

// listIcons : même ordre que LIST_ICONS dans la page web (136 icônes).
var listIcons = []listIcon{
	// Général
	{"list", "liste"},
	{"list-checks", "liste cases tâches"},
	{"check", "valider fait"},
	{"star", "étoile favori"},
	{"flag", "drapeau objectif"},
	{"bookmark", "marque-page signet"},
	{"inbox", "boîte de réception"},
	{"archive", "archive carton"},
	{"pin", "épingle punaise"},
	{"zap", "éclair rapide urgent"},
	{"target", "cible objectif"},
	{"trophy", "trophée victoire"},
	{"rocket", "fusée lancement"},
	{"sparkles", "étincelles magie nouveau"},
	{"bell", "cloche rappel notification"},
	{"clock", "horloge heure"},
	{"calendar", "calendrier agenda"},
	{"hourglass", "sablier attente"},
	{"tag", "étiquette"},
	{"folder", "dossier"},
	// Personnel
	{"user", "personne perso"},
	{"users", "personnes groupe équipe famille"},
	{"baby", "bébé enfant"},
	{"heart", "cœur amour"},
	{"smile", "sourire humeur"},
	{"gift", "cadeau anniversaire"},
	{"cake", "gâteau anniversaire fête"},
	{"party-popper", "fête soirée"},
	{"dog", "chien animal"},
	{"cat", "chat animal"},
	{"paw-print", "patte animal"},
	{"flower-2", "fleur"},
	// Maison
	{"house", "maison"},
	{"sofa", "canapé salon meubles"},
	{"bed", "lit chambre dormir"},
	{"bath", "bain salle de bain"},
	{"lamp", "lampe"},
	{"key", "clé"},
	{"wrench", "clé outil réparation bricolage"},
	{"hammer", "marteau bricolage"},
	{"paintbrush", "pinceau peinture"},
	{"shovel", "pelle jardin"},
	{"sprout", "pousse jardin plante"},
	{"trash-2", "poubelle déchets ménage"},
	{"washing-machine", "machine à laver lessive"},
	{"plug", "prise électricité"},
	// Travail et études
	{"briefcase", "travail mallette"},
	{"building-2", "bureau entreprise immeuble"},
	{"presentation", "présentation réunion"},
	{"chart-line", "graphique courbe stats"},
	{"chart-pie", "camembert graphique stats"},
	{"file-text", "document fichier texte"},
	{"clipboard-list", "presse-papiers liste"},
	{"mail", "mail courriel email"},
	{"phone", "téléphone appel"},
	{"printer", "imprimante"},
	{"lightbulb", "idée ampoule projet"},
	{"graduation-cap", "études école cours diplôme"},
	{"book-open", "livre lecture"},
	{"library", "bibliothèque livres"},
	{"pencil", "crayon écrire"},
	{"notebook-pen", "carnet notes"},
	{"languages", "langues traduction"},
	{"calculator", "calculatrice maths"},
	// Tech
	{"code", "code programmation dev"},
	{"terminal", "terminal console"},
	{"bug", "bug insecte"},
	{"git-branch", "git branche"},
	{"server", "serveur"},
	{"database", "base de données"},
	{"cpu", "processeur puce électronique"},
	{"laptop", "ordinateur portable"},
	{"monitor", "écran ordinateur"},
	{"smartphone", "téléphone portable mobile"},
	{"wifi", "wifi réseau internet"},
	{"cloud", "nuage cloud"},
	{"shield", "bouclier sécurité"},
	{"settings", "réglages paramètres engrenage"},
	// Argent et courses
	{"wallet", "argent budget portefeuille"},
	{"piggy-bank", "tirelire épargne"},
	{"credit-card", "carte bancaire paiement"},
	{"banknote", "billet argent"},
	{"receipt", "reçu facture ticket"},
	{"landmark", "banque impôts administration"},
	{"shopping-cart", "courses achats panier"},
	{"shopping-bag", "sac achats shopping"},
	{"store", "magasin boutique"},
	{"package", "colis paquet livraison"},
	// Santé et sport
	{"heart-pulse", "santé cœur pouls"},
	{"pill", "médicament pilule"},
	{"stethoscope", "médecin docteur"},
	{"activity", "activité forme"},
	{"dumbbell", "musculation sport haltère"},
	{"bike", "vélo cyclisme"},
	{"footprints", "marche pas randonnée"},
	{"volleyball", "ballon volley sport"},
	{"brain", "cerveau mental"},
	{"apple", "pomme fruit nutrition"},
	// Cuisine
	{"utensils", "couverts repas restaurant"},
	{"chef-hat", "toque cuisine recette"},
	{"cooking-pot", "marmite cuisine"},
	{"coffee", "café pause"},
	{"pizza", "pizza"},
	{"wine", "vin apéro"},
	{"beer", "bière apéro"},
	{"carrot", "carotte légume"},
	// Voyage et transport
	{"plane", "avion voyage vol"},
	{"car", "voiture"},
	{"train-front", "train"},
	{"bus", "bus"},
	{"ship", "bateau"},
	{"fuel", "essence carburant"},
	{"map", "carte plan"},
	{"map-pin", "lieu adresse"},
	{"compass", "boussole"},
	{"globe", "globe monde"},
	{"luggage", "valise bagages"},
	{"tent", "tente camping"},
	{"mountain", "montagne randonnée"},
	{"palmtree", "palmier vacances plage"},
	{"sun", "soleil été"},
	{"umbrella", "parapluie"},
	// Loisirs
	{"music", "musique"},
	{"headphones", "casque écouter podcast"},
	{"guitar", "guitare"},
	{"film", "film cinéma"},
	{"tv", "télé série"},
	{"gamepad-2", "jeu vidéo manette"},
	{"puzzle", "puzzle jeu"},
	{"camera", "photo appareil"},
	{"palette", "palette peinture art dessin"},
	{"brush", "pinceau art"},
	{"scissors", "ciseaux bricolage couture"},
	{"book", "livre roman"},
	{"newspaper", "journal actualités"},
	{"ticket", "billet ticket concert"},
}

// findColor retourne le code hexadécimal d'une couleur donnée par son nom ("bleu") ou son code ("#2F6FEB").
func findColor(color string) (string, bool) {
	color = strings.TrimSpace(color)
	for _, c := range listColors {
		if strings.EqualFold(color, c.Name) || strings.EqualFold(color, c.Hex) {
			return c.Hex, true
		}
	}
	return "", false
}

// isListIcon indique si name est une icône proposée par la page web.
func isListIcon(name string) bool {
	for _, icon := range listIcons {
		if icon.Name == name {
			return true
		}
	}
	return false
}
