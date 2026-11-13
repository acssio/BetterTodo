# Étape 2b : routes des listes, groupes et tâches

## Objectif

Donner accès à toutes les données de BetterTodo par l'API : groupes de listes, listes, groupes, tâches, niveaux d'urgence et tags. Chacun ne voit et ne modifie que ses propres données.

C'est tout ce dont la page web (étape 3) aura besoin.

## Fichiers ajoutés

```
api/internal/handlers/
  optional.go       → Optional[T] : distinguer « absent », « null » et « valeur » dans un PATCH
  helpers.go        → outils communs : appartenance, validation, UPDATE partiel, ordre (glisser-déposer)
  folders.go        → FolderHandler  : groupes de listes
  lists.go          → ListHandler    : listes + la vue complète d'une liste (board)
  groups.go         → GroupHandler   : groupes de tâches
  tasks.go          → TaskHandler    : tâches et leurs tags
  urgency.go        → UrgencyHandler : niveaux d'urgence
  tags.go           → TagHandler     : tags
  helpers_test.go   → tests Go des outils communs
api/cmd/server/main.go → toutes les routes sont branchées ici
api/tests/bettertodo-api-donnees.postman_collection.json → tests de bout en bout
```

Chaque fichier de ressource suit le même plan, pour qu'on s'y retrouve vite :
1. la classe `XxxHandler` et son constructeur `NewXxxHandler` ;
2. le type renvoyé au client (`folder`, `list`, `task`…) avec la requête `xxxSelect` et la fonction `scanXxx` ;
3. l'objet `xxxOrdering`, qui décrit l'ordre des éléments ;
4. les méthodes `List`, `Create`, `Update`, `Delete`, avec leur type de requête juste au-dessus.

## Les routes

Toutes sous `/api/v1`, et **toutes demandent d'être connecté**.

| Méthode | Route | Effet |
|---|---|---|
| GET | `/folders` | groupes de listes, dans l'ordre |
| POST | `/folders` | `{name, position?}` |
| PATCH | `/folders/{id}` | `{name?, position?, collapsed?}` |
| DELETE | `/folders/{id}` | ses listes passent **hors groupe** (elles ne sont pas supprimées) |
| GET | `/lists` | toutes les listes, dans l'ordre de la barre latérale, avec `open_tasks` |
| POST | `/lists` | `{name, folder_id?, color?, icon?, default_view?, position?}` |
| PATCH | `/lists/{id}` | mêmes champs ; `folder_id: null` = sortir du groupe |
| DELETE | `/lists/{id}` | supprime aussi ses groupes et ses tâches |
| GET | `/lists/{id}/board` | **la liste, ses groupes et leurs tâches, en un seul appel** |
| POST | `/lists/{id}/groups` | `{name, position?}` |
| PATCH | `/groups/{id}` | `{name?, position?}` |
| DELETE | `/groups/{id}` | supprime aussi ses tâches |
| POST | `/groups/{id}/tasks` | `{title, notes?, due_date?, urgency_id?, tags?, position?}` |
| GET | `/tasks/{id}` | une tâche |
| PATCH | `/tasks/{id}` | `{title?, notes?, due_date?, urgency_id?, done?, group_id?, position?, tags?}` |
| DELETE | `/tasks/{id}` | |
| GET | `/urgency-levels` | niveaux, du moins au plus urgent |
| POST | `/urgency-levels` | `{name, color, rank?}` |
| PATCH | `/urgency-levels/{id}` | `{name?, color?, rank?}` |
| DELETE | `/urgency-levels/{id}` | les tâches de ce niveau passent « sans urgence » |
| GET | `/tags` | tags avec `task_count` |
| POST | `/tags` | `{name}` |
| PATCH | `/tags/{id}` | `{name}` : renomme sur toutes les tâches à la fois |
| DELETE | `/tags/{id}` | l'enlève de toutes les tâches |

Exemple de réponse de `GET /lists/{id}/board` (raccourcie) :

```json
{
  "list": { "id": "…", "name": "Perso", "icon": "user", "color": "#E5484D", "open_tasks": 8, … },
  "groups": [
    {
      "id": "…", "list_id": "…", "name": "Courses", "position": 0,
      "tasks": [
        { "id": "…", "title": "Acheter du pain", "due_date": "2026-11-13", "urgency_id": "…",
          "done": false, "done_at": null, "position": 0, "tags": ["courses"], "notes": "", … }
      ]
    }
  ]
}
```

