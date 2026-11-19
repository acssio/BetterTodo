# Étape 8-2 : app Android, heure d'une tâche et échéance de la Quick task

## Objectif

Reprendre dans l'app ce que le site a reçu aux étapes 7-3 et 7-4 :

- une **heure facultative** sur une tâche (« 14:30 »), **tapée** dans un champ « Heure » du panneau de tâche (pas de liste d'heures, maquette v3, écran Android 3) ;
- l'**échéance proposée par la Quick task** (Aucune / Aujourd'hui / Demain), réglée dans Paramètres › Quick task.

Jusqu'ici l'app ignorait `due_time` et `quick_task_due` sans planter. **Ni l'API ni la base n'ont changé** : tout existait depuis la 7-3 et la 7-4.

## Fichiers

```
android/app/src/main/java/io/github/acssio/bettertodo/
  api/Models.kt         → Task.dueTime ("14:30" ou null), User.quickTaskDue (+ constantes QUICK_DUE_*)
  api/TaskChanges.kt    → dueTime(…) pour PATCH /tasks/{id}
  api/ApiClient.kt      → updateMe(quickTaskDue = …)
  ui/DueDates.kt        → format(date, heure) « Aujourd'hui 14:30 », parseTime (heure tapée)
  ui/TaskStore.kt       → setDueTime ; setDueDate(null) enlève aussi l'heure à l'écran
  ui/TaskSheet.kt       → champ « Heure » (DueTimeField) sous l'échéance
  ui/TaskItem.kt        → l'heure après la date sur la carte
  ui/SmartLists.kt      → Aujourd'hui / Planifié triés par date puis par heure (sans heure en dernier)
  ui/QuickTaskModel.kt  → dueDateFor(réglage) : date proposée à l'ouverture
  ui/HomeScreen.kt      → openQuickTask(user)
  ui/SettingsScreen.kt  → partie « Quick task » (setQuickTaskDue, PATCH /me)
android/app/src/test/…  → DueDatesTest, SmartListsTest, QuickTaskTest, TaskChangesTest
```

## Comment ça marche

### Le champ Heure

```
frappe ──► text (état du champ seulement, rien n'est envoyé)
↵ / sortie du champ / fermeture du panneau ──► save()
      vide            → setDueTime(null)        (plus d'heure)
      parseTime ok    → setDueTime("14:30")     (et le champ affiche « 14:30 »)
      pas compris     → on remet l'ancienne heure
```

- **Jamais d'envoi à chaque frappe** : sur le site, la réponse du serveur réécrivait le champ pendant la saisie (bug 7-3). On garde la même règle.
- **Clavier numérique** (`KeyboardType.Number`) : il n'a pas toujours « : », alors `DueDates.parseTime` comprend `1430`, `930`, `14h30`, `14h`, `9`, `14.30` et `14:30`. Après « : », les minutes doivent avoir 2 chiffres : `09:1` (une lettre effacée de « 09:15 ») est refusé au lieu de devenir 09:01 (bug trouvé en testant).
- **Pas d'heure sans date** (contrainte de la base, migration 009) : le champ est grisé tant qu'il n'y a pas d'échéance. Choisir « Aucune » envoie `due_date: null`, le serveur enlève l'heure, et `TaskStore.setDueDate` fait pareil à l'écran.
- « Rappel à 14:30 » (maquette) viendra avec les notifications (8-7).

### Quick task

- `User.quickTaskDue` vaut `none`, `today` (par défaut) ou `tomorrow`. C'est un réglage **du compte** : le même sur le site et le téléphone.
- `QuickTaskModel.dueDateFor` le transforme en date **à l'ouverture** du panneau (une valeur inconnue donne « Aujourd'hui »).
- Paramètres › Quick task : modification optimiste comme les autres réglages du compte (`SettingsModel.updateMe`), compte remonté à `AppViewModel`, donc la Quick task suivante utilise la nouvelle valeur.

## Lancer et tester

```bash
cd android && ./gradlew assembleDebug testDebugUnitTest
```

75 tests unitaires, dont `heureTapee`, `heureIncomprise`, `dateAvecHeure`, `memeJourParHeureSansHeureEnDernier`, `echeanceProposee`.

### Vérifié sur l'émulateur, avec de vrais appuis, et sur le serveur

- carte : « Aujourd'hui 14:30 », aussi dans Aujourd'hui ;
- taper `915` puis ↵ → `due_time = 09:15` sur le serveur, le champ affiche « 09:15 » ;
- taper `2500` → refusé, « 09:15 » revient, rien n'est envoyé ;
- champ vidé → `due_time = null` ; `1430` → `14:30` ;
- échéance « Aucune » → `due_date` et `due_time` à null, champ Heure grisé ;
- Paramètres › Quick task › Aucune → `quick_task_due = none` sur le serveur ; la Quick task s'ouvre sur « Aucune » et crée une tâche sans échéance.

## Où regarder en cas de problème

| Symptôme | Cause probable | Que faire |
|---|---|---|
| L'heure tapée revient à l'ancienne | `parseTime` ne la comprend pas | `DueDatesTest` ; ajouter le format voulu dans `parseTime` |
| Le serveur répond 400 sur l'heure | Heure envoyée sans échéance | `TaskStore.setDueTime` ne doit rien envoyer si `dueDate == null` |
| L'heure n'est pas enregistrée en fermant le panneau | Le champ n'avait pas le focus | `DueTimeField`, `DisposableEffect` → `saveOnClose` |
| La Quick task propose toujours « Aujourd'hui » | Compte pas à jour dans `AppViewModel` | `SettingsModel.updateMe` → `onUserChanged` |
