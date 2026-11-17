# Étape 6-3 : app Android, ajouter, modifier et supprimer une tâche

## Objectif

Depuis l'écran d'une liste :

- **ajouter** des tâches à la suite (« + Ajouter une tâche » en bas de chaque groupe) ;
- **toucher une tâche** pour ouvrir un **panneau du bas** et tout modifier : titre, échéance, groupe, urgence, tags, notes ;
- **supprimer** une tâche, après confirmation.

Comme sur le site, **chaque champ s'enregistre tout seul**. Il n'y a pas de bouton « Enregistrer ». Aucun changement côté serveur.

## Fichiers

```
app/src/main/java/io/github/acssio/bettertodo/
  api/TaskChanges.kt   ← corps de PATCH /tasks/{id} : seuls les champs modifiés (absent / null / valeur)
  api/ApiClient.kt     ← + createTask, updateTask, deleteTask (setTaskDone retiré : updateTask le remplace)
  api/Models.kt        ← + CreateTaskRequest
  ui/BoardModel.kt     ← + tâche ouverte, setTitle / setNotes / setDueDate / setUrgency / addTag / removeTag /
                          moveToGroup, createTask, deleteTask ; applyTask / removeTask / cleanTag (fonctions pures)
  ui/TaskSheet.kt      ← le panneau du bas
  ui/AddTaskRow.kt     ← « + Ajouter une tâche »
  ui/TaskItem.kt       ← toucher = ouvrir (onOpen) ; CheckCircle partagé avec le panneau
  ui/ListScreen.kt     ← lignes d'ajout, panneau, imePadding
  ui/DueDates.kt       ← + todayIso / tomorrowIso, conversions pour le calendrier
  ui/theme/Theme.kt    ← puces sélectionnées et calendrier aux couleurs du site
app/src/test/…         ← + TaskChangesTest, BoardModelTest et DueDatesTest complétés (27 tests)
```

## TaskChanges : envoyer seulement ce qui change

L'API distingue trois cas pour chaque champ de `PATCH /tasks/{id}` (`Optional[T]` côté Go) :

