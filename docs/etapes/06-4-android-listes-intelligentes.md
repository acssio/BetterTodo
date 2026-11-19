# Étape 6-4 : app Android, Aujourd'hui / Planifié et Quick task

## Objectif

Reprendre sur Android deux choses du site mobile :

- les listes intelligentes **« Aujourd'hui »** (échéance aujourd'hui ou en retard) et **« Planifié »** (toutes les échéances), en tuiles en haut de l'accueil, avec leur compteur ;
- le bouton flottant **« Quick task »**, pour ajouter une tâche sans ouvrir de liste.

Aucun changement côté serveur : `GET /tasks/due`, `GET /tasks/due/counts` et `due_date` à la création existaient déjà.

## Fichiers

```
app/src/main/java/io/github/acssio/bettertodo/
  api/Models.kt          ← + ListRef, DueInfo, DueTask, DueCounts ; CreateTaskRequest.dueDate
  api/ApiClient.kt       ← + dueTasks(until), dueCounts(today) ; createTask(…, dueDate)
  data/QuickTaskMemory.kt ← DataStore « quick_task » : liste et groupe de la dernière Quick task
  ui/TaskStore.kt        ← NOUVEAU : classe de base commune (chargement, tâche ouverte, modifications)
  ui/BoardModel.kt       ← hérite de TaskStore, ne garde que ce qui est propre à une liste
  ui/SmartModel.kt       ← hérite de TaskStore : Aujourd'hui / Planifié
  ui/SmartLists.kt       ← SmartKind (titre, icône, couleur), sections par date, belongs()
  ui/SmartScreen.kt      ← écran d'une liste intelligente
  ui/QuickTaskModel.kt   ← choix de la liste, du groupe, de l'échéance ; chooseList / chooseGroup
  ui/QuickTaskSheet.kt   ← panneau du bas de la Quick task
  ui/Dropdown.kt         ← menu déroulant commun (groupe d'une tâche, liste et groupe de la Quick task)
  ui/HomeScreen.kt       ← tuiles, HomePage.OpenSmart, bouton flottant, Snackbar, compteurs
  ui/TaskSheet.kt        ← travaille avec TaskStore ; groupe en lecture seule si inconnu ; DueDateChips réutilisable
  ui/TaskItem.kt         ← + origin : la liste d'origine de la tâche
app/src/test/…           ← + SmartListsTest, QuickTaskTest ; ModelsTest complété (36 tests)
```

## TaskStore : une classe de base pour deux sortes d'écran

Le panneau de la 6-3 ne connaissait que `BoardModel`. Pour le réutiliser dans Aujourd'hui / Planifié sans copier la logique, ce qui est commun est passé dans une **classe abstraite** :

```
                    TaskStore (abstraite)
        chargement + niveaux d'urgence, tâche ouverte,
        setTitle / setDueDate / addTag… (optimistes, annulation en cas d'erreur)
                 ▲                              ▲
       BoardModel (une liste)          SmartModel (Aujourd'hui / Planifié)
   board, groupes, createTask            tasks (DueTask), sections
```

Chaque sous-classe dit seulement :

