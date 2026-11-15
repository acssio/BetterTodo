# Étape 4 : intégration MCP (Claude lit et écrit tes tâches)

## Objectif

Permettre à Claude (Claude Code, Claude Desktop) de **lire et modifier tes tâches** : « qu'est-ce que j'ai à faire aujourd'hui ? », « ajoute *Appeler le garage* pour vendredi dans Perso », « coche *Acheter du pain* ».

Deux morceaux :

- **4a, jetons d'accès personnels** : un programme peut utiliser l'API sans ton mot de passe. Les jetons se créent et se révoquent dans **Paramètres › Jetons d'accès**.
- **4b, serveur MCP** : un petit programme Go (`mcp/`) qui donne à Claude des outils comme `create_task` ou `update_task`. Il appelle l'API avec un jeton.

Le tuto pour brancher Claude sur ton serveur est dans **[docs/connecter-claude.md](../connecter-claude.md)**.

## C'est quoi MCP ?

**MCP (Model Context Protocol)** est un protocole ouvert qui sert à donner des outils à une IA. Un *serveur MCP* annonce une liste d'outils. Chaque outil a un nom, une description en langage naturel et des paramètres décrits en JSON Schema. Claude lit cette liste, puis décide seul quand appeler quel outil.

```
 Ton Mac                                                         Ton serveur
┌──────────────────────────────────────────────┐               ┌──────────────────────────┐
│  Claude Code / Desktop                       │               │  nginx (web)             │
│        │  lance le programme, puis           │    HTTPS      │    │ /api                │
│        │  JSON-RPC sur stdin / stdout        │  Bearer btd_… │    ▼                     │
│        ▼                                     │ ────────────► │  api (Go) ──► PostgreSQL │
│  bettertodo-mcp ── ApiClient ────────────────┼───────────────┤                          │
└──────────────────────────────────────────────┘               └──────────────────────────┘
```

- Le transport est **stdio** : Claude lance `bettertodo-mcp` et lui parle par l'entrée et la sortie standard, une ligne JSON par message. Il n'y a rien à ouvrir sur le serveur, ni rien de nouveau dans Docker.
- `bettertodo-mcp` est **un client de l'API comme les autres**. Il ne touche jamais la base, ce qui respecte la décision « seul le serveur API parle à PostgreSQL ».
- **La sortie standard est réservée au protocole.** Un `fmt.Println` de débogage casserait la communication : les messages pour l'humain passent par `log`, sur la sortie d'erreur.

Pour suivre une conversation, voici à quoi ressemble l'appel d'un outil (simplifié) :

```json
→ {"jsonrpc":"2.0","id":3,"method":"tools/call","params":{"name":"update_task","arguments":{"task_id":"…","done":true}}}
← {"jsonrpc":"2.0","id":3,"result":{"content":[{"type":"text","text":"{\"id\":\"…\",\"title\":\"Pain\",\"done\":true}"}]}}
```

## Fichiers

```
api/
  migrations/007_api_tokens.sql      → table api_tokens (hash du jeton, nom, dates)
  internal/auth/apitokens.go         → préfixe "btd_", NewAPIToken, APITokenStore.UserID
  internal/auth/middleware.go        → RequireUser accepte session OU jeton ; RequireSession, session seulement
  internal/auth/sessions.go          → un jeton de session ne commence jamais par "btd_"
  internal/handlers/tokens.go        → TokenHandler : GET/POST /me/tokens, DELETE /me/tokens/{id}
  cmd/server/main.go                 → routes ; sessionOnly(…) pour les routes sensibles
  tests/bettertodo-api.postman_collection.json → + 13 requêtes sur les jetons
web/src/
  api/types.ts, api/ApiClient.ts     → ApiToken, getApiTokens / createApiToken / deleteApiToken
  components/SettingsDialog.tsx/.css → partie « Jetons d'accès »
mcp/                                 → module Go séparé (son propre go.mod)
  main.go                            → lit la config, crée le serveur MCP, transport stdio
  apiclient.go                       → ApiClient : appels HTTP avec "Authorization: Bearer btd_…"
  tools.go                           → Tools : outils des tâches (lecture, création, modification)
  tools_structure.go                 → outils des groupes de listes, listes et groupes (créer, renommer)
  palette.go                         → couleurs et icônes des listes, copiées de web/src/components/listIcons.ts
  types.go                           → réponses de l'API (champs utiles seulement)
  tools_test.go, palette_test.go     → tests des fonctions pures + synchro de la palette avec la page web
```

