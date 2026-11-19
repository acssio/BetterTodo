# Étape 8-4 : serveur, synchronisation de l'app « local d'abord »

## Objectif

Depuis la 8-3, l'app Android garde ses données dans sa propre base et marche sans serveur. Pour qu'elle puisse **se synchroniser** avec un serveur (8-5), le serveur doit savoir trois choses de plus :

1. **accepter les id choisis par l'app** : une tâche créée hors ligne a déjà son UUID, elle doit garder le même sur le serveur ;
2. **supporter le rejeu** : si la réponse d'une création s'est perdue (réseau coupé), l'app la renverra. Le serveur ne doit pas créer de doublon ;
3. **dire ce qui a changé** depuis la dernière synchronisation, **suppressions comprises** (`GET /sync?since=…`).

Le site web et le serveur MCP ne changent pas : ils n'envoient pas d'id et n'appellent pas `/sync`.

## Fichiers

```
api/
  migrations/012_sync.sql            → NOUVEAU : table sync_deletions
  internal/handlers/sync.go          → NOUVEAU : checkClientID, recordDeletion, deleteRecorded, SyncHandler
  internal/handlers/folders.go       → POST avec "id" ; DELETE noté
  internal/handlers/lists.go         → POST avec "id" et "default_group_id" ; DELETE et « vider les faites » notés
  internal/handlers/groups.go, tasks.go, urgency.go, tags.go, smartlists.go → POST avec "id" ; DELETE noté
  cmd/server/main.go                 → route GET /api/v1/sync
  tests/generer_collection_donnees.py, bettertodo-api-donnees.postman_collection.json → partie « Synchronisation »
```

## Ce qui change dans l'API

### Créer avec un id choisi par l'app

Toutes les créations acceptent un champ facultatif **`id`** (un UUID) : `POST /folders`, `/lists`, `/lists/{id}/groups`, `/groups/{id}/tasks`, `/urgency-levels`, `/tags`, `/smart-lists`. Sans `id`, rien ne change.

| Cas | Réponse |
|---|---|
| id nouveau | **201**, l'élément est créé avec cet id |
| id déjà utilisé **par ce compte** | **200**, l'élément existant (c'est un rejeu : rien n'est créé) |
| id déjà utilisé par un **autre** compte | **409** « Cet id est déjà utilisé » |
| pas un UUID | **400** « id doit être un UUID » |

`POST /lists` accepte aussi **`default_group_id`** : l'id du groupe créé avec la liste. Sans lui, l'app aurait son groupe « Tâches » et le serveur en créerait un autre, avec un autre id.

Le tout est fait par `checkClientID` (dans `sync.go`), appelé au début de chaque `Create` :

```go
replay, ok := checkClientID(w, r, h.pool, "tasks", sqlOwnsTask, req.ID)
if !ok { return }            // 400 ou 409 déjà répondu
if replay { … renvoyer l'existant (200) … }
… INSERT … VALUES (COALESCE($7::uuid, gen_random_uuid()), …)   // l'id de l'app, ou un nouveau
```

### Les suppressions sont notées

Table **`sync_deletions`** (`user_id`, `entity`, `entity_id`, `deleted_at`). Chaque `DELETE` y ajoute une ligne **dans la même transaction** (`deleteRecorded`), de même que « vider les tâches faites » (une ligne par tâche).

Seul l'élément supprimé est noté. Ce qui part avec lui en cascade (les tâches d'une liste…) partira aussi en cascade sur le téléphone, qui a les mêmes clés étrangères (8-3).

Une suppression rejouée répond **404** : l'app la considérera comme faite.

### `GET /api/v1/sync?since=<curseur>`

Avec une session **ou** un jeton (route `private`, comme les autres). Réponse :

```json
{
  "cursor": "2026-11-19T17:41:28.989612Z",   // à renvoyer dans ?since= la prochaine fois
  "user": { … comme GET /me … },
  "folders": [ … ], "lists": [ … ], "groups": [ … avec list_id … ],
  "tasks": [ … avec tags (noms) … ], "urgency_levels": [ … ], "tags": [ … ], "smart_lists": [ … ],
  "deleted": [ { "type": "task", "id": "…" }, … ]   // folder, list, group, task, urgency_level, tag, smart_list
}
```

- **Sans `since`** : tout (première synchronisation), `deleted` vide ou presque.
- **Avec `since`** : seulement ce dont `updated_at` (ou `deleted_at`) est plus récent. Chaque table a déjà un `updated_at` tenu à jour par un trigger (migration 001), et déplacer, renommer, cocher ou changer les tags d'une tâche le met à jour.
- **Chevauchement d'une minute** (`syncOverlap`) : `updated_at` est l'heure du **début** de la transaction qui a modifié la ligne. Une transaction commencée juste avant le curseur mais validée juste après serait sinon oubliée. Renvoyer une ligne deux fois ne gêne pas : le téléphone réécrit la même valeur.
- Le curseur est pris **avant** les lectures, et toutes les lectures sont faites dans **une transaction `REPEATABLE READ`** : elles voient la base au même instant.
- Un utilisateur ne voit que **ses** éléments et **ses** suppressions (vérifié par Postman avec le compte B).

## Lancer et tester

```bash
cd api && go vet ./... && go test ./...
```

Reconstruire l'API (la migration 012 s'applique au démarrage) :

```bash
docker compose up -d --build --wait api
```

Tests de bout en bout (il faut un compte admin) :

```bash
/Users/acssiociardo/.local/bin/postman collection run api/tests/bettertodo-api-donnees.postman_collection.json --env-var adminEmail=… --env-var adminPassword=…
```

Après une modification de `generer_collection_donnees.py` :

```bash
python3 api/tests/generer_collection_donnees.py api/tests/bettertodo-api-donnees.postman_collection.json
```

### Vérifié

- `go vet` et `go test` ;
- collection « données » : **145 requêtes, 216 assertions, aucune en échec**, dont la nouvelle partie « Synchronisation » (synchro complète, curseur invalide, création avec id puis rejeu sans doublon, id qui n'est pas un UUID, id de A réutilisé par B → 409, liste + groupe par défaut avec leurs id, synchro depuis le curseur, vider les faites, chaque suppression notée, rejeu d'une suppression → 404, B ne voit pas les suppressions de A) ;
- collection « compte » : 66 requêtes, 72 assertions, aucune en échec.

**Problème trouvé, pas corrigé** : la requête **89** de la collection « données » (« Travail : tâches à faire triées par échéance », étape 7-5) a une erreur de syntaxe JavaScript dans son test (`'avec la liste d'origine'` : apostrophe non échappée). Son test ne s'exécute donc pas. Il suffit de remplacer l'apostrophe dans `generer_collection_donnees.py` puis de régénérer la collection.

## Où regarder en cas de problème

| Symptôme | Cause probable | Que faire |
|---|---|---|
| 409 « Cet id est déjà utilisé » | L'app réutilise un id d'un autre compte (base locale d'un autre compte ?) | Vérifier quel compte est connecté sur le téléphone |
| Deux groupes « Tâches » dans une liste créée par l'app | `default_group_id` pas envoyé | Le corps du `POST /lists` |
| Un appareil ne voit pas une suppression | Le `DELETE` ne passe pas par `deleteRecorded` | `SELECT * FROM sync_deletions ORDER BY deleted_at DESC LIMIT 10;` |
| La synchro renvoie toujours tout | `since` absent ou mal recopié | Renvoyer exactement le `cursor` reçu |
| `migrations appliquées` sans `012_sync.sql` au démarrage | Image pas reconstruite | `docker compose up -d --build api`, puis `docker compose logs api` |
