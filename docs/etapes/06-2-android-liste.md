# Étape 6-2 : app Android, ouvrir une liste et cocher

## Objectif

Toucher une liste sur l'accueil ouvre son écran : ses groupes et ses tâches, affichés selon la vue de la liste (colonnes ou sections), avec tags, échéance et urgence. On peut **cocher / décocher** une tâche. Le reste est en lecture seule (ajouter et modifier : 6-3).

## Feuille de route Android

| Étape | Contenu |
|---|---|
| 6-1 | Connexion, accueil en lecture seule |
| **6-2** | **Ouvrir une liste, cocher une tâche** |
| 6-3 | Ajouter une tâche, la modifier dans un panneau du bas, la supprimer |
| 6-4 | Listes intelligentes « Aujourd'hui » / « Planifié », Quick task |
| 6-5 | Sélection par appui long, « Déplacer », glisser au doigt |
| 6-6 | Gérer listes, groupes de listes et groupes (créer, renommer, couleur, icône, repli enregistré) |
| 6-7 | Paramètres, dont **changer son mot de passe initial depuis l'app** |

## Fichiers

```
app/src/main/java/io/github/acssio/bettertodo/
  api/Models.kt        ← + Task, BoardGroup, Board, UrgencyLevel ; TodoList.defaultView / showDone
  api/ApiClient.kt     ← + board(listId), setTaskDone(taskId, done), urgencyLevels()
  ui/HomeScreen.kt     ← + HomePage (Home / OpenList), openList / closeList, ListIconBox partagé
  ui/BoardModel.kt     ← données d'une liste ouverte, cocher « optimiste »
  ui/ListScreen.kt     ← en-tête, vue colonnes (onglets + pages), vue sections
  ui/TaskItem.kt       ← une tâche : case, titre, note, tags, échéance, urgence
  ui/DueDates.kt       ← "Aujourd'hui", "Demain", "18 nov.", en retard ? (copie de utils/dates.ts)
app/src/test/…         ← + DueDatesTest, BoardModelTest
```

## La navigation : un niveau de plus dans la machine d'état

```
  AppViewModel.screen :  Loading → Login → Home ──────────────────────────┐
                                                                          │
  HomeViewModel.page :              Home ──(toucher une liste)──→ OpenList(board)
                                     ▲                                │
                                     └──(‹ ou bouton retour Android)──┘  (accueil rechargé)
```

- `HomeScreen` regarde `model.page` : si c'est `OpenList`, il affiche `ListScreen` à la place de l'accueil.
- `BackHandler` intercepte le bouton retour d'Android tant qu'une liste est ouverte. Sur l'accueil, retour quitte l'app, comme d'habitude.
- En revenant, l'accueil se recharge **sans indicateur**, pour que les compteurs suivent les tâches cochées.

## BoardModel plutôt qu'un ViewModel

Le plan prévoyait un `BoardViewModel`. Problème : un ViewModel Compose est rangé sous une clé et réutilisé. En rouvrant la même liste, on retrouverait ses vieilles données.

`BoardModel` est donc une **classe simple**, créée par `HomeViewModel.openList` à chaque ouverture et rangée dans `HomePage.OpenList` :

- toujours des données fraîches à l'ouverture ;
- elle survit à la rotation, car `HomeViewModel` la garde ;
- ses requêtes tournent dans le `viewModelScope` de `HomeViewModel`.

## Les deux vues

| `default_view` | Affichage | Compose |
|---|---|---|
| `columns` | Un groupe à la fois en pleine largeur, glisser pour changer, onglets en haut avec le nombre de tâches à faire | `PrimaryScrollableTabRow` + `HorizontalPager` |
| `sections` | Groupes empilés, titre puis tâches en lignes | `LazyColumn` |

`TaskItem` sert aux deux : en carte avec bord (`card = true`) dans les colonnes, en ligne dans les sections. Comme `TaskCard` et `TaskRow` sur le site.

Basculer d'une vue à l'autre depuis l'app viendra à l'étape 6-6.

## Cocher une tâche

`BoardModel.toggleDone` est **optimiste**, comme `Workspace` sur le site :

1. le board est modifié tout de suite (`replaceTask`, qui fait une copie : on ne modifie jamais une liste en place) ;
2. `PATCH /tasks/{id}` `{"done": true}` part ensuite ;
3. en cas d'erreur, la tâche est remise comme avant, puis la liste est rechargée, avec le bandeau « Modification non enregistrée : … ».

**Liste qui masque les tâches faites** (`show_done = false`) : la tâche cochée reste visible 0,7 s (`leavingIds`), le temps de voir la case se cocher, puis elle disparaît en fondu (`Modifier.animateItem()`).

Afficher / masquer les tâches faites depuis l'app viendra à l'étape 6-6. D'ici là, c'est le réglage fait sur le site qui s'applique.

## Lancer et tester

Comme à l'étape 6-1 (voir [06-1](06-1-android-connexion-accueil.md)) :

```bash
cd android && ./gradlew test assembleDebug
```

### Tests automatiques (17 tests)

- `DueDatesTest` : aujourd'hui / demain / hier, autre mois, autre année, en retard ;
- `BoardModelTest` : tâches faites masquées ou affichées, tâche qui vient d'être cochée encore visible, `replaceTask` ne modifie pas l'original ;
- ceux de la 6-1.

### Vérifié sur l'émulateur (Pixel 10, Android 17), avec de vrais appuis

Sur une pile Docker de test avec une liste en colonnes (faites masquées) et une en sections (faites affichées) :

- colonnes : onglets avec compteurs, tags, échéances (« Aujourd'hui » et « 14 nov. » en rouge, « Demain », « 3 janv. 2027 »), pastilles d'urgence, icône de note, tâche faite masquée ;
- glisser vers le groupe suivant, toucher un onglet, groupe vide → « Aucune tâche » ;
- cocher dans la liste en colonnes → case verte, titre barré, compteur 3 → 2, puis la tâche disparaît ; le serveur est à jour ;
- sections : tâche faite affichée barrée ; décocher et cocher, et la tâche reste visible ; le serveur est à jour ;
- cocher avec l'API arrêtée → la tâche revient comme avant, bandeau « Modification non enregistrée : Serveur injoignable… » ; tirer vers le bas une fois l'API relancée → bandeau effacé ;
- bouton ‹ et bouton retour d'Android → accueil, compteurs à jour ;
- rotation de l'écran → la liste et le groupe affiché restent.

## Où regarder en cas de problème

| Symptôme | Cause probable | Que faire |
|---|---|---|
| L'app plante en ouvrant une liste | Exception dans un `async` hors `coroutineScope` | `adb logcat -b crash` ; vérifier `BoardModel.fetch` |
| Une tâche cochée réapparaît | L'API a refusé : le bandeau donne la raison | Logcat, filtre `tag:ApiClient` |
| La tâche cochée ne disparaît pas | La liste affiche les tâches faites (`show_done`) | Normal ; réglage sur le site pour l'instant |
| Les échéances sont décalées d'un jour | Date du téléphone | `DueDates` utilise la date locale du téléphone, comme le site |
| Pas de pastille d'urgence | `GET /urgency-levels` n'a pas répondu ou l'id est inconnu | Tirer vers le bas pour recharger |
| Retour quitte l'app au lieu de revenir à l'accueil | `BackHandler` pas composé | Vérifier que `model.page` est bien `OpenList` |