## 4a : jetons d'accès personnels

### En base

```sql
api_tokens (id, user_id → users ON DELETE CASCADE, name, token_hash UNIQUE, created_at, last_used_at)
```

C'est le même principe que les sessions : **seul le hash SHA-256** est en base. Le jeton en clair n'est envoyé qu'une fois, dans la réponse de création. Il n'y a **pas d'expiration** : le jeton vaut jusqu'à sa révocation, sinon il faudrait reconfigurer Claude tous les 30 jours. `last_used_at` est mis à jour à chaque requête, ce qui permet de repérer un jeton qui ne sert plus.

### Session ou jeton ?

Les jetons commencent par **`btd_`**. Le middleware regarde ce préfixe pour savoir quelle table interroger :

```
jeton reçu ──┬── commence par "btd_" ──► api_tokens   (401 « Jeton d'accès invalide ou révoqué »)
             └── sinon ────────────────► sessions     (401 « Session invalide ou expirée »)
```

Un jeton de session est aléatoire, il pourrait donc commencer par `btd_` par hasard (environ une chance sur 17 millions). `SessionStore.Create` en tire alors un autre.

### Ce qu'un jeton ne peut pas faire

Un jeton est écrit en clair dans la configuration de Claude. S'il fuit, il ne doit pas permettre de prendre le contrôle du compte. Les routes sensibles passent donc par `RequireSession` et répondent **403** à un jeton :