## Comment ça marche

### Les PATCH : modifier seulement ce qui est envoyé (`optional.go`)

Un `PATCH` ne contient que les champs à changer. Mais pour l'échéance, il faut distinguer trois cas :

| JSON envoyé | Sens |
|---|---|
| `{}` (champ absent) | ne pas toucher à l'échéance |
| `{"due_date": null}` | **effacer** l'échéance |
| `{"due_date": "2026-11-20"}` | la changer |

Avec un simple pointeur `*string`, Go donnerait `nil` dans les deux premiers cas. `Optional[T]` retient en plus **si le champ était présent** (`Set`). L'astuce : sa méthode `UnmarshalJSON` n'est appelée par Go que pour les champs présents, même s'ils valent `null`.

Ensuite, `update` (dans `helpers.go`) construit l'`UPDATE` avec seulement les colonnes envoyées :

```go
changes := newUpdate("tasks")
changes.set("title", "Pain")        // → title = $1
changes.set("due_date", nil)        // → due_date = $2   (NULL)
changes.exec(ctx, tx, id)           // → UPDATE tasks SET title = $1, due_date = $2 WHERE id = $3
```

Les valeurs passent toujours en paramètres (`$1`, `$2`…), jamais collées dans le SQL : pas d'injection SQL possible. Les noms de colonnes, eux, sont écrits en dur dans le code.

### L'ordre et le glisser-déposer (`ordering.move`)

Chaque ressource ordonnée a un objet `ordering`, qui dit quelle table, quelle colonne (`position`, ou `rank` pour les urgences) et comment trouver ses « frères ». Déplacer un élément :

```
Tâches du groupe : [A, B, C, D]   →   PATCH /tasks/A {"position": 2}
1. on retire A         → [B, C, D]
2. on l'insère en 2    → [B, C, A, D]
3. on renumérote       → B=0, C=1, A=2, D=3
```

- Une position trop grande met l'élément **à la fin**.
- Si on change de groupe (`group_id`) sans donner de position, la tâche va à la fin du nouveau groupe.
- Seules les lignes dont la position change sont modifiées, pour ne pas toucher `updated_at` pour rien.
- Tout se passe dans une **transaction** (`pgx.BeginFunc`) : si quelque chose échoue, rien n'est modifié.

### L'appartenance : personne ne voit les données des autres (`helpers.go`)

Avant toute action, une requête SQL remonte jusqu'à l'utilisateur. Par exemple, pour une tâche :

```sql
SELECT EXISTS (SELECT 1 FROM tasks t JOIN task_groups g ON g.id = t.group_id
               JOIN lists l ON l.id = g.list_id WHERE t.id = $1 AND l.user_id = $2)
```

| Situation | Réponse |
|---|---|
| L'élément de **l'URL** n'est pas à moi (ou n'existe pas) | **404** : on ne révèle pas qu'il existe chez quelqu'un d'autre |
| Un identifiant envoyé **dans le corps** n'est pas à moi (`folder_id`, `urgency_id`, `group_id`) | **400** avec un message (« Niveau d'urgence inconnu »…) |
| L'identifiant n'a même pas la forme d'un UUID | 404 ou 400, sans interroger la base |

C'est ce qui ferme le point laissé ouvert à l'étape 1 : la base seule n'empêchait pas de relier une tâche à l'urgence d'un autre utilisateur.

### Le board en 3 requêtes

`GET /lists/{id}/board` ne fait pas une requête par groupe, mais trois en tout :
1. la liste ;
2. ses groupes ;
3. toutes ses tâches, avec leurs tags regroupés par PostgreSQL (`array_agg`).

Ensuite, en Go, chaque tâche est rangée dans son groupe avec une `map`. Le nombre de requêtes reste fixe, quel que soit le nombre de groupes.

### Les tags

- Sur une tâche, on envoie des **noms** : `"tags": ["courses", "#maison"]`. Les espaces et le `#` sont enlevés, les doublons aussi, et les tags inconnus sont créés automatiquement.
- Envoyer `tags` dans un PATCH **remplace** tous les tags de la tâche.
- Les tags sont partagés par toutes les listes d'un utilisateur. Renommer un tag le renomme partout.