| À écrire | BoardModel | SmartModel |
|---|---|---|
| `loadData()` | `GET /lists/{id}/board` | `GET /tasks/due?until=` (aujourd'hui) ou `GET /tasks/due` |
| `applyLocal(task)` | remplace dans son groupe, ou change de groupe | remplace dans `tasks` |
| `removeLocal(id)` | retire du board | retire de `tasks` |
| `hidesDoneTasks` | si la liste masque les faites | toujours |
| `groupsFor(task)` | les groupes de la liste | `null` : on ne les connaît pas |

`TaskSheet` ne connaît que `TaskStore`. Quand `groupsFor` renvoie `null`, il affiche « Liste › Groupe » **en lecture seule**, comme le site, qui ne connaît alors que le groupe de la tâche.

## Aujourd'hui / Planifié

- **Les sections** sont faites par `SmartLists.sections`, une copie de `groupDueTasks` du site :
  - « Aujourd'hui » : *En retard* (titre en rouge), puis *Aujourd'hui* ;
  - « Planifié » : *En retard*, *Aujourd'hui*, *Demain*, puis une section par date (« mer. 25 nov. », avec l'année si ce n'est pas celle-ci).
- **Une tâche modifiée reste dans `SmartModel.tasks`**, pour que le panneau ouvert la retrouve. C'est `SmartLists.belongs` qui décide si elle est encore à sa place. Une tâche **cochée**, ou **repoussée à demain** dans « Aujourd'hui », quitte la vue en fondu (`animateItem`). Une tâche cochée reste visible 0,7 s (`leavingIds`).
- **La date du jour est celle du téléphone** (`DueDates.todayIso()`), envoyée à l'API comme le fait le site.
- **En revenant à l'accueil**, celui-ci se recharge, donc les compteurs suivent.

## Quick task

- **Le bouton flottant** est centré en bas (`FabPosition.Center`), et affiché seulement s'il y a au moins une liste.
- **Le panneau** contient :
  - le titre ;
  - la liste puis le groupe (les groupes d'une liste sont chargés quand on la choisit) ;
  - l'échéance (**Aujourd'hui** par défaut), avec les mêmes puces que le panneau de tâche ;
  - « Créer la tâche », ou ↵.
- **En cas de succès** : le panneau se ferme, une **Snackbar** « Tâche ajoutée dans Liste › Groupe » apparaît en bas, et les compteurs sont rechargés.
- **En cas d'erreur** : le panneau reste ouvert, avec le message.
- **Mémoire** (`QuickTaskMemory`, DataStore) :
  - la liste et le groupe de la dernière création **réussie** ;
  - retenue **par téléphone**, et gardée après la déconnexion ;
  - `chooseList` / `chooseGroup` (copiés du site) retombent sur la première liste ou le premier groupe si ceux retenus n'existent plus.

## À retenir

- **Lire le même JSON deux fois.** `GET /tasks/due` renvoie des tâches avec deux champs en plus (`list`, `group_name`). Plutôt que recopier tous les champs de `Task` dans une autre classe, `ApiClient.dueTasks` lit le texte une fois en `List<Task>`, une fois en `List<DueInfo>`, et les assemble avec `zip`. Ça marche grâce à `ignoreUnknownKeys`.
- **Les valeurs par défaut ne sont pas écrites.** `CreateTaskRequest("Pain")` donne `{"title":"Pain"}`, sans `"due_date":null`. kotlinx.serialization n'écrit pas les valeurs par défaut (vérifié par un test).
- **Le texte du bouton flottant n'arrivait pas aux services d'accessibilité.** Je l'ai vu avec `uiautomator` : un nœud sans texte, marqué « pas accessible » (`NAF`). Correction : un `contentDescription` explicite, pour que TalkBack l'annonce.

## Lancer et tester

```bash
cd android && ./gradlew test assembleDebug
```

### Tests automatiques (36 tests)

- `SmartListsTest` :
  - sections d'Aujourd'hui et de Planifié (avec une autre année) ;
  - section en retard en rouge ;
  - `belongs` : repoussée à demain, échéance enlevée, cochée ;
  - tâche cochée encore visible un instant ;
- `QuickTaskTest` : liste retenue, disparue ou absente ; groupe retenu ou d'une autre liste ;
- `ModelsTest` : création sans échéance (pas de `null` envoyé) et avec échéance ;
- ceux des étapes précédentes.

### Vérifié sur l'émulateur, avec de vrais appuis, et sur le serveur

- tuiles : 2 / 5, comme `GET /tasks/due/counts` ;
- Planifié : sections En retard (rouge), Aujourd'hui, Demain, « mer. 25 nov. », « dim. 3 janv. 2027 », avec la liste d'origine ;
- Aujourd'hui :
  - cocher → la tâche disparaît ;
  - panneau → groupe « Projet maison › À faire » en lecture seule ;
  - « Demain » → la tâche sort de la vue, puis « Rien d'urgent… » ;
  - compteurs 0 / 4 au retour ;
- Quick task :
  - par défaut, première liste, premier groupe, Aujourd'hui, bouton désactivé sans titre ;
  - Courses › Droguerie → tâche créée avec l'échéance du jour, Snackbar, compteurs 1 / 5 ;
  - Courses › Droguerie reproposé, même après relance ;
  - « Aucune » → tâche sans échéance ;
- rotation dans Planifié ;
- API arrêtée : changer l'urgence depuis Planifié → annulée, bandeau.

## Où regarder en cas de problème

| Symptôme | Cause probable | Que faire |
|---|---|---|
| Compteurs des tuiles faux d'un jour | Date du téléphone | `DueDates.todayIso()` utilise le fuseau du téléphone, comme le site |
| Une tâche ne quitte pas Aujourd'hui après modification | `SmartLists.belongs` | Tester le cas dans `SmartListsTest` |
| La Quick task propose une liste inattendue | Mémoire d'un autre compte ou liste supprimée | Normal : retombe sur la première liste ; vider les données de l'app pour repartir de zéro |
| « Aucun groupe » dans la Quick task | La liste n'a pas de groupe, ou chargement raté | Message d'erreur sous les puces ; Logcat `tag:ApiClient` |
| Le panneau ne propose pas de changer de groupe | Ouvert depuis Aujourd'hui / Planifié | Voulu : passer par la liste |
