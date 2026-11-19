# Étape 8-5 : app Android, synchronisation avec le serveur

## Objectif

Fin de la demande du 2026-11-19 : l'app marche sans serveur (8-3), **et si on ajoute un serveur, elle s'y synchronise**.

Depuis cette étape, **l'app lit et écrit toujours sa propre base**, avec ou sans serveur. Le serveur n'est plus qu'un endroit avec lequel elle se synchronise. Conséquences :

- l'app s'ouvre **tout de suite**, même hors ligne ou serveur arrêté ;
- on peut tout modifier hors ligne : les modifications partent quand le serveur répond ;
- ce qui est fait sur le site (ou par Claude via le MCP) arrive sur le téléphone ;
- au premier lien, les listes déjà sur le téléphone sont **ajoutées** au compte du serveur (fusion).

## Ce qui change pour l'utilisateur

- **Paramètres › Serveur**, avec un serveur : son adresse, « Synchronisé à 18:42 · 2 modifications en attente », l'erreur de la dernière tentative s'il y en a une, et « Synchroniser maintenant ». L'email du compte s'affiche dans « Compte ».
- **Tirer vers le bas** (accueil, liste, Aujourd'hui / Planifié) : synchronise, puis relit.
- **Se connecter depuis le mode sans serveur** : les listes du téléphone rejoignent celles du compte.
- **Se déconnecter** : ce qui attend encore est envoyé (si le serveur répond), puis la session est fermée. Les listes **restent sur le téléphone**. « Utiliser l'app sans serveur » les retrouve, sans synchronisation.
- L'écran « Serveur injoignable » au lancement a disparu : il n'a plus de raison d'être.

## Fichiers

```
android/app/src/main/java/io/github/acssio/bettertodo/
  sync/SyncEngine.kt      → NOUVEAU : envoi de la file, récupération, premier lien, état pour l'écran
  sync/SyncStatus.kt      → NOUVEAU : « Synchronisé à 18:42 · 2 modifications en attente »
  local/Entities.kt       → OutboxEntity (file d'attente), SyncStateEntity (curseur, compte relié), profile.email
  local/LocalDatabase.kt  → version 3 : migrations 1 → 2 (outbox, sync_state) et 2 → 3 (profile.email)
  local/LocalDao.kt       → file d'attente, @Upsert, fusion de tags et d'urgences
  local/LocalApi.kt       → chaque modification est aussi notée dans la file (record), saveProfile
  api/ApiClient.kt        → sync(since), replay(…) (implémente SyncServer) ; 502/503/504 = « Serveur injoignable »
  api/Models.kt           → SyncData, SyncGroup, SyncDeletion
  ui/App.kt               → machine d'état réécrite : les listes viennent toujours de LocalApi, SyncEngine en plus
  ui/HomeScreen.kt        → rechargement après une synchro (version), tirer = synchroniser
  ui/TaskStore.kt         → beforeRefresh, reloadQuietly
  ui/SettingsScreen.kt    → partie Serveur avec l'état de la synchronisation
app/src/test/…/SyncTest.kt                 → pendingIds, SyncStatus
app/src/androidTest/…/SyncEngineTest.kt    → NOUVEAU : SyncEngine avec un faux serveur
```

## Comment ça marche

### Vue d'ensemble

```
 écrans ──► LocalApi ──► base Room (bettertodo.db)
               │
               └── recording (serveur relié) : la requête REST équivalente est notée dans outbox
                                                    │
 SyncEngine.syncNow()                               ▼
   1. push : outbox, dans l'ordre ──────────► serveur (POST avec "id", PATCH, DELETE)
   2. pull : GET /sync?since=curseur ◄─────── ce qui a changé ou disparu
             → écrit dans la base (@Upsert, suppressions), curseur gardé
   3. version++ → les écrans se rechargent
```

### 1. La file d'attente (outbox)

Chaque méthode de `LocalApi` qui modifie quelque chose appelle `record(méthode, chemin, corps)` **dans la même transaction** que la modification : jamais l'une sans l'autre. Le corps est exactement ce que le serveur attend, avec les id choisis sur le téléphone. Exemples :

| Sur le téléphone | Noté dans la file |
|---|---|
| nouvelle liste | `POST /lists {"id", "default_group_id", "name", "folder_id"}` |
| tag ajouté à une tâche (nouveau nom) | `POST /tags {"id", "name"}` **puis** `PATCH /tasks/<id> {"tags": […]}` |
| cocher | `PATCH /tasks/<id> {"done": true}` (le JSON de `TaskChanges`, tel quel) |
| supprimer | `DELETE /tasks/<id>` |

Sans serveur, `recording` est faux : rien n'est noté, la file reste vide.

### 2. L'envoi (push)

Les requêtes partent **dans l'ordre**. Selon la réponse :

- **2xx** : envoyée, retirée de la file ;
- **400, 404, 409** : le serveur refuse (élément supprimé sur le site entre-temps, nom de tag déjà pris…). Elle est **abandonnée** : le serveur garde sa version, et la récupération la remet sur le téléphone. Une ligne est écrite dans Logcat (`tag:SyncEngine`) ;
- **réseau coupé (0) ou 5xx** : on s'arrête là, **rien n'est perdu**, on réessaiera ;
- **401** : session expirée, retour à la connexion. La file est gardée et partira après la reconnexion au même compte.

Grâce à la 8-4, renvoyer une création déjà reçue est sans danger (le serveur répond 200 avec l'existant), et une suppression déjà faite répond 404 (abandonnée, donc considérée comme faite).

### 3. La récupération (pull)

Seulement quand la file est vide, sinon on écraserait nos propres modifications pas encore envoyées. Les éléments qui ont encore une requête en attente sont de toute façon sautés (`pendingIds`).

- **@Upsert** (insérer ou mettre à jour), **jamais `REPLACE`** : REPLACE supprime puis réinsère, et la suppression partirait en cascade (toutes les tâches d'une liste…).
- **Tags du même nom** : un tag créé sur le téléphone et un tag du même nom sur le serveur sont fusionnés (`upsertTagByName`). Les tâches gardent leur tag, celui du serveur.
- **Suppressions** : `deleted` du serveur, appliquées avec les mêmes cascades que PostgreSQL.
- Une écriture refusée par la base (parent absent) est ignorée (`safely`) : la synchronisation suivante corrigera.
- Le **compte** (nom, langue, vue, Quick task, email) vient du serveur.

**Le serveur reste l'arbitre** : les positions sont celles qu'il a renumérotées, et pour un même champ modifié à deux endroits, c'est la dernière modification reçue par le serveur qui gagne. Chaque PATCH ne contient que ses champs : titre changé sur le site et tâche cochée sur le téléphone, les deux sont gardés.

### 4. Le premier lien (link)

Au premier passage avec un compte (`sync_state.linked = false`) :

1. ce qui attendait encore part ;
2. `GET /sync` complet : les **niveaux d'urgence du même nom** sont ceux du serveur (les tâches du téléphone passent dessus, les doublons disparaissent). Même chose pour les tags ;
3. `snapshot()` note dans la file **tout le reste du téléphone** comme des créations, dans l'ordre des dépendances et d'affichage. Un élément que le serveur a déjà (même id) est un rejeu : rien n'est doublé ;
4. envoi, puis récupération complète.

`sync_state.userId` retient **quel compte** est relié : se connecter à un **autre** compte vide la file et refait un premier lien (`useAccount`), pour ne jamais envoyer à B ce qui était destiné à A.

### 5. Quand ça synchronise

- à l'ouverture de l'app (avec un serveur) ;
- **1,5 s après une modification** (`schedule`, chaque nouvelle modification repousse le délai) ;
- en tirant vers le bas, et avec « Synchroniser maintenant » ;
- avant de se déconnecter.

Une seule synchronisation à la fois (`Mutex`). Pas de synchronisation en arrière-plan quand l'app est fermée pour l'instant (WorkManager, avec les notifications de la 8-7 si besoin).

### Base locale : versions

| Version | Changement | Migration |
|---|---|---|
| 1 | tables de la 8-3 | |
| 2 | `outbox`, `sync_state` | `MIGRATION_1_2` |
| 3 | `profile.email` | `MIGRATION_2_3` |

## Lancer et tester

```bash
cd android && ./gradlew assembleDebug testDebugUnitTest
```

```bash
cd android && ./gradlew connectedDebugAndroidTest
```

> Attention : `connectedDebugAndroidTest` **désinstalle l'app** de l'émulateur à la fin (ses données avec).

### Vérifié

- **82 tests JVM** (dont `SyncTest`) et **18 tests sur l'émulateur** : `LocalApiTest` (11) et `SyncEngineTest` (7), qui couvrent : sans serveur rien n'est noté ; chaque modification part dans l'ordre (tag avant la tâche, `default_group_id`) ; réseau coupé → rien de perdu, 404 → abandonnée et la suite part ; la récupération écrit la base puis applique une suppression ; fusion d'un tag du même nom ; premier lien avec fusion des niveaux d'urgence ; changer de compte remet à zéro ;
- **migration réelle** : données créées avec l'app de la 8-3 (base v1), puis nouvelle app installée par-dessus → tout est là (v1 → v2), puis v2 → v3 ;
- **sur l'émulateur, avec le vrai serveur** :
  - premier lien depuis le mode sans serveur : la liste « maison » du téléphone rejoint celles du compte, **sans doublon** de niveaux d'urgence ni du tag « maison », avec l'heure et l'urgence Haute (celle du serveur) ;
  - tâche renommée sur le serveur → arrive en tirant vers le bas ;
  - **API arrêtée** : cocher et ajouter une tâche marchent, Paramètres affiche « 2 modifications en attente » et l'erreur ; API relancée, « Synchroniser maintenant » → les deux sont sur le serveur ;
  - Quick task avec le serveur en marche → sur le serveur **sans rien toucher** (1,5 s) ;
  - tâche supprimée sur le serveur → disparaît du téléphone ;
  - **lancement à froid avec l'API arrêtée** : les listes s'ouvrent tout de suite.

## Limites connues

- Une modification encore en attente au moment d'une **déconnexion ratée** (serveur injoignable) n'est pas envoyée : elle reste sur le téléphone, mais en se reconnectant, la version du serveur la remplacera.
- Pas de synchronisation quand l'app est fermée (voir 8-7).
- Un **téléphone = un compte** : se connecter à un autre compte y ajoute les listes du téléphone (fusion).
- Les listes intelligentes personnalisées sont reçues par `/sync` mais pas encore gardées par l'app (8-6).

## Où regarder en cas de problème

| Symptôme | Cause probable | Que faire |
|---|---|---|
| « N modifications en attente » ne descend pas | Serveur injoignable ou 5xx | Paramètres › Serveur (erreur affichée), Logcat `tag:ApiClient` |
| Une modification du téléphone n'apparaît pas sur le site | Requête refusée et abandonnée (400 / 404 / 409) | Logcat `tag:SyncEngine` : « … refusée (code) » |
| Ce qui est fait sur le site n'arrive pas | Pas encore synchronisé, ou file pas vide (la récupération attend) | Tirer vers le bas ; « Synchroniser maintenant » |
| Doublons après le premier lien | Noms différents (« Haute » / « haute ») : la fusion compare les noms exacts | Renommer puis supprimer le doublon |
| Plantage au lancement après un changement de table | Version ou migration Room manquante | `LocalDatabase` : nouvelle version + `Migration` |
| Voir la file d'attente | | Android Studio › App Inspection › Database Inspector › `outbox` |
