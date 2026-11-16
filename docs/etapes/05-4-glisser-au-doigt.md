# Étape 5-4 : glisser une tâche au doigt

## Objectif

Sur téléphone, changer l'ordre des tâches (ou les envoyer dans un autre groupe) en les **glissant au doigt**, après un appui long :

- **appui long sans bouger**, puis relâcher → la tâche est **sélectionnée** (comme en 5-3) ;
- **appui long puis glisser** → la tâche **se déplace** : une étiquette suit le doigt, la ligne bleue montre où elle arrivera, on relâche pour la déposer.

À 0,5 s, la tâche **se soulève** (ombre, léger agrandissement, petite vibration sur Android) : c'est le signal qu'on peut la relâcher ou la glisser.

Choix validés :
- on peut glisser dans le même groupe **et vers les autres groupes de la liste** :
  - vue sections : on monte ou on descend, la liste défile toute seule près des bords ;
  - vue colonnes : le doigt **gardé contre le bord gauche ou droit** pendant 0,5 s fait passer au groupe voisin ;
- si on glisse une tâche qui fait partie d'une sélection de plusieurs tâches, **toute la sélection se déplace** (comme sur ordinateur) ;
- **pas de glisser dans « Aujourd'hui » / « Planifié »** (l'ordre vient des dates) : l'appui long y sélectionne seulement.

## Fichiers

```
web/src/
  utils/longPress.ts (+ tests)   → nouveaux états armed / dragging / consumed
  utils/dragDrop.ts (+ tests)    → autoScrollSpeed, sideEdge (défilement automatique)
  components/
    TaskCard.tsx, TaskRow.tsx    → tâche soulevée (lifted), glisser, blocage du défilement
    TaskDropZone.tsx             → attribut data-drop-group (retrouver le groupe sous le doigt)
    actions.ts                   → touchDragStart / touchDragMove / touchDragEnd
    Board.tsx, SectionsView.tsx  → transmettent le glisser au doigt (pas SmartView)
    Workspace.tsx                → boucle du glisser, étiquette (touchGhost), dragTaskIds
    Workspace.css                → .lifted, .touch-drag-ghost
```

**Pas de changement d'API.** Le dépôt passe par le même chemin que le glisser à la souris : `setDropTarget` → `dropTask` → `moveTasks` (un PATCH par tâche, l'un après l'autre).

## La machine d'état

```
idle ──down──► pressing ──(500 ms sans bouger)──► armed ──up sans bouger──► consumed (sélection)
                  │                                  │
                  │                                  └──move > 10 px──► dragging ──up──► consumed (dépôt)
                  ├──move > 10 px──► idle    (le doigt fait défiler)
                  └──up avant 500 ms──► idle (simple toucher : le clic passe)

abort() (pointercancel) : retour à idle, un glisser en cours est annulé sans dépôt.
consumed : le clic qui suit le relâchement est ignoré (consumeClick).
```

