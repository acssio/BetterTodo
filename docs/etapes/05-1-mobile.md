# Étape 5-1 : le site sur téléphone

## Objectif

Rendre le site agréable sur téléphone, de l'**iPhone 13 mini** (375 × 812) à l'**iPhone 17 Pro Max** (440 × 956), en passant par le **Galaxy Z Flip 6** ouvert (environ 412 × 1000). La maquette est sur la page « BetterTodo Mobile » (écrans 1 à 8 ; le 9, Quick task, viendra en 5-2).

Ce n'est **pas une deuxième application** : c'est la même page React, qui change d'affichage quand l'écran est petit. Toutes les données, les appels à l'API et les modifications « optimistes » restent dans `Workspace`, exactement comme sur ordinateur.

Ce qui change en mode mobile :

| Ordinateur | Téléphone |
|---|---|
| Barre latérale + liste | **Un écran à la fois** : Accueil → Liste, flèche ‹ pour revenir |
| Toutes les colonnes côte à côte | **Une colonne pleine largeur**, on glisse le doigt ou on touche un **onglet** |
| Clic = modifier le titre, bouton au survol = overlay | **Toucher une tâche ouvre l'overlay** |
| Overlay à côté de la tâche | **Panneau qui monte du bas**, au-dessus du clavier |
| Paramètres en fenêtre | Paramètres **en plein écran** |
| Vue scindée, glisser-déposer | Pas sur téléphone (le déplacement viendra en 5-3 avec « Déplacer ») |

## Fichiers

```
web/index.html                      → viewport-fit=cover (pour les marges de l'encoche)
web/src/
  index.css                         → champs en 16 px sur téléphone (sinon iOS zoome)
  utils/mobileLayout.ts (+ tests)   → isMobileLayout, detectMobileLayout, keyboardInset
  components/
    MobileHome.tsx / .css           → NOUVEAU : écran d'accueil (remplace la barre latérale)
    Workspace.tsx                   → état mobile + mobileScreen, renderMobile, historique
    Workspace.css                   → règles .Workspace.mobile (tâches, vue sections, plein écran…)
    ListHeader.tsx / .css           → flèche ‹ (BackButton, prop onBack)
    SmartView.tsx                   → props mobile et onBack
    Board.tsx / .css                → onglets des groupes, une colonne à la fois (scroll-snap)
    SectionsView.tsx                → transmet tapToOpen
    TaskCard.tsx, TaskRow.tsx       → prop tapToOpen
    TaskPopover.tsx / .css          → prop sheet : panneau du bas, remonté au-dessus du clavier
    actions.ts                      → TaskViewProps.mobile
```

## Quand passe-t-on en mode mobile ?

`isMobileLayout(largeur, hauteur, doigt)` dans `utils/mobileLayout.ts` :

