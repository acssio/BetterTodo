# Étape 6-5b : app Android, glisser une tâche au doigt

## Objectif

Réordonner et déplacer les tâches au doigt, comme le site mobile (étape 5-4) :

- **appui long puis glisser** → la tâche suit le doigt ; **appui long sans bouger** → sélection (au relâchement) ;
- si la tâche fait partie d'une sélection, **toute la sélection** est glissée ;
- une **ligne bleue** montre où les tâches seront déposées ;
- la liste **défile toute seule** près du haut et du bas ;
- **vue colonnes** : doigt gardé 0,5 s contre le bord gauche / droit → groupe voisin ;
- **vue sections** : dépôt dans n'importe quel groupe (sur une tâche, sur le titre = en tête, sur « Ajouter » = à la fin).

Pas de glisser dans Aujourd'hui / Planifié (comme le site), où l'appui long sélectionne toujours. Côté serveur, rien de nouveau : `PATCH /tasks/{id}` avec `group_id` et `position`.

## Choix validé : fait maison

Comme validé le 2026-11-17, j'ai fait le glisser **moi-même**, avec les gestes de Compose, plutôt qu'avec la bibliothèque Reorderable :

- la bibliothèque ne marche qu'à l'intérieur d'une seule liste déroulante ;
- elle ne permet donc ni le passage d'un groupe à l'autre en vue colonnes, ni le glisser de plusieurs tâches à la fois.

## Fichiers

```
app/src/main/java/io/github/acssio/bettertodo/
  ui/DragDrop.kt     ← NOUVEAU, calculs purs : computeDrop (cible sous le doigt), dropAnchor,
                        multiMovePositions (copies du site), autoScrollSpeed, sideEdge
  ui/TaskDrag.kt     ← NOUVEAU : TaskDragState (repères, glisser en cours), Modifier.taskDragGesture (le geste),
                        track / trackScroller (repères), TaskDragLoop (chaque image), TaskDragOverlay (ligne, fantôme)
  ui/BoardModel.kt   ← + dropTasks, reorder, visibleIdsByGroup
  ui/TaskStore.kt    ← + idsToDrag, toggleSelection(id), titleOf
  ui/ListScreen.kt   ← geste sur l'écran, repères sur les tâches et les hauts / bas de groupes
  ui/TaskItem.kt     ← onLongPress facultatif (null dans une liste : le glisser s'en occupe)
  api/TaskChanges.kt ← + position
app/src/test/…       ← + DragDropTest ; BoardModelTest et TaskChangesTest complétés (56 tests)
```

## La machine d'état du geste

```
posé ──(bouge avant 0,5 s : défilement, page voisine — ou levé : toucher)──→ rien, la liste et la tâche s'en occupent
  └──(0,5 s sans bouger : vibration)──→ appui long ──(levé sans bouger)──→ sélection
                                             └──(bouge)──→ glisser ──(levé)──→ dépôt
```

**Le geste est écouté par l'écran de la liste, pas par la tâche.** C'est la leçon du bug du Z Flip sur le site : pendant le défilement automatique, la tâche d'origine sort de l'écran, et la `LazyColumn` la retire de la composition. Un geste écouté sur elle serait perdu en plein glisser. Le test de défilement automatique (25 tâches) l'a vérifié : la tâche d'origine est sortie de l'écran, et le dépôt a quand même marché.

**Comment l'écran passe avant la tâche et la liste** : `Modifier.taskDragGesture` lit les événements **avant** elles (`PointerEventPass.Initial`).

- **Avant 0,5 s**, il ne touche à rien. Le toucher (ouvrir le panneau), la case à cocher, le défilement et le changement de page marchent comme avant.
- **Après l'appui long**, il **consomme** les événements (`consume()`). La liste ne défile plus sous le doigt, et la tâche ne reçoit pas de « toucher » quand le doigt se lève.

## Repères et cible

- **Chaque élément qui peut recevoir un dépôt se déclare** avec `drag.track(...)` : sa place à l'écran est mesurée à chaque placement (`onGloballyPositioned`), et il est retiré quand il quitte l'écran (`DisposableEffect`). Ce sont :
  - les tâches ;
  - le haut d'un groupe (le titre en vue sections, une petite bande en haut de la page en vue colonnes) ;
  - le bas d'un groupe (la ligne « Ajouter une tâche »).
- **À chaque image** (`TaskDragLoop`), `DragDrop.computeDrop` choisit la cible :
  - on garde seulement les éléments de la colonne sous le doigt. En vue colonnes, les pages hors de l'écran ont un rectangle vide et sont ignorées ;
  - on prend l'élément sous le doigt, sinon le plus proche en hauteur ;
  - sur une tâche : avant elle si le doigt est sur sa moitié haute, après sinon.