| Route | Avec un jeton |
|---|---|
| Listes, groupes, tâches, tags, urgences, `GET`/`PATCH /me` | ✅ |
| `POST /me/password` (changer le mot de passe) | ❌ 403 |
| `/me/tokens` (créer, lister, révoquer des jetons) | ❌ 403 |
| `/admin/*` (console admin) | ❌ 403 |
| `POST /auth/logout` | ❌ 403 (il n'y a pas de session à fermer) |

### Routes

| Méthode | Route | Effet |
|---|---|---|
| GET | `/me/tokens` | Les jetons du compte (sans le jeton lui-même), du plus récent au plus ancien |
| POST | `/me/tokens` | `{"name": "…"}` → 201 avec `"token": "btd_…"`, **une seule fois**. 20 jetons au maximum |
| DELETE | `/me/tokens/{id}` | Révoque : le jeton est refusé dès la requête suivante (204, ou 404) |

**Note :** changer son mot de passe, ou le faire changer par l'admin, ferme les sessions mais **ne révoque pas les jetons**. Pour couper l'accès à Claude, il faut révoquer le jeton. Supprimer le compte supprime aussi ses jetons (`ON DELETE CASCADE`).

### Page web

Il y a une nouvelle partie **Jetons d'accès**, en bas des Paramètres :
- **Créer un jeton** : on donne un nom, puis un encadré affiche le jeton avec un bouton **Copier**. Si le navigateur refuse le presse-papiers (site en HTTP hors localhost, navigateur intégré de l'app Claude…), le jeton est sélectionné et un message propose Cmd+C ;
- chaque jeton affiche « Jamais utilisé » ou « Utilisé le … », et une corbeille pour le révoquer, avec confirmation.

## 4b : le serveur MCP

### Les outils

| Outil | Lecture / écriture | Ce qu'il fait |
|---|---|---|
| `list_lists` | lecture | Groupes de listes (même vides) et listes, avec le nombre de tâches à faire |
| `get_list` | lecture | Groupes et tâches d'une liste (tâches faites seulement avec `include_done`) |
| `get_due_tasks` | lecture | Échéances toutes listes confondues : `until` = `"today"`, une date, ou vide |
| `list_tags_and_urgency_levels` | lecture | Tags existants et noms des niveaux d'urgence |
| `create_task` | écriture | Crée une tâche (groupe par son nom, sinon le premier), avec échéance, urgence, tags et notes |
| `update_task` | écriture | Change seulement les champs donnés ; `done: true` coche la tâche |
| `list_colors_and_icons` | lecture | Les 8 couleurs (nommées : rouge, bleu…) et les 136 icônes de liste, avec leurs mots-clés |
| `create_folder` / `rename_folder` | écriture | Créer / renommer un groupe de listes (barre latérale) |
| `create_list` / `rename_list` | écriture | Créer une liste (groupe de listes par son nom, couleur, icône) / la renommer |
| `create_group` / `rename_group` | écriture | Créer un groupe de tâches à la fin d'une liste / le renommer |

**Aucun outil ne supprime** : c'était la demande. Claude peut cocher une tâche, créer ou renommer une liste ou un groupe, mais rien effacer. Il ne déplace ni ne réordonne rien non plus.

### Couleur et icône d'une liste

Claude ne peut choisir que dans la palette de la page web. Une icône hors de la liste fixe (`listIcons.ts`) s'afficherait comme « list ». Le serveur MCP ne peut pas lire le code de la page web : `palette.go` en est une **copie**, avec un nom français pour chaque couleur. `TestPaletteSynchroniseeAvecLaPageWeb` relit `listIcons.ts` et échoue si les deux ne correspondent plus. **Quand tu ajoutes une icône dans la page web, ajoute-la aussi dans `palette.go`.**

`create_list` vérifie la couleur, l'icône et le groupe de listes **avant** de créer la liste. En cas d'erreur, le message donne les choix possibles.

Quelques choix :
- **Claude manipule des noms, l'API des identifiants.** Claude écrit `"urgency": "Haute"`, et `findUrgency` le traduit en `urgency_id`, sans tenir compte des majuscules. Si le nom est inconnu, l'erreur liste les niveaux possibles, et Claude se corrige seul.
- **Erreurs.** Quand un outil renvoie une `error` en Go, le SDK la transforme en résultat `isError: true` que Claude lit. Les messages de l'API (« due_date doit être une date au format AAAA-MM-JJ »…) lui arrivent donc tels quels.
- **`create_task` en deux appels**, comme la page web : `POST /groups/{id}/tasks` avec le titre, puis `PATCH /tasks/{id}` pour le reste. L'urgence est vérifiée **avant** la création, pour ne pas laisser une tâche à moitié créée.
- **« Aujourd'hui »** : la date du jour vient de l'ordinateur où tourne `bettertodo-mcp`, dans son fuseau, comme pour la page web. Elle est renvoyée dans la réponse (`"today"`) pour que Claude sache quel jour on est.
- **Réponses compactes** : les champs vides sont omis et les tâches faites masquées par défaut. Chaque réponse prend de la place dans la conversation de Claude.

### SDK

Le serveur utilise le SDK MCP officiel pour Go, [`github.com/modelcontextprotocol/go-sdk`](https://github.com/modelcontextprotocol/go-sdk), en v1.8.0. `mcp.AddTool` déduit le JSON Schema des paramètres à partir de la structure Go :

```go
type getListInput struct {
    ListID      string `json:"list_id" jsonschema:"id de la liste (voir list_lists)"`
    IncludeDone bool   `json:"include_done,omitempty" jsonschema:"vrai pour inclure aussi les tâches faites"`
}
```

Sans `omitempty`, le champ est **obligatoire**. Le texte de `jsonschema:"…"` est la description que lit Claude.

## Lancer et tester

### Tests automatiques

```bash
cd api && go test ./...
```

```bash
cd mcp && go test ./...
```

```bash
cd web && npm test
```

Collections Postman, contre une instance locale :

```bash
/Users/acssiociardo/.local/bin/postman collection run api/tests/bettertodo-api.postman_collection.json --env-var adminEmail=… --env-var adminPassword=…
```

### Essayer le serveur MCP à la main

L'outil officiel **MCP Inspector** ouvre une page web pour appeler les outils un par un (il faut Node.js) :

```bash
cd mcp && go build -o bettertodo-mcp .
```

```bash
BETTERTODO_URL=http://localhost:8000 BETTERTODO_TOKEN=btd_… npx @modelcontextprotocol/inspector ./bettertodo-mcp
```

### Ce qui a été vérifié

- `go test` (api, mcp), `npm test`, `tsc` et oxlint passent ;
- les deux collections Postman passent, contre une pile Docker locale neuve (62 et 80 requêtes) ;
- les routes avec curl : avec un jeton, `/lists` et `/me` répondent 200 ; `/me/tokens`, `/me/password` et `/admin/users` répondent 403 ; un faux jeton reçoit 401 ;
- les outils, en parlant au binaire en JSON-RPC comme le fait Claude : création avec échéance, urgence (« haute » en minuscules), tags et notes ; `get_due_tasks` avec `today` et sans date ; cocher une tâche en enlevant l'échéance et l'urgence ; erreurs pour un groupe inconnu, une urgence inconnue (sans créer de tâche), une tâche introuvable ou une modification vide ; jeton révoqué → 401 ;
- les outils d'organisation : créer et renommer un groupe de listes, une liste (avec groupe de listes en minuscules, couleur « Vert », icône `shopping-cart`) et un groupe ; créer une tâche dans le nouveau groupe ; erreurs pour une couleur hors palette, une icône inconnue, un groupe de listes inconnu (ou aucun), un nom vide, un groupe introuvable ;
- la page web, avec de vrais clics dans le navigateur : créer un jeton, l'encadré, Copier (le repli Cmd+C quand le presse-papiers est refusé), « Jamais utilisé » / « Utilisé le … », révoquer avec la boîte de confirmation.

**Pas vérifié** : le branchement dans Claude Code ou Claude Desktop sur ton vrai serveur. C'est le tuto [connecter-claude.md](../connecter-claude.md).

## En cas de problème

| Symptôme | Où regarder |
|---|---|
| `bettertodo-mcp` quitte tout de suite | Les messages sont sur la sortie d'erreur : `BETTERTODO_URL` / `BETTERTODO_TOKEN` manquants, ou jeton sans `btd_` (`mcp/main.go`) |
| Outils en erreur « 401 : Jeton d'accès invalide ou révoqué » | Jeton révoqué ou mal copié. `APITokenStore.UserID` (`api/internal/auth/apitokens.go`) |
| « 403 : Un jeton d'accès ne peut pas utiliser cette route » | Route protégée par `sessionOnly` dans `api/cmd/server/main.go` : c'est voulu |
| « impossible de joindre BetterTodo » | `BETTERTODO_URL` : l'adresse de la **page web**, sans `/api` (`NewApiClient` ajoute `/api/v1`) |
| Réponse 404 sur tout | URL avec `/api` en trop, ou mauvais port |
| Claude ne voit pas les outils | Chemin du binaire pas absolu, ou application non relancée. Logs de Claude Desktop : `~/Library/Logs/Claude/mcp-server-bettertodo.log` |
| Ajouter un outil | Une méthode sur `Tools` + un `mcp.AddTool` dans `Register` (`mcp/tools.go`) ou `registerStructure` (`mcp/tools_structure.go`), puis rebuild du binaire et relance de Claude |
| `go test` échoue sur la palette | Une icône ou une couleur a changé dans `listIcons.ts` : reporte-la dans `mcp/palette.go` |
| Voir les jetons en base | `docker compose exec db psql -U bettertodo -c "SELECT name, created_at, last_used_at FROM api_tokens"` |

## Plus tard

- **Mode distant (HTTP)** : servir MCP directement depuis le serveur (`/mcp`). Cela permettrait de l'utiliser depuis claude.ai sur le web et le téléphone, mais il faudrait OAuth.
- Rechercher une tâche par son titre dans toutes les listes (il n'y a pas encore de route de recherche dans l'API).
