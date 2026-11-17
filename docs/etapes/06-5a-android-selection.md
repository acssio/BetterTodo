# Étape 6-5a : app Android, sélection par appui long, Déplacer, Supprimer

## Objectif

Agir sur plusieurs tâches à la fois, comme le mode sélection du site mobile (étape 5-3) :

- **appui long** sur une tâche → elle est sélectionnée (petite vibration) ;
- ensuite, **toucher** une tâche l'ajoute à la sélection ou l'en retire ;
- **en haut** : « Annuler · N sélectionnées · Tout » ;
- **en bas** : « Déplacer » et « Supprimer ».

Ça marche dans une liste **et** dans Aujourd'hui / Planifié. Le glisser au doigt viendra à la 6-5b. Aucun changement côté serveur.

## Fichiers

```
app/src/main/java/io/github/acssio/bettertodo/ui/
  TaskStore.kt       ← + sélection (selectedIds, tap, toggleSelection, selectAll),
                        deleteSelected, startMove / confirmMove ; toggle / inDisplayOrder (fonctions pures)
  BoardModel.kt      ← + visibleTaskIds, listIdOf, applyMoved (autre liste = la tâche quitte l'écran)
  SmartModel.kt      ← + visibleTaskIds, listIdOf, applyMoved, afterMove (recharge les noms)
  PlacePicker.kt     ← NOUVEAU : choisir « Liste › Groupe » (sorti de la Quick task, commun avec Déplacer)
  QuickTaskModel.kt  ← utilise PlacePicker
  Selection.kt       ← NOUVEAU : SelectionTopBar, SelectionBottomBar (+ confirmation, panneau Déplacer)
  TaskItem.kt        ← appui long (combinedClickable + vibration), fond bleu clair si sélectionnée
  ListScreen.kt, SmartScreen.kt ← barres de sélection, BackHandler, pas d'ajout pendant une sélection
app/src/test/…       ← BoardModelTest complété, QuickTaskTest → PlacePicker (39 tests)
```

## La machine d'état de la sélection

```
  aucune ──(appui long)──→ sélection ──(toucher)──→ ajoute / retire (dernière retirée → aucune)
                              ├──(Annuler ou bouton retour)──→ aucune
                              ├──(Tout)──→ toutes les tâches affichées
                              ├──(Supprimer → confirmer)──→ aucune
                              └──(Déplacer)──→ panneau ──(Déplacer)──→ aucune
                                                   └──(fermer)──→ sélection
```

- **La sélection est dans `TaskStore`** (`selectedIds`). Elle marche donc pour les deux sortes d'écran, et survit à la rotation.
- **`TaskStore.tap(task)`** décide quoi faire d'un toucher : en mode sélection, ajouter ou retirer la tâche ; sinon, ouvrir son panneau.
- **Le bouton retour d'Android** annule la sélection au lieu de revenir à l'accueil. Le `BackHandler(enabled = model.selecting)` de l'écran est composé après celui de l'accueil, donc c'est lui qui a la priorité.

## Supprimer

- Confirmation : « Supprimer 2 tâches ? » (`AlertDialog`).
- Les tâches disparaissent tout de suite, puis un `DELETE` par tâche part, **l'un après l'autre**.
- En cas d'erreur : bandeau, et rechargement.

**Limite connue** (même chose pour une seule tâche depuis la 6-3) : si le serveur ne répond pas, le rechargement échoue aussi. Les tâches restent alors absentes de l'écran, alors qu'elles existent toujours. Tirer vers le bas, une fois le serveur revenu, les fait réapparaître (vérifié).

## Déplacer

- Panneau « Déplacer N tâches vers… » : la liste puis le groupe (`PlacePicker`, comme la Quick task), puis « Déplacer ».
- **La destination proposée** est le groupe de la première tâche sélectionnée, dans l'ordre de l'écran.
- **Les tâches vont à la fin du groupe**, dans l'ordre de l'écran (`inDisplayOrder`), avec un `PATCH group_id` par tâche, l'un après l'autre, comme le site.
- **À l'écran** :
  - dans la même liste, les tâches changent de groupe ;
  - vers une autre liste, elles quittent l'écran (`BoardModel.applyMoved`) ;
  - depuis Aujourd'hui / Planifié, elles restent, puis l'écran se recharge pour afficher leurs nouveaux « Liste › Groupe » (`afterMove`).

## PlacePicker : une logique commune

La Quick task avait déjà « choisir une liste, charger ses groupes, proposer un groupe ». Cette partie est sortie dans `PlacePicker` (+ le composable `PlacePickerRow`), que la Quick task et « Déplacer » utilisent tous les deux. `chooseList` / `chooseGroup` y ont déménagé, avec leurs tests.

## Bug trouvé en testant : écran tout blanc

**Ce qui se passait** : au premier appui long, l'écran devenait **entièrement blanc**. Pourtant l'arbre d'accessibilité (`uiautomator`) montrait bien l'écran de sélection.

**La cause** : j'avais écrit dans le lambda `topBar` du `Scaffold` :

```kotlin
if (model.selecting) {
    SelectionTopBar(model)
    return@Scaffold        // ← retour anticipé dans un lambda @Composable
}
TopAppBar(…)
```

**La correction** : un simple `if (…) SelectionTopBar(model) else TopAppBar(…)`.

**À retenir** : jamais de `return` anticipé dans un lambda `@Composable` (noté dans `CLAUDE.md`).

## Lancer et tester

```bash
cd android && ./gradlew test assembleDebug
```

### Tests automatiques (39 tests)

- `BoardModelTest` : ajouter / retirer de la sélection, sélection dans l'ordre de l'écran, plusieurs tâches déplacées à la fin dans l'ordre ;
- `QuickTaskTest` : `PlacePicker.chooseList` / `chooseGroup` ;
- ceux des étapes précédentes.

### Vérifié sur l'émulateur, avec de vrais appuis, et sur le serveur

- appui long → « 1 sélectionnée », barres en haut et en bas, tâche sur fond bleu clair ;
- toucher + → 2 ; « Tout » → 5 (tous les groupes de la liste) ; toucher − → 4 ; bouton retour → plus de sélection ;
- Déplacer 2 tâches de « À faire » vers « En cours » : à la fin, dans l'ordre de l'écran ; compteurs des onglets à jour ;
- Déplacer vers une autre liste : la tâche quitte l'écran et arrive à la fin de « Courses › Épicerie » ;
- Supprimer 2 tâches : « Annuler » garde la sélection, « Supprimer » les efface du serveur ;
- dans Planifié : destination proposée = groupe de la tâche ; Déplacer vers « Plus tard » ;
- rotation pendant une sélection : elle reste ;
- API arrêtée : Supprimer → bandeau ; tirer vers le bas une fois l'API revenue → la tâche revient.

## Où regarder en cas de problème

| Symptôme | Cause probable | Que faire |
|---|---|---|
| Écran blanc après un changement d'état | `return` anticipé dans un lambda `@Composable` | Remplacer par `if / else` |
| Le bouton retour quitte la liste au lieu d'annuler la sélection | `BackHandler(enabled = model.selecting)` absent ou composé avant celui de l'accueil | Voir `ListScreen` / `SmartScreen` |
| « Déplacer » propose une liste inattendue | `listIdOf` de l'écran | `BoardModel` : la liste ouverte ; `SmartModel` : la liste de la tâche |
| Tâches supprimées absentes alors qu'elles existent | Suppression et rechargement ratés (serveur injoignable) | Tirer vers le bas |
