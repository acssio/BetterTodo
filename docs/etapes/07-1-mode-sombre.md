# Étape 7-1 : barre du haut grise et mode sombre

## Objectif

Deux demandes de `ModifBetterTodo.md`, partie « UI Design » :

- la barre du haut d'une liste avait presque la même couleur que le reste de la page, ce qui alourdissait l'interface : elle est maintenant **grise** (et pas teintée par la couleur de la liste) ;
- un **mode sombre**, au choix **Système / Clair / Sombre** dans les Paramètres.

Maquette : canevas « BetterTodo – Maquettes v3 », page Site (clair à gauche, sombre à droite). Les couleurs sombres sont reprises telles quelles de son `theme.css`.

Choix validés :
- le thème est **retenu par appareil** (`localStorage`), pas sur le compte : pas de migration, pas d'API ;
- « Système » (par défaut) suit le réglage de l'ordinateur ou du téléphone, **même pendant que la page est ouverte**.

## Fichiers

```
web/src/
  index.css                    → nouvelles variables + bloc :root[data-theme="dark"]
  main.tsx                     → theme.start() avant le premier rendu
  utils/theme.ts (+ tests)     → NOUVEAU : parseThemeChoice, resolveTheme, ThemeManager (objet theme)
  components/
    SettingsDialog.tsx         → partie « Apparence » (en premier)
    ListHeader.css             → fond var(--header), gris
    *.css                      → couleurs écrites en dur remplacées par des variables
    Sidebar / MobileHome / LoginPage / ChangePasswordPage .tsx → logo qui suit le thème
    ListEditPopover.tsx / .css → icônes non choisies en currentColor
```

**Ni l'API ni la base n'ont changé.**

## Comment ça marche

```
démarrage ── theme.start() ── localStorage « bettertodo.theme » ──► choix (system / light / dark)
                                 matchMedia(prefers-color-scheme: dark) ──► mode de l'appareil
                                          │
                              resolveTheme(choix, appareilSombre)
                                          │
                              <html data-theme="light|dark">
                                          │
            index.css : :root[data-theme="dark"] { --bg: …; --surface: …; … }
```

- **Tout passe par les variables CSS** de `index.css`. En mode sombre, le bloc `:root[data-theme="dark"]` redéfinit les mêmes variables : aucun composant ne sait qu'il est en sombre.
- `color-scheme: dark` dans ce bloc : les champs natifs (date, listes déroulantes, barres de défilement) passent aussi en sombre.
- `ThemeManager` écoute l'événement `change` de `matchMedia` : en mode « Système », passer le Mac en sombre change la page tout de suite.
- Paramètres › Apparence appelle `theme.setChoice(…)`, qui enregistre le choix et l'applique.

### Nouvelles variables

| Variable | Sert à |
|---|---|
| `--header`, `--header-border` | barre du haut d'une liste (grise) |
| `--border-strong` | survol d'une carte, séparation de la vue scindée, poignée du panneau mobile |
| `--views-bg` | sélecteur Colonnes / Sections (et les choix des Paramètres) |
| `--tag-bg`, `--tag-text` | tags, nom de la liste d'origine |
| `--count-bg` | pastille du nombre de tâches |
| `--check-border` | rond à cocher, poignée de glisser |
| `--placeholder` | « — » quand il n'y a pas d'échéance |
| `--selected`, `--selected-border` | tâche sélectionnée, bouton « allumé » |
| `--drop-over` | colonne survolée pendant un glisser |
| `--danger-bg` | bandeaux d'erreur |
| `--scrollbar`, `--scrollbar-hover` | barre de défilement fine du board |
| `--primary-bg`, `--primary-text` | bouton principal, bouton « Quick task » (clair en mode sombre) |
| `--toast-bg` | barre de la sélection, message en bas (texte blanc dans les deux modes) |
| `--backdrop` | fond grisé derrière une fenêtre |

**Règle pour la suite : plus aucune couleur en dur dans les `.css` des composants**, sauf le texte blanc posé sur un fond coloré (bleu, rouge, `--toast-bg`). Une nouvelle couleur = une variable dans `index.css`, avec sa valeur claire **et** sa valeur sombre.

Les couleurs des listes et des niveaux d'urgence (choisies par l'utilisateur) ne changent pas avec le thème.

## Lancer et tester

```bash
cd web
npm test        # dont utils/theme.test.ts
npx tsc -b
```

Dans le navigateur (`docker compose up -d --build --wait`, puis http://localhost:8000) :

1. Paramètres › Apparence › **Sombre** : toute la page passe en sombre, fenêtre des Paramètres comprise.
2. Recharger la page : le choix est gardé.
3. **Système** : changer le mode du Mac (Réglages › Apparence) ; la page suit sans rechargement.
4. Vérifier en sombre : colonnes, sections, overlay d'une tâche (le champ date doit être sombre), Aujourd'hui / Planifié, sélection (Cmd+clic), accueil et liste en mode mobile.
5. En clair : la barre du haut d'une liste est grise, un peu plus foncée que le fond.

## Où regarder en cas de problème

- **Un élément reste clair en mode sombre** : une couleur écrite en dur. Chercher dans les `.css` :
  ```bash
  grep -n "#[0-9A-Fa-f]\{3,8\}\b" web/src/components/*.css
  ```
  puis la remplacer par une variable (en créer une dans `index.css` si besoin, avec ses deux valeurs).
- **Une icône reste blanche ou noire** : une prop `color="#…"` dans un `.tsx`. Mettre `currentColor` (ou rien : c'est la valeur par défaut de Lucide) et régler `color` dans le `.css`.
- **Le thème ne se garde pas** : navigation privée ou stockage bloqué, `localStorage` refuse d'écrire ; le choix tient alors jusqu'au rechargement. Dans la console : `localStorage.getItem('bettertodo.theme')` et `document.documentElement.dataset.theme`.
- **Bref éclair clair au chargement en mode sombre** : le thème est posé par `main.tsx`, juste avant le premier rendu. Si ça gêne, on pourra le poser plus tôt avec un petit script dans `index.html`.