- **La place est comptée parmi les tâches affichées**, à partir des données du modèle et pas des éléments à l'écran. Sinon, les tâches sorties de l'écran ne compteraient pas.

## Le dépôt

`BoardModel.dropTasks(ids, groupe, place)` :

1. `dropAnchor` (copie du site) transforme la place parmi les tâches affichées en « avant telle tâche ». Les **tâches faites masquées** comptent pour l'API, donc on s'accroche à une vraie tâche du groupe ;
2. `multiMovePositions` (copie du site) calcule la position de chaque tâche, envoyée **l'une après l'autre** : l'API compte la position sans la tâche déplacée ;
3. `reorder` met l'écran à jour tout de suite. Si rien ne change (tâche reposée à sa place), rien n'est envoyé ;
4. un `PATCH {group_id, position}` par tâche ; en cas d'erreur, l'ordre d'avant est remis, puis la liste est rechargée, avec un bandeau.

## Défilement automatique et bords

- **Près du haut ou du bas de la liste** (zone de 56 dp), `dispatchRawDelta` la fait défiler. La vitesse va jusqu'à 6 dp par image : plus le doigt est près du bord, plus ça va vite (`autoScrollSpeed`, copié du site).
- **Vue colonnes** : un doigt gardé contre le bord gauche ou droit (28 dp) pendant 0,5 s passe au groupe voisin. Chaque 0,5 s de plus passe au suivant.

## Lancer et tester

```bash
cd android && ./gradlew test assembleDebug
```

Sur l'émulateur, `adb shell input motionevent DOWN x y`, puis des `MOVE`, puis `UP`, simulent un vrai geste.

### Tests automatiques (56 tests)

- `DragDropTest` :
  - `dropAnchor`, y compris avec des tâches faites masquées et un groupe sans tâche visible ;
  - `multiMovePositions`, dans le même groupe et vers un autre ;
  - `computeDrop` : avant / après une tâche, haut et bas du groupe, sans les tâches glissées, élément le plus proche, colonne sous le doigt seulement, éléments hors de l'écran ignorés ;
  - défilement automatique et bords ;
- `BoardModelTest` : réordonner, déposer plusieurs tâches, reposer à sa place sans rien changer ;
- `TaskChangesTest` : `group_id` + `position`.

### Vérifié sur l'émulateur, avec de vrais gestes, et sur le serveur

- vue colonnes : 3e tâche glissée en tête → ligne bleue, fantôme, tâche d'origine estompée ; ordre à jour sur le serveur (tâche faite masquée à sa place) ;
- rien de cassé :
  - appui long sans bouger → sélection ;
  - toucher pendant une sélection → retire la tâche ;
  - toucher → panneau ;
  - glisser horizontal → page suivante ;
  - case à cocher ;
- 2 tâches sélectionnées glissées contre le bord droit → passage aux groupes suivants, dépôt des deux dans l'ordre, sélection vidée ;
- vue sections : dépôt dans l'autre groupe sous une tâche, et sur un titre (en tête) ;
- 25 tâches : doigt gardé en bas → la liste défile, la tâche est déposée loin de son point de départ, et le geste n'est pas perdu ;
- API arrêtée : l'ordre change, puis revient, avec un bandeau ;
- Planifié : l'appui long sélectionne toujours.

**Ajusté pendant les tests** : le fantôme dépassait de l'écran quand le doigt était près du bord droit. Il est maintenant centré au-dessus du doigt et gardé dans l'écran.

**À tester au vrai doigt**, sur un téléphone : les vitesses (6 dp par image, 0,5 s contre le bord) sont celles du site et pourront être ajustées.

## Où regarder en cas de problème

| Symptôme | Cause probable | Que faire |
|---|---|---|
| Le glisser ne démarre pas | Le doigt a bougé avant 0,5 s (pris pour un défilement) | Normal ; sinon vérifier `taskDragGesture` |
| Toucher une tâche ne l'ouvre plus | Événements consommés trop tôt | Ils ne doivent l'être qu'après l'appui long (boucle « Appui long atteint ») |
| La ligne bleue est au mauvais endroit | Repère manquant ou périmé | Chaque élément a son `drag.track(...)` avec une clé unique |
| Mauvaise position sur le serveur | Tâches faites masquées | `dropAnchor` / `multiMovePositions` : cas à ajouter dans `DragDropTest` |
| Le glisser s'arrête pendant le défilement automatique | Geste écouté sur la tâche au lieu de l'écran | Le geste doit rester sur la `Box` de `ListScreen` |
