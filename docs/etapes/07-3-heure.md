# Étape 7-3 : heure facultative sur une tâche

## Objectif

Demande de `ModifBetterTodo.md` : « ajouter l'heure aussi sur une tâche ». Une tâche peut maintenant avoir, en plus de sa date d'échéance, une **heure facultative** (« 14:30 »), **tapée directement** dans un champ « Heure » de l'overlay (maquette v3, écran 3). Elle prépare aussi les notifications Android (étape à part).

Choix validés :
- **jamais d'heure sans date** : la base l'interdit, l'API refuse (400), et le champ est grisé tant qu'il n'y a pas d'échéance ;
- **enlever l'échéance enlève aussi l'heure** ;
- Aujourd'hui / Planifié trient par **date, puis heure** (les tâches sans heure après celles qui en ont une, le même jour) ;
- « en retard » ne regarde **que la date** (une tâche d'aujourd'hui à 9:00 pas faite à 15:00 reste dans « Aujourd'hui ») ;
- pas d'heure dans la Quick task ni (pour l'instant) dans l'app Android, qui ignore le nouveau champ sans planter.

## Fichiers

```
api/
  migrations/009_task_due_time.sql      → NOUVEAU : tasks.due_time TIME + contrainte « pas d'heure sans date »
  internal/handlers/tasks.go            → due_time lu, créé, modifié (PATCH Optional), validTime
  internal/handlers/due.go              → due_time lu, tri date puis heure
  internal/handlers/helpers_test.go     → TestValidTime
  tests/generer_collection_donnees.py   → 7 requêtes « Heure – … » (+ collection régénérée)
web/src/
  api/types.ts                          → Task.due_time, TaskChanges.due_time
  components/TaskPopover.tsx / .css     → champ « Heure », Groupe sur sa propre ligne
  components/TaskCard.tsx               → DueDate affiche l'heure (utilisé aussi par TaskRow)
  utils/*.test.ts                       → due_time dans les tâches de test
mcp/
  types.go, tools.go                    → due_time en lecture, dans create_task et update_task
```

## Comment ça marche

### Base et API

- `due_time` est une colonne `TIME`. L'API la renvoie au format `"HH:MM"` grâce à `to_char(t.due_time, 'HH24:MI')` dans le `SELECT` (sans les secondes que PostgreSQL ajouterait).
- Création : `due_time` demande `due_date` dans la même requête.
- PATCH `/tasks/{id}` :

| Requête | Résultat |
|---|---|
| `{"due_time": "14:30"}` | heure mise si la tâche a déjà une date, sinon 400 |
| `{"due_date": "2026-11-20", "due_time": "09:00"}` | les deux |
| `{"due_time": null}` | heure enlevée, date gardée |
| `{"due_date": null}` | date **et** heure enlevées |
| `{"due_date": null, "due_time": "09:00"}` | 400 |
| `{"due_time": "25:00"}` ou `"9:05"` | 400 (format HH:MM, 00:00 à 23:59) |

- `validTime` (avec test Go) vérifie le format. La contrainte `tasks_due_time_needs_date` en base protège en dernier recours.
- `/tasks/due` trie par `t.due_date, t.due_time NULLS LAST`, puis l'ordre des listes et des groupes comme avant.

### Page web

- L'overlay a une ligne « Échéance | Heure », puis « Groupe » sur sa propre ligne. **Écart avec la maquette**, qui mettait les trois sur une ligne : avec de vrais champs date et heure, la date était coupée (« 23.11.2 ») dans les 360 px de l'overlay.
- **L'heure est enregistrée en quittant le champ ou avec Entrée** (comme le titre), pas à chaque chiffre. Un premier essai qui enregistrait à chaque changement ne marchait pas : la réponse de l'API réécrivait le champ pendant la frappe et le navigateur perdait la saisie (09:15 devenait 14:05). L'heure en cours de frappe est donc dans `TaskPopover.state.dueTime`, envoyée par `saveDueTime` (aussi à la fermeture de l'overlay).
- Vider l'échéance envoie `{due_date: null, due_time: null}` et vide le champ Heure.
- `DueDate` (carte, ligne, listes intelligentes) affiche « Aujourd'hui 14:30 », « 23 nov. 09:15 »…

### MCP

`taskOut.due_time` en lecture ; `create_task` et `update_task` ont un paramètre `due_time` (HH:MM, vide = enlever). Claude peut donc écrire « mets la réunion à 14 h demain ».

## Lancer et tester

```bash
cd api && go test ./...
cd mcp && go test ./...
cd web && npm test && npx tsc -b
docker compose up -d --build --wait     # applique la migration 009 au démarrage de l'API
/Users/<vous>/.local/bin/postman collection run api/tests/bettertodo-api-donnees.postman_collection.json \
    --env-var adminEmail=… --env-var adminPassword=…
```

Dans le navigateur :
1. Ouvrir l'overlay d'une tâche **sans** échéance : le champ Heure est grisé.
2. Choisir une date, cliquer sur les heures du champ Heure, taper `0915`, Entrée : la carte affiche « … 09:15 ».
3. Dans Planifié, deux tâches le même jour : celle avec l'heure la plus tôt est en premier, celles sans heure à la fin.
4. Vider la date : l'heure disparaît aussi.

## Où regarder en cas de problème

- **L'heure tapée change toute seule ou se perd** : elle ne doit être envoyée qu'au `blur` / Entrée (`saveDueTime`), jamais dans `onDueTimeChanged`.
- **400 « due_time demande une due_date »** : la tâche n'a pas de date ; vérifier que le client envoie la date avant (ou avec) l'heure.
- **L'heure s'affiche avec des secondes** : le `to_char(..., 'HH24:MI')` manque dans un `SELECT` (`taskSelect` dans `tasks.go`, `dueSelect` dans `due.go`).
- **Erreur au démarrage de l'API** : `docker compose logs api`, la ligne « migrations appliquées » doit lister `009_task_due_time.sql`.