Différence avec 5-3 : la sélection se fait **au relâchement** (`up` dans l'état `armed`), plus pile à 0,5 s, puisque le doigt peut encore partir en glisser. Les étapes sont des « handlers » donnés par le composant (`LongPressHandlers`) : `onArm`, `onLongPress`, `canDrag`, `onDragStart`, `onDragMove`, `onDragEnd`.

## Empêcher le navigateur de faire défiler

Sans précaution, bouger le doigt fait défiler la page, et le navigateur coupe le geste (`pointercancel`). Chaque tâche écoute donc `touchmove` en **« non passive »** (`addEventListener(..., { passive: false })` dans `componentDidMount`, React ne sait pas le faire) et appelle `preventDefault()` **seulement** quand `longPress.blocksScroll()` est vrai (tâche soulevée ou glissée). Avant l'appui long, le doigt fait défiler normalement.

Au doigt, les événements restent envoyés à la tâche touchée au départ, même quand le doigt s'en éloigne : tout le suivi peut rester dans `TaskCard` / `TaskRow`.

## Côté Workspace : la boucle du glisser

`touchDragStart` met en place l'état `drag` (comme `startTaskDrag` à la souris, via `dragTaskIds`) et lance une boucle `requestAnimationFrame` (`onTouchDragFrame`), environ 60 fois par seconde :

1. `document.elementFromPoint(x, y)` : l'élément sous le doigt. L'étiquette a `pointer-events: none`, elle est ignorée.
2. **Défilement vertical** : la colonne (`.board-column`) ou la vue sections sous le doigt défile de `autoScrollSpeed(y, haut, bas)` pixels (plus le doigt est près du bord, plus c'est rapide).
3. **Vue colonnes** : si le doigt reste contre un bord (`sideEdge`) pendant `AUTO_SCROLL.sideDelay`, la zone défile d'une colonne.
4. **Cible** : `closest('[data-drop-group]')` donne le groupe, `computeDropIndex` l'index entre ses tâches (la même fonction qu'à la souris). `setDropTarget` affiche la ligne bleue.

La boucle tourne même quand le doigt ne bouge pas : c'est ce qui permet le défilement automatique et le passage au groupe voisin. `touchDragEnd(drop)` arrête la boucle, retire l'étiquette, puis `dropTask()` (doigt levé) ou `endTaskDrag()` (geste annulé).

## Lancer et tester

```bash
npm --prefix web test
```

```bash
npm --prefix web run dev
```

Sur un **vrai téléphone** (indispensable pour ce geste) :
1. appui long sans bouger : la tâche se soulève ; relâcher → elle est sélectionnée ;
2. appui long puis glisser vers le haut ou le bas : la ligne bleue suit, relâcher → la tâche change de place ;
3. vue colonnes : glisser jusqu'au bord droit et attendre → le groupe suivant s'affiche, on peut déposer dedans ;
4. vue sections : glisser vers le bas d'une longue liste → elle défile toute seule ;
5. faire défiler la liste normalement (sans appui long) : rien n'est sélectionné ni glissé ;
6. dans « Aujourd'hui », appui long puis glisser : pas de déplacement, la tâche est sélectionnée.

Testé dans le navigateur intégré à 375 × 812 : le navigateur de test ne sait ni garder le doigt posé ni glisser au doigt. Les gestes ont donc été **envoyés depuis la page** (`pointerdown`, attente, `pointermove`, `pointerup`, `click`), comme le glisser-déposer en 3g-2. Résultats : réordonnancement dans un groupe, passage au groupe voisin par le bord en vue colonnes, déplacement vers un autre groupe en vue sections, **ordre retrouvé après rechargement de la page** (donc enregistré par le serveur), appui long sans bouger = sélection, pas de glisser dans « Aujourd'hui ». **Non testé** : le blocage du défilement et les gestes sur un vrai iPhone ou Android.

## Où regarder en cas de problème

| Symptôme | Où regarder |
|---|---|
| La page défile au lieu de glisser la tâche | `onTouchMove` (écoute non passive) et `blocksScroll()` ; l'écoute est posée dans `componentDidMount` sur `rootRef` |
| Le glisser s'arrête tout seul | `pointercancel` → `onPointerCancel` ; le navigateur a pris la main (défilement commencé avant l'appui long ?) |
| Faire défiler sélectionne ou soulève une tâche | `LONG_PRESS.tolerance` / `delay` |
| Pas de ligne bleue | `data-drop-group` sur `TaskDropZone` ; `onTouchDragFrame` (élément sous le doigt) |
| Le groupe voisin ne vient pas (vue colonnes) | `sideEdge`, `AUTO_SCROLL.sideEdge` / `sideDelay` |
| La liste ne défile pas toute seule | `autoScrollSpeed` ; le conteneur cherché est `.board-column` ou `.SectionsView` |
| La tâche revient à sa place après le dépôt | onglet Réseau : les `PATCH /tasks/{id}` (position, group_id) ; `moveTasks` dans `Workspace.tsx` |