- fenêtre de **moins de 600 px** de large → mobile (téléphone en portrait, ou fenêtre d'ordinateur très étroite) ;
- **écran tactile** de **moins de 500 px** de haut → mobile (téléphone tenu à l'horizontale) ;
- sinon → affichage ordinateur. Une tablette garde la barre latérale.

« Tactile » se lit avec la media query `(pointer: coarse)`. `Workspace` recalcule le mode à chaque `resize` (rotation du téléphone comprise) et ajoute la classe `mobile` : `<div class="Workspace mobile">`. **Tout le CSS du mode mobile commence par `.Workspace.mobile`**, ce qui permet de le retrouver facilement.

Les mêmes seuils sont dans la media query de `index.css` (champs en 16 px) : les changer ensemble.

## La machine d'état des écrans

```
home ──(toucher une liste ou « Aujourd'hui »)──► list
  ▲                                                 │
  └──────(flèche ‹ ou « retour » du téléphone)──────┘
```

- `mobileScreen` est dans `WorkspaceState`. L'écran `list` affiche la liste de la **moitié active** (`panes[activePane]`) : on réutilise exactement le même mécanisme que l'ordinateur (`openList`, `reloadBoard`…).
- Entrer dans une liste fait `history.pushState(...)`. Le geste **retour** d'Android ou d'iOS revient alors à l'accueil (`popstate` → `onPopState`) au lieu de quitter le site.
- La flèche ‹ fait `history.back()`, qui passe par le même `onPopState` : un seul chemin pour revenir.
- Au chargement, on démarre toujours sur l'accueil.

## L'accueil (`MobileHome`)

Composant d'affichage seulement : il reçoit les listes et appelle les fonctions de `Workspace`, comme la barre latérale. Il contient les tuiles « Aujourd'hui » / « Planifié » (avec leurs compteurs), les groupes de listes repliables (menu « … » : nouvelle liste ici, renommer, supprimer), le « + » de l'en-tête (nouvelle liste, nouveau groupe de listes), puis en bas Console admin (admin seulement), Paramètres et la déconnexion.

## La vue colonnes sur téléphone

- Chaque colonne fait la largeur de l'écran moins les marges : `width: calc(100cqw - 32px)`. `cqw` = 1 % de la largeur du conteneur `.pane` (c'est déjà le conteneur des container queries).
- `scroll-snap-type: x mandatory` sur la zone qui défile et `scroll-snap-align: start` sur les colonnes : quand on lâche le doigt, le navigateur s'arrête **pile sur une colonne**. Pas de bibliothèque.
- Chaque colonne défile seule à la verticale (`overflow-y: auto`), donc changer de groupe ne garde pas la position d'un groupe plus long.
- **Onglets** : `BoardState.current` = index de la colonne affichée, calculé dans `updateEdges` (`scrollLeft / largeur d'une colonne`). Toucher un onglet appelle `scrollToColumn(index)`. L'onglet allumé reste visible (`scrollIntoView`).
- « Nouveau groupe » (bouton + de l'en-tête) fait défiler jusqu'au champ, à la fin.

## Toucher une tâche, le panneau du bas

- `TaskCard` et `TaskRow` ont une prop `tapToOpen`. Si elle est vraie, toucher la tâche appelle `onOpen` au lieu de modifier le titre, et la tâche n'est pas `draggable`.
- `TaskPopover` reçoit `sheet`. Il ne se place plus à côté de la tâche : il est collé en bas (`left: 0; right: 0`), avec une petite barre en haut et un fond grisé. Toucher le fond le ferme (c'est un clic en dehors : `DismissListener`, comme avant).
- **Le clavier** : sur téléphone, il ne réduit pas `window.innerHeight`, seulement la partie visible (`window.visualViewport`). `keyboardInset()` calcule la différence, et le panneau se met à `bottom: <hauteur du clavier>`.

## Les tailles d'écran

- `height: 100dvh` : la hauteur **réellement visible**, barres du navigateur comprises (`100vh` déborde sous la barre d'adresse sur iPhone).
- `viewport-fit=cover` + `env(safe-area-inset-*)` : marges pour l'encoche (en haut), la barre de geste (en bas) et les côtés quand le téléphone est à l'horizontale.
- Champs en **16 px minimum** : en dessous, iOS zoome sur toute la page quand on touche un champ.
- Zones à toucher d'au moins 44 px : boutons de l'en-tête, onglets, lignes de l'accueil ; la case à cocher (20 px) a une zone invisible plus grande (`.card-check::after`).
- La vue sections passe sur deux lignes : titre + urgence, puis liste d'origine + échéance. C'est fait en CSS seulement (`flex-wrap` + `order`, `.row-spacer` sert de retour à la ligne).

## Lancer et tester

```bash
npm --prefix web test
```

```bash
npm --prefix web run dev
```

Dans le navigateur, ouvrir les outils de développement, mode « appareil » (Chrome : Cmd+Maj+M), et choisir une largeur de 375, 412 ou 440 px. Pour essayer sur un vrai téléphone, le site doit être joignable depuis le réseau (voir `docker-compose.yml`, port 8000).

À vérifier :
1. l'accueil s'affiche à la place de la barre latérale ;
2. toucher une liste l'ouvre ; ‹ et le geste « retour » reviennent à l'accueil ;
3. vue colonnes : glisser passe d'un groupe à l'autre, l'onglet suit ; toucher un onglet y va ;
4. toucher une tâche ouvre le panneau du bas ; toucher le fond le ferme ;
5. dans le panneau, toucher les notes : le panneau reste au-dessus du clavier (vrai téléphone seulement) ;
6. sur ordinateur, rien n'a changé.

Testé dans le navigateur intégré, avec de vrais clics, à 375 × 812, 412 × 1004 et 440 × 956, puis en taille ordinateur. **Pas testé** : le téléphone à l'horizontale (le navigateur de test ne simule un écran tactile qu'en dessous de 768 px de large ; la règle est couverte par les tests Vitest), et le clavier d'un vrai téléphone. Un essai sur iPhone et sur le Flip reste à faire.

## Où regarder en cas de problème

| Symptôme | Où regarder |
|---|---|
| Le mode mobile ne s'active pas (ou s'active sur tablette) | `isMobileLayout` et ses tests ; `matchMedia('(pointer: coarse)')` dans la console |
| Le geste « retour » quitte le site | `showMobileList` (pushState) et `onPopState` dans `Workspace.tsx` |
| Une colonne s'arrête entre deux groupes | `scroll-snap-type` / `scroll-snap-align` dans `Board.css` ; la largeur `100cqw` suppose que `.pane` est un conteneur (`container: main / inline-size`) |
| L'onglet allumé n'est pas le bon | `columnStep()` et le calcul de `current` dans `Board.updateEdges` |
| Le panneau du bas est caché par le clavier | `onViewportChange` dans `TaskPopover.tsx`, `keyboardInset` |
| iOS zoome quand on touche un champ | media query à la fin de `index.css` : la taille calculée du champ doit être 16 px |
| Contenu sous l'encoche ou la barre de geste | `viewport-fit=cover` dans `index.html`, `env(safe-area-inset-*)` |

## Limites connues

- Pas de glisser-déposer au doigt : réordonner des tâches, et réordonner les niveaux d'urgence dans les Paramètres, ne marchent qu'à la souris. Le déplacement de tâches viendra en 5-3 (« Déplacer »).
- « Modifier la liste » s'ouvre encore en petit overlay (pas en panneau du bas).