### Cocher une tâche

`{"done": true}` remplit `done_at` avec l'heure actuelle. Si la tâche était déjà cochée, l'heure d'origine est gardée. `{"done": false}` vide `done_at`. Tout se fait en une seule expression SQL :

```sql
done_at = CASE WHEN $n THEN COALESCE(done_at, now()) ELSE NULL END
```

## Lancer

```bash
docker compose up -d --build --wait
```

Il n'y a pas de nouvelle migration : le schéma de l'étape 1 suffisait.

Pour essayer à la main avec curl, avec le compte de démo (identifiants en tête de `db/exemple/exemple.sql`, à charger d'abord) :

```bash
# Connexion : récupère le jeton
TOKEN=$(curl -s -X POST localhost:8080/api/v1/auth/login -H 'Content-Type: application/json' \
  -d '{"email":"demo@bettertodo.local","password":"demo"}' | python3 -c 'import sys,json;print(json.load(sys.stdin)["token"])')

# Les listes
curl -s -H "Authorization: Bearer $TOKEN" localhost:8080/api/v1/lists | python3 -m json.tool
```

## Tester

```bash
cd api && go test ./...
```

Ces tests Go couvrent `Optional`, le nettoyage des tags, les dates, la construction de l'`UPDATE`, et les formats de couleur et d'icône.

```bash
/Users/acssiociardo/.local/bin/postman collection run api/tests/bettertodo-api.postman_collection.json
/Users/acssiociardo/.local/bin/postman collection run api/tests/bettertodo-api-donnees.postman_collection.json
```

La 2ᵉ collection (52 requêtes, 80 vérifications) crée deux comptes, A et B, et vérifie dans l'ordre :
- la création des groupes de listes, listes, groupes et tâches, avec leurs erreurs (couleur, date, titre vide…) ;
- le glisser-déposer : l'ordre des tâches après déplacement, et les positions renumérotées ;
- cocher et décocher, effacer l'échéance et l'urgence (`null`), remplacer les tags, et changer une tâche de groupe ;
- les tags (doublon, renommage visible sur la tâche) et les niveaux d'urgence (ajout, réordonnancement) ;
- **l'isolation** : B ne voit rien de A, ne peut ni modifier, ni supprimer, ni utiliser ses groupes de listes, ses niveaux d'urgence ou ses groupes ;
- les suppressions en cascade.

**Résultat :** les tests Go passent, les deux collections passent (96 vérifications), et le serveur n'a produit aucune erreur 500.

## En cas de problème

| Symptôme | Cause probable | Solution |
|---|---|---|
| `404 Introuvable` alors que l'élément existe | Il appartient à un autre compte, ou mauvais jeton | Vérifier avec `GET /me` qui est connecté |
| `400 Groupe de listes inconnu` / `Niveau d'urgence inconnu` | L'id envoyé dans le corps n'est pas à toi | Reprendre l'id depuis `GET /folders` ou `GET /urgency-levels` |
| `400 JSON invalide : json: unknown field "…"` | Faute de frappe dans un nom de champ | Corriger le nom (voir le tableau des routes) |
| `400 … ne peut pas être null` | `null` envoyé pour un champ obligatoire | Omettre le champ pour ne pas le changer |
| L'ordre n'est pas celui attendu | Les positions commencent à **0** | `position: 0` = en tête |
| `500` | Bug côté serveur | `docker compose logs api` donne le détail |

## Limites connues

- **Pas de pagination** : `GET /lists` et `/board` renvoient tout. C'est largement suffisant pour une todo list personnelle.
- Les **trous de position** dans l'ancien groupe, après avoir déplacé une tâche ailleurs, ne sont pas renumérotés. Ils ne gênent pas l'ordre d'affichage.
- La vérification d'appartenance se fait juste **avant** la transaction. Si une ressource est supprimée exactement entre les deux, on peut obtenir une erreur 500 au lieu de 404. C'est très improbable, et sans risque pour les données.

## Et après ?

À l'**étape 3**, la page web en React + TypeScript :
- un conteneur `web` (nginx), qui sert la page et redirige `/api` vers le serveur API ;
- l'écran de connexion et d'inscription ;
- la barre latérale (groupes de listes, listes et icônes Lucide) et la vue colonnes de la maquette, branchées sur `GET /lists/{id}/board`.