| JSON envoyé | Effet |
|---|---|
| champ absent | on n'y touche pas |
| `"due_date": null` | on efface (plus d'échéance) |
| `"due_date": "2026-11-25"` | on remplace |

Une `data class` Kotlin ne sait pas faire la différence entre « absent » et « null ». `TaskChanges` construit donc le JSON à la main, champ par champ :

```kotlin
TaskChanges().dueDate(null).urgencyId(levelId).toJson()  // {"due_date":null,"urgency_id":"…"}
```

## Le panneau (TaskSheet)

`ModalBottomSheet` de Material 3. On le ferme en le glissant vers le bas, en touchant à côté, avec ✕ ou avec le bouton retour.

| Champ | Quand c'est enregistré |
|---|---|
| Titre | à ↵, en quittant le champ, ou à la fermeture. Un titre vidé reprend l'ancien |
| Échéance | tout de suite : **Aujourd'hui / Demain / Choisir… / Aucune**. « Choisir… » ouvre le calendrier Material, puis affiche la date choisie |
| Groupe | tout de suite : la tâche va à la fin du groupe choisi (comme le fait le serveur) |
| Urgence | tout de suite : **Aucune** + les niveaux, avec leur point de couleur |
| Tags | tout de suite : ✕ sur un tag pour l'enlever, ↵ dans « Ajouter un tag » (le `#` du début est retiré) |
| Notes | 0,8 s après la dernière frappe (`LaunchedEffect(notes)` relancé à chaque frappe), et à la fermeture |
| Supprimer | après confirmation (`AlertDialog`) |

- **Ce qui est retenu** : la tâche ouverte (`BoardModel.openTaskId`) et les textes en cours de frappe (`rememberSaveable`). Le panneau reste ouvert si on tourne le téléphone.
- **Enregistrement à la fermeture** : il se fait dans `DisposableEffect { onDispose { … } }`. `updateTask` ne fait rien si la tâche n'est plus dans la liste, pour ne pas faire « revenir » une tâche qu'on vient de supprimer.

## Ajouter des tâches (AddTaskRow)

Petite machine d'état :

```
fermé ──(toucher)──→ ouvert ──(↵ sur champ vide / ✕)──→ fermé
                       └──(↵ avec un titre)──→ ouvert (tâche ajoutée, champ vidé, clavier toujours ouvert)
```

La tâche apparaît à la réponse du serveur, parce qu'il faut son id.

## Modifications optimistes

Toutes les modifications passent par `BoardModel.updateTask(before, after, changes)` :

1. l'écran affiche `after` tout de suite (`applyTask`, qui gère aussi le changement de groupe) ;
2. `PATCH` avec `changes` ;
3. en cas d'erreur, `before` est remise, puis la liste est rechargée, avec le bandeau « Modification non enregistrée : … ».

Même principe pour la suppression : la tâche disparaît tout de suite, et revient au rechargement si l'API refuse.

## Bugs trouvés en testant sur l'émulateur

1. **Le champ « Ajouter » était caché sous le clavier.**
   - La liste ne réduisait pas sa hauteur quand le clavier s'ouvrait. Correction : `imePadding()` sur le contenu de `ListScreen`.
   - Après chaque ajout, la nouvelle tâche poussait le champ plus bas. Correction : `BringIntoViewRequester`, qui le ramène en vue à l'ouverture et après chaque ajout.
2. **↵ dans le titre ajoutait un retour à la ligne.** Avec Compose, l'action « OK » du clavier ne marche qu'avec `singleLine = true`. Je voulais garder le titre sur plusieurs lignes pour les titres longs, donc un `\n` tapé est retiré et traité comme une validation.
3. **Le titre gardait le focus après ↵.** `ModalBottomSheet` s'affiche dans **une fenêtre à part**, avec son propre focus. `LocalFocusManager` était lu en dehors du panneau, donc `clearFocus()` agissait sur la fenêtre de la liste. Correction : le lire **à l'intérieur** du contenu du panneau. À retenir pour tous les panneaux et boîtes de dialogue.
4. **Puces sélectionnées et calendrier en lavande.** Ce sont des couleurs Material par défaut. Correction : `secondaryContainer`, `surfaceContainerHigh` et `surfaceContainerLow` ont les couleurs du site dans `Theme.kt`.

## Lancer et tester

```bash
cd android && ./gradlew test assembleDebug
```

### Tests automatiques (27 tests)

- `TaskChangesTest` : seulement les champs modifiés, `null` pour effacer, valeurs, guillemets échappés ;
- `BoardModelTest` : changer de groupe met la tâche à la fin, nouvelle tâche à la fin, suppression, nettoyage d'un tag ;
- `DueDatesTest` : aujourd'hui / demain (changement de mois), aller-retour avec le calendrier (UTC) ;
- ceux des étapes précédentes.

### Vérifié sur l'émulateur, avec de vrais appuis, et sur le serveur

- vue colonnes : 4 tâches ajoutées à la suite, le champ reste au-dessus du clavier ; ↵ sur champ vide referme ;
- vue sections : tâche ajoutée dans le 2e groupe ;
- panneau : échéance Demain, puis le 25 au calendrier ; urgence ; tag retiré puis ajouté (avec `#`) ; notes enregistrées sans fermer ; titre validé par ↵, puis le champ lâche bien le focus ; changement de groupe (la tâche est à la fin du nouveau groupe) ;
- rotation avec le panneau ouvert et une note en cours de frappe : tout reste, et la note est enregistrée ;
- suppression : « Annuler » garde la tâche, « Supprimer » l'efface ;
- API arrêtée : changer l'urgence → la pastille revient comme avant, bandeau d'erreur.

**À savoir** : le calendrier suit la langue du téléphone. Sur l'émulateur (en anglais) il affiche « Select date » ; sur un téléphone en français, il sera en français.

## Où regarder en cas de problème

| Symptôme | Cause probable | Que faire |
|---|---|---|
| Un champ du panneau ne s'enregistre pas | Valeur inchangée (rien n'est envoyé) ou erreur API | Logcat, filtre `tag:ApiClient` ; bandeau sur la liste |
| Le clavier cache un champ | `imePadding()` manquant sur le conteneur | Voir `ListScreen` et `TaskSheet` |
| `clearFocus()` ou `hide()` sans effet dans un panneau | `LocalFocusManager` lu hors de la fenêtre du panneau | Le lire dans le contenu de `ModalBottomSheet` / `AlertDialog` |
| Une tâche supprimée réapparaît | Le serveur a refusé : le rechargement la remet | Lire le bandeau |
| Date décalée d'un jour avec le calendrier | Conversion sans UTC | `DueDates.toPickerMillis` / `fromPickerMillis` |
