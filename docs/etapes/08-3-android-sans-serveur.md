# Étape 8-3 : app Android sans serveur (base locale Room)

## Objectif

Demande du 2026-11-19 : **l'app doit marcher sans serveur**, et se synchroniser avec un serveur si on en ajoute un dans les Paramètres.

Ce morceau fait la première moitié : l'app a maintenant sa **propre base de données** (Room, c'est-à-dire SQLite sur le téléphone) et un **mode sans serveur**, proposé au premier lancement. La synchronisation avec un serveur viendra à la 8-4 (serveur) et à la 8-5 (app).

## Ce qui change pour l'utilisateur

- **Premier lancement** : écran de bienvenue, « Commencer » (sans serveur) ou « J'ai un serveur BetterTodo ».
- **Sans serveur** : tout marche comme avant (listes, groupes, tâches, heure, tags, urgences, Aujourd'hui / Planifié, Quick task, glisser, Paramètres), mais les données restent sur le téléphone. Pas de bouton de déconnexion, pas de mot de passe ; Paramètres › Serveur › « Se connecter à un serveur ».
- **Écran de connexion** : nouveau lien « Utiliser l'app sans serveur ».
- **Limite de la 8-3** : connecté à un serveur, l'app montre les données **du serveur** (comme avant). Les listes du téléphone sont gardées, et on les retrouve en revenant au mode sans serveur, mais elles ne sont pas encore envoyées au serveur : c'est la 8-5.

## Fichiers

```
android/
  gradle/libs.versions.toml, build.gradle.kts, app/build.gradle.kts → Room 2.8.5, KSP, tests instrumentés
  app/src/main/java/io/github/acssio/bettertodo/
    api/TodoApi.kt          → NOUVEAU : tout ce que les écrans demandent (une méthode par route)
    api/ApiClient.kt        → implémente TodoApi (override), rien d'autre ne change
    local/Entities.kt       → NOUVEAU : les tables (même schéma que PostgreSQL, sans user_id)
    local/LocalDao.kt       → NOUVEAU : les requêtes SQL (Room les vérifie à la compilation)
    local/LocalDatabase.kt  → NOUVEAU : la base « bettertodo.db », version 1
    local/Ordering.kt       → NOUVEAU : ordering.move du serveur
    local/LocalApi.kt       → NOUVEAU : implémente TodoApi sur la base, avec les règles du serveur
    data/SessionStore.kt    → mode « local » / « server » (MODE_LOCAL, MODE_SERVER, useLocal())
    ui/App.kt               → écrans Welcome, Home(api, user, server, sessionKey), useLocal(), showLogin()
    ui/WelcomeScreen.kt     → NOUVEAU : premier lancement
    ui/LoginScreen.kt       → « Utiliser l'app sans serveur »
    ui/HomeScreen.kt        → pas de déconnexion sans serveur ; Paramètres reçoivent le serveur
    ui/SettingsScreen.kt    → partie « Serveur » sans serveur ; email et mot de passe seulement avec
    ui/*Model.kt, PlacePicker.kt, TaskStore.kt → reçoivent un TodoApi au lieu d'un ApiClient
  app/src/test/…/OrderingTest.kt           → ordre (JVM)
  app/src/androidTest/…/LocalApiTest.kt    → NOUVEAU : LocalApi sur une vraie base, sur l'émulateur
```

## Comment c'est organisé

### Une interface, deux façons de répondre

```
HomeViewModel, BoardModel, SmartModel, TaskStore, SettingsModel, QuickTaskModel, PlacePicker
                                   │  ne connaissent que
                                   ▼
                               TodoApi
                     ┌─────────────┴─────────────┐
                ApiClient                     LocalApi
           (HTTP vers le serveur)     (Room : SQLite du téléphone)
```

- Les écrans n'ont **pas changé** : seul le type de `api` est passé de `ApiClient` à `TodoApi`. Ils gardent leurs modifications optimistes et leurs messages d'erreur.
- `LocalApi` répond **comme le serveur** : mêmes règles et mêmes erreurs (`ApiError` 400 / 404 / 409, mêmes messages). Ce qui est recopié du Go (`api/internal/handlers/`) :
  - noms nettoyés et limités (100 caractères ; titre 500 ; notes 10 000 ; tag 50, sans « # ») ;
  - **ordre** : un nouvel élément va à la fin (`nextPosition` = MAX + 1) ; un déplacement renumérote 0, 1, 2… (`Ordering.move`, copie de `ordering.move`) ;
  - une nouvelle liste reçoit son **groupe par défaut** (« Tâches » / « Tasks » selon la langue), la vue par défaut du compte, et masque les tâches faites ;
  - **pas d'heure sans date**, et `due_date: null` enlève aussi l'heure ;
  - tags donnés par leur nom, créés s'ils n'existent pas ; 409 si un nom est déjà pris ;
  - Aujourd'hui / Planifié : même tri (date, heure sans heure en dernier, liste, groupe, tâche) ;
  - premier accès : le compte local « Moi » et les **3 niveaux d'urgence** de départ (Basse, Moyenne, Haute).
- Les PATCH (`TaskChanges`, `ListChanges`) sont lus **dans leur JSON** par `LocalApi` (clé absente / `null` / valeur), exactement comme le serveur lit `Optional[T]`. Avantage pour la 8-5 : ce JSON est aussi ce qu'il faudra envoyer au serveur.
- Plusieurs écritures liées passent par `db.withTransaction { }` : tout ou rien.

### La base

- Mêmes tables que PostgreSQL, sans `user_id` (un seul compte par téléphone) ; `profile` remplace la ligne `users`.
- **Les id sont des UUID générés par l'app** : à la synchronisation, ils resteront les mêmes sur le serveur.
- **Clés étrangères** avec les mêmes suppressions que le serveur : liste → groupes → tâches → liens de tags en cascade ; groupe de listes supprimé → listes hors groupe ; niveau d'urgence supprimé → tâches sans urgence. Vérifié par `suppressionsEnCascade`.
- `version = 1`. **Changer une table = augmenter la version et écrire une migration Room**, jamais de destruction automatique (ce sont les données de l'utilisateur), comme la règle des migrations du serveur.

### La machine d'état de l'app

```
Loading ──(premier lancement)──→ Welcome ──(Commencer)──→ Home sans serveur
                                    └──(J'ai un serveur)──→ Login
        ──(mode local)──→ Home sans serveur
        ──(mode serveur)──→ comme avant (Home, Login, Unreachable, MustChangePassword)
Login ──(Utiliser l'app sans serveur)──→ Home sans serveur
Home sans serveur ──(Paramètres › Se connecter à un serveur)──→ Login
```

Le mode est gardé dans `SessionStore` (DataStore). Une installation d'avant la 8-3 avec un jeton reste en mode serveur.

## Lancer et tester

```bash
cd android && ./gradlew assembleDebug testDebugUnitTest
```

Tests de la base locale, **émulateur démarré** (vraie base SQLite, en mémoire) :

```bash
cd android && ./gradlew connectedDebugAndroidTest
```

Revoir l'écran de bienvenue (efface **toutes** les données de l'app sur l'émulateur) :

```bash
~/Library/Android/sdk/platform-tools/adb shell pm clear io.github.acssio.bettertodo
```

### Vérifié

- 80 tests JVM (dont `OrderingTest`) et 11 tests `LocalApiTest` sur l'émulateur : compte et niveaux de départ, groupe par défaut (fr / en), noms refusés, déplacer des tâches (dans un groupe, vers un autre, à la fin), pas d'heure sans date, cocher et vider, tags (doublons, 409, suppression), suppressions en cascade, Aujourd'hui / Planifié (tri par heure), niveaux d'urgence, listes ;
- sur l'émulateur, avec de vrais appuis : bienvenue → Commencer → accueil vide sans déconnexion ; nouvelle liste (s'ouvre, groupe « Tâches ») ; trois tâches ; échéance, heure 14:30, urgence Haute, tag ; Aujourd'hui = 1 ; **app tuée puis relancée : tout est là, toujours sans serveur** ;
- Paramètres › Se connecter à un serveur → connexion → les listes du serveur ; déconnexion → « Utiliser l'app sans serveur » → les listes du téléphone reviennent (accueil, pas l'ancienne page Paramètres : bug trouvé et corrigé, clé du `HomeViewModel` neuve à chaque ouverture).

## Où regarder en cas de problème

| Symptôme | Cause probable | Que faire |
|---|---|---|
| Erreur de compilation dans `LocalDao` | Requête SQL fausse (Room la vérifie) | Lire le message KSP : il cite la colonne ou la table |
| L'app plante au lancement après un changement de table | `version` de `LocalDatabase` pas augmentée, ou migration manquante | Ajouter une migration Room (ne jamais effacer la base de l'utilisateur) |
| Un comportement diffère du site | Règle du serveur mal recopiée dans `LocalApi` | Comparer avec `api/internal/handlers/<ressource>.go`, ajouter un cas à `LocalApiTest` |
| Une suppression ne supprime pas les tâches | Clés étrangères | `Entities.kt` (`onDelete`), test `suppressionsEnCascade` |
| L'écran de bienvenue revient à chaque lancement | Mode pas enregistré | `SessionStore.useLocal()` / `save()` |
| Voir le contenu de la base | | Android Studio › App Inspection › Database Inspector (`bettertodo.db`) |
