# Étape 7-5 : listes intelligentes personnalisées (API et affichage)

## Objectif

Demande de `ModifBetterTodo.md` : des listes « intelligentes » comme Aujourd'hui et Planifié, mais choisies par l'utilisateur :
- **par tags** : « afficher les tags… » → seulement les tâches qui ont ces tags ;
- **par urgence** : les tâches des niveaux d'urgence choisis, rangées du plus urgent au moins urgent ;
- affichées **sous** Aujourd'hui / Planifié, et on peut choisir lesquelles afficher (Aujourd'hui et Planifié compris).

Cette étape fait la **base, l'API et l'affichage**. La fenêtre « Nouvelle liste intelligente » et la partie Paramètres viennent en **7-6** (en attendant, on crée les listes par l'API).

Choix validés :
- seulement les **tâches à faire**, de toutes les listes ;
- par tags : **au moins un** des tags (`any`) ou **tous** (`all`) ;
- tags et niveaux choisis gardés dans des **tableaux d'UUID** (simple) : un tag ou un niveau supprimé est ignoré à la lecture ;
- Aujourd'hui / Planifié se masquent avec deux réglages du compte (`show_today`, `show_scheduled`).

## Fichiers

```
api/
  migrations/011_smart_lists.sql        → NOUVEAU : table smart_lists, users.show_today / show_scheduled
  internal/handlers/smartlists.go       → NOUVEAU : SmartListHandler (CRUD + tâches), validation
  internal/handlers/auth.go             → show_today / show_scheduled dans /me et PATCH /me
  internal/handlers/helpers_test.go     → TestSmartListValidation
  cmd/server/main.go                    → routes /smart-lists
  tests/generer_collection_donnees.py   → partie « Listes intelligentes personnalisées » (+ collection régénérée)
web/src/
  api/types.ts, api/ApiClient.ts        → SmartList, getSmartLists, getSmartListTasks, User.show_today / show_scheduled
  utils/smartLists.ts (+ tests)         → identifiants de moitié, groupSmartTasks, smartSections, smartListSummary
  components/SmartView.tsx              → affiche aussi une liste perso (icône, nom, sous-titre, sections)
  components/Workspace.tsx              → state.smartLists, chargement, rechargement, findSmartList
  components/Sidebar.tsx                → listes perso visibles sous Aujourd'hui / Planifié
  components/MobileHome.tsx             → tuiles des listes perso
```

## Comment ça marche

### Base

```
smart_lists : id, user_id, name, color, icon,
              kind ('tags' | 'urgency'), tag_match ('any' | 'all'),
              tag_ids UUID[], urgency_ids UUID[], visible, position
```

### API

| Route | Rôle |
|---|---|
| `GET /smart-lists` | les listes, avec `open_tasks` (compteur) et `labels` (« #boulot », « Haute »…) |
| `POST /smart-lists` | créer (`kind` + `tag_ids` ou `urgency_ids`, au moins un) |
| `PATCH /smart-lists/{id}` | modifier (nom, couleur, icône, filtres, `visible`, `position`) |
| `DELETE /smart-lists/{id}` | supprimer (les tâches ne sont pas touchées) |
| `GET /smart-lists/{id}/tasks` | les tâches à faire qui correspondent, au format de `/tasks/due` |

- Le filtre est **une seule condition SQL**, `sqlSmartMatch`, utilisée à la fois pour le compteur et pour la liste des tâches (ils ne peuvent donc pas se contredire).
  - par urgence : `t.urgency_id = ANY(s.urgency_ids)` ;
  - par tags `any` : la tâche a au moins un des tags ;
  - par tags `all` : aucun des tags (qui existent encore) ne manque à la tâche, et il en reste au moins un.
- Ordre : par urgence → niveau (`rank` décroissant), puis échéance ; par tags → échéance ; sans échéance à la fin.
- Règles du PATCH : une liste d'ids envoyée ne peut pas être vide ; changer `kind` demande d'envoyer aussi les ids du nouveau type ; chaque id doit être au compte (400 sinon).

### Page web

- Une moitié d'écran affiche une liste intelligente perso sous l'identifiant **`smart:<id>`** (`smartPaneId`, `customSmartId`). Aujourd'hui et Planifié gardent `smart:today` / `smart:scheduled` (`isBuiltInSmart`). `isSmart` reconnaît les trois.
- Tout le reste est commun avec Aujourd'hui / Planifié : `Pane.due`, `reloadSmart` (qui appelle `getSmartListTasks` pour une liste perso), rechargement après chaque modification (`refreshLists`), tâche cochée qui s'efface, sélection, Maj+clic, clic droit, overlay, vue scindée, glisser depuis la barre latérale.
- `refreshCounts` recharge aussi `GET /smart-lists` : les compteurs de la barre latérale suivent.
- `SmartView` reçoit `paneId` et `smartList`. Sections : `smartSections` (dans `utils/smartLists.ts`), aussi utilisée par `Workspace.visibleOrder` pour que le Maj+clic suive l'ordre de l'écran.
  - par tags : une section « À faire » ;
  - par urgence : une section par niveau, titre dans la couleur du niveau.
- Sous-titre : « 2 tâches à faire · urgence Haute ou Moyenne », « · #boulot ou #réunion » (`et` si `all`).

## Lancer et tester

```bash
cd api && go test ./...
cd web && npm test && npx tsc -b
docker compose up -d --build --wait     # applique la migration 011
postman collection run api/tests/bettertodo-api-donnees.postman_collection.json \
    --env-var adminEmail=… --env-var adminPassword=…
```

Créer une liste à la main (en attendant 7-6), avec un jeton d'accès (Paramètres › Jetons d'accès) :

```bash
curl -X POST http://localhost:8000/api/v1/smart-lists -H "Authorization: Bearer btd_…" \
     -H "Content-Type: application/json" \
     -d '{"name": "Urgent", "color": "#E5484D", "icon": "flag", "kind": "urgency", "urgency_ids": ["<id du niveau Haute>"]}'
```

Puis dans le navigateur : la liste apparaît sous Planifié avec son compteur ; l'ouvrir, cocher une tâche (elle s'efface, le compteur baisse), Cmd+clic pour l'ouvrir à côté, et la tuile sur l'accueil mobile.

## Où regarder en cas de problème

- **Compteur et liste qui ne correspondent pas** : impossible si les deux passent par `sqlSmartMatch` ; vérifier qu'une nouvelle requête l'utilise bien.
- **Une tâche manque** : elle est peut-être faite (seulement les tâches à faire), ou son tag a été renommé puis supprimé. `GET /smart-lists` montre les `tag_ids` qui existent encore.
- **La moitié reste vide après la suppression d'une liste intelligente** : `Workspace` n'affiche `SmartView` que si `findSmartList` la trouve ; recharger la page la retire des moitiés retenues.
- **Erreur au démarrage de l'API** : `docker compose logs api`, la ligne « migrations appliquées » doit lister `011_smart_lists.sql`.
