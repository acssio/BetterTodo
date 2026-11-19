# Étape 8-6 : app Android, listes intelligentes personnalisées

## Objectif

Reprendre dans l'app ce que le site a reçu aux étapes 7-5 et 7-6 (`ModifBetterTodo.md`, « listes intelligentes ») :

- des listes qui s'affichent comme « Aujourd'hui » et « Planifié », mais **filtrées par tags** (au moins un / tous) ou **par niveaux d'urgence** ;
- choisir dans les Paramètres les listes intelligentes **affichées** sur l'accueil, Aujourd'hui et Planifié compris ;
- les créer, les modifier et les supprimer depuis l'app.

Comme tout depuis la 8-3, elles sont **calculées sur la base du téléphone** (elles marchent hors ligne) et synchronisées avec le serveur s'il y en a un.

## Ce qui change pour l'utilisateur

- **Accueil** : les tuiles, deux par ligne : Aujourd'hui et Planifié (si affichées), puis les listes perso visibles, avec leur nombre de tâches.
- **Une liste perso ouverte** : son nom, son filtre en petit (« urgence Haute ou Moyenne », « #boulot et #réunion »).
  - **Par tags** : une section « À faire ».
  - **Par urgence** : une section par niveau, dans sa couleur, du plus urgent au moins urgent.
  - Comme dans Aujourd'hui : cocher, ouvrir le panneau, sélection. Une tâche qui ne correspond plus (cochée, tag enlevé…) quitte la vue.
- **Paramètres › Listes intelligentes** : un interrupteur pour Aujourd'hui, Planifié et chaque liste perso ; un crayon sur les listes perso ; « Nouvelle liste intelligente ».
- **Panneau de création / modification** : nom, type (Par tags / Par urgence), tags et « Au moins un / Tous », ou niveaux d'urgence, couleur et icône (les mêmes que pour une liste). Rien n'est enregistré avant « Créer » ou « Enregistrer ». « Supprimer » demande confirmation, avec le texte du site.

## Fichiers

```
android/app/src/main/java/io/github/acssio/bettertodo/
  api/Models.kt            → SmartList (+ summary), User.showToday / showScheduled, SyncData.smartLists
  api/SmartListChanges.kt  → NOUVEAU : corps de POST / PATCH /smart-lists (toJson(id) pour la synchro)
  api/TodoApi.kt           → smartLists, smartTasks, create / update / deleteSmartList ; updateMe(showToday, showScheduled)
  api/ApiClient.kt         → les routes /smart-lists
  local/Entities.kt        → SmartListEntity ; profile.showToday / showScheduled
  local/LocalDatabase.kt   → version 4, MIGRATION_3_4
  local/LocalDao.kt        → smart_lists, openTasksWithPlace (tri), allTaskTags
  local/SmartMatch.kt      → NOUVEAU : le filtre (copie de sqlSmartMatch du serveur)
  local/LocalApi.kt        → les listes intelligentes sur la base (SmartContext), règles et messages du serveur
  sync/SyncEngine.kt       → reçoit / supprime les listes intelligentes ; les envoie au premier lien ; corrige leurs id
                             quand un tag ou un niveau est fusionné
  ui/SmartLists.kt         → customSections, belongsCustom ; DueSection.color
  ui/SmartModel.kt         → kind (Aujourd'hui / Planifié) OU custom (liste perso) ; title, icon, color, empty
  ui/SmartScreen.kt        → en-tête avec le filtre, sections colorées ; SmartIconBox(icon, color)
  ui/HomeScreen.kt         → tuiles (Tile), smartLists chargées avec l'accueil, openCustomSmart
  ui/SmartListForm.kt      → NOUVEAU : le formulaire (canSave, toggle, toChanges)
  ui/SmartListSettings.kt  → NOUVEAU : partie Paramètres et panneau SmartListSheet
  ui/SettingsScreen.kt     → état et actions des listes intelligentes dans SettingsModel
  ui/ListMenus.kt          → ColorDot, IconChoice, SheetField partagés (n'étaient plus privés)
app/src/test/…/SmartMatchTest.kt     → filtre, sections, résumé, formulaire
app/src/androidTest/…                → LocalApiTest.listesIntelligentes, SyncEngineTest.listesIntelligentesSynchronisees
```

## Comment ça marche

### Le filtre, à l'identique du serveur

`SmartMatch.matches` recopie `sqlSmartMatch` (`api/internal/handlers/smartlists.go`) :

| Type | Une tâche à faire y est si… |
|---|---|
| par urgence | son niveau est un de ceux choisis |
| par tags, au moins un | elle porte au moins un des tags |
| par tags, tous | elle porte tous les tags choisis (et il en reste au moins un) |

Un tag ou un niveau **supprimé** reste dans la liste intelligente (comme sur le serveur), il est simplement ignoré : `toSmartList` ne garde que ceux qui existent.

### Le tri, à l'identique du serveur

`smartTasks` : par urgence du plus urgent (liste « par urgence » seulement), puis échéance, heure (sans échéance ni heure en dernier), position de la liste, du groupe, de la tâche. D'où `openTasksWithPlace`, qui donne les positions de la liste et du groupe avec chaque tâche.

### Base locale v4

| Version | Changement |
|---|---|
| 4 | table `smart_lists` (tags et niveaux en id séparés par des virgules) ; `profile.showToday`, `profile.showScheduled` (1 par défaut) |

### Synchronisation

- Créer / modifier / supprimer une liste intelligente est noté dans la file comme le reste (`POST /smart-lists` avec son `id`, `PATCH`, `DELETE`).
- `GET /sync` envoie déjà `smart_lists` et les suppressions `smart_list` (8-4) : elles sont maintenant écrites dans la base.
- Au **premier lien**, les listes intelligentes du téléphone partent avec le reste. Quand un tag ou un niveau du téléphone est fusionné avec celui du même nom sur le serveur, les listes intelligentes qui le citaient citent désormais celui du serveur (`replaceInSmartLists`).
- `show_today` / `show_scheduled` sont des réglages **du compte** (`PATCH /me`), comme sur le site.

### L'écran : un seul modèle pour les deux sortes

`SmartModel` reçoit soit `kind` (Aujourd'hui / Planifié), soit `custom` (une liste perso). Tout le reste est commun (`TaskStore` : chargement, panneau, sélection, modifications). Seuls changent : le chargement (`dueTasks` ou `smartTasks`), les sections (`SmartLists.sections` ou `customSections`) et la règle « a-t-elle encore sa place ? » (`belongs` ou `belongsCustom`).

## Lancer et tester

```bash
cd android && ./gradlew assembleDebug testDebugUnitTest
```

```bash
cd android && ./gradlew connectedDebugAndroidTest
```

### Vérifié

- **91 tests JVM** (dont `SmartMatchTest` : filtre urgence / au moins un / tous, sections par urgence et par tags, une tâche qui quitte la vue, résumé, formulaire et son JSON) et **20 tests sur l'émulateur** (dont refus comme le serveur, compteur, tri par urgence puis échéance, « tous » les tags, tag supprimé ignoré, masquer, supprimer, tuiles ; synchro : envoi avec l'id, réception, suppression) ;
- **sur l'émulateur, avec le vrai serveur** :
  - téléphone vide relié au compte : listes, tags et niveaux du serveur arrivent ;
  - Paramètres › Nouvelle liste intelligente « Urgent », par urgence, Haute et Moyenne, orange → sur l'accueil (3 tâches) **et sur le serveur** (même nom, type, niveaux, couleur) ;
  - « Urgent » ouverte : sections Haute (rouge) puis Moyenne (orange), filtre sous le titre ;
  - cocher la tâche « Moyenne » : elle quitte la vue, la section vide disparaît ;
  - interrupteur d'Aujourd'hui → la tuile disparaît de l'accueil, `show_today = false` sur le serveur.

## Où regarder en cas de problème

| Symptôme | Cause probable | Que faire |
|---|---|---|
| Une tâche apparaît sur le site mais pas dans l'app (ou l'inverse) | Filtre différent | Comparer `SmartMatch` et `sqlSmartMatch` ; ajouter un cas à `SmartMatchTest` |
| Une liste intelligente ne montre plus rien après la connexion à un serveur | Tag ou niveau fusionné, id pas remplacé | `SyncEngine.replaceInSmartLists`, table `smart_lists` (Database Inspector) |
| « Créer » reste grisé | Pas de nom, ou aucun tag / niveau choisi | `SmartListForm.canSave` |
| Plantage au lancement après mise à jour | Migration 3 → 4 | `LocalDatabase.MIGRATION_3_4` (le SQL doit être exactement celui que Room attend) |
