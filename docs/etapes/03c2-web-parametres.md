# Étape 3c-2 : l'écran Paramètres

## Objectif

L'écran Paramètres de la maquette (écran 6), ouvert par « Paramètres » en bas de la barre latérale :
- **Compte** : changer son nom affiché ;
- **Niveaux d'urgence** : ajouter, renommer, changer la couleur, réordonner par glisser-déposer, supprimer ;
- **Tags** : ajouter, renommer (sur toutes les tâches à la fois), supprimer ;
- **Affichage par défaut** : la vue (colonnes ou sections) des **nouvelles** listes.

La vue par défaut est une préférence du **compte** : elle suit l'utilisateur sur tous ses appareils. Il a fallu la stocker côté serveur.

## Fichiers

```
api/
  migrations/003_user_default_view.sql   → ajoute users.default_view ('columns' par défaut)
  internal/handlers/auth.go              → default_view dans les réponses + nouvelle route UpdateMe
  internal/handlers/lists.go             → POST /lists sans default_view → vue du compte
  cmd/server/main.go                     → route PATCH /api/v1/me
  tests/bettertodo-api.postman_collection.json → + 6 requêtes sur PATCH /me et la vue par défaut
web/src/
  api/types.ts, api/ApiClient.ts         → User.default_view, UserChanges, Tag, UrgencyChanges + appels
  components/SettingsDialog.tsx/.css     → l'écran Paramètres (nouveau)
  utils/moveItem.ts (+ .test.ts)         → déplacer un élément dans une liste (réordonner les niveaux)
  utils/dragDrop.ts                      → computeDropIndex accepte un autre attribut que data-task-id
  utils/popover.ts, InlineInput.tsx      → Échap dans un champ n'annule que le champ, pas la fenêtre
  App.tsx, Workspace.tsx, Sidebar.tsx    → ouverture/fermeture, utilisateur mis à jour
```

## Côté API

### Migration 003

```sql
ALTER TABLE users ADD COLUMN default_view TEXT NOT NULL DEFAULT 'columns'
    CHECK (default_view IN ('columns', 'sections'));
```

Elle est appliquée **toute seule** au démarrage du conteneur `api`, grâce au gestionnaire de migrations de l'étape 2a : `migrations appliquées : [003_user_default_view.sql]`. Les comptes existants reçoivent `columns`. C'est la première fois que le schéma évolue sans effacer la base.

### `PATCH /api/v1/me`

```json
{ "display_name": "Alessio", "default_view": "sections" }
```

- Les deux champs sont facultatifs, et un champ absent n'est pas modifié (`Optional[T]`).
- Le nom est nettoyé et doit faire entre 1 et 100 caractères. La vue doit être `columns` ou `sections`.
- La réponse est l'utilisateur complet. `default_view` apparaît aussi dans `GET /me`, `login` et `register`.

Pour lire l'utilisateur toujours de la même façon, `auth.go` a maintenant `userColumns` (la liste des colonnes) et `scanTargets()` (les champs Go dans le même ordre). Ajouter une colonne ne demande plus qu'une modification à ces deux endroits.

### Nouvelles listes

```sql
COALESCE($6, (SELECT default_view FROM users WHERE id = $1))
```

Si le client envoie `default_view`, il est utilisé ; sinon (`NULL`), c'est la vue du compte. La page web n'a rien à faire : elle ne précise jamais la vue en créant une liste.

## Côté page web

### Pourquoi `SettingsDialog` parle à l'API

Jusqu'ici, seuls `App` et `Workspace` appelaient l'API. `SettingsDialog` fait **exception**, volontairement :
- c'est un écran à part, avec ses propres données (la liste des tags, qui ne sert nulle part ailleurs) ;
- `Workspace` aurait encore grossi d'une dizaine de méthodes qui ne le concernent pas.

Il prévient quand même les autres de ce qui les concerne :
- `onUserChanged(user)` : le nom affiché remonte jusqu'à `App`, et la barre latérale se met à jour ;
- `onUrgencyLevelsChanged(levels)` : les pastilles d'urgence des cartes changent tout de suite ;
- à la **fermeture**, `Workspace` recharge la liste affichée, qui contient peut-être un tag renommé ou supprimé.

### Réordonner les niveaux

C'est le même glisser-déposer natif que pour les tâches :
- les lignes portent `data-level-id` (au lieu de `data-task-id`), d'où le nouveau paramètre `attribute` de `computeDropIndex` ;
- au dépôt, `moveItem()` réordonne la liste localement et renumérote les rangs 0, 1, 2…, comme le fera le serveur ;
- ensuite, `PATCH /urgency-levels/{id}` avec `{ "rank": index }` est envoyé.

### Échap dans un champ

Avant cette étape, appuyer sur Échap dans un champ (par exemple « Nouveau tag ») annulait le champ **et** fermait la fenêtre ou l'overlay qui le contenait. Maintenant :
1. `InlineInput` appelle `event.preventDefault()` en traitant Échap ;
2. `DismissListener` ignore une touche Échap déjà « traitée » (`event.defaultPrevented`).

Un premier Échap annule le champ, un second ferme la fenêtre.

## Lancer

```bash
docker compose up -d --build --wait
```

La migration 003 s'applique au démarrage de l'API. Rien à effacer : les données existantes sont gardées.

## Tester

```bash
cd api && go test ./...
cd web && npm test && npm run build && npm run lint
/Users/acssiociardo/.local/bin/postman collection run api/tests/bettertodo-api.postman_collection.json
/Users/acssiociardo/.local/bin/postman collection run api/tests/bettertodo-api-donnees.postman_collection.json
```

- **Postman** : 20 + 52 requêtes, 102 vérifications. Les nouvelles couvrent :
  - la vue par défaut initiale, la modification du nom (nettoyé) et de la vue ;
  - le refus d'une vue invalide et d'un nom vide ;
  - une nouvelle liste sans vue, qui prend celle du compte, et une liste avec vue explicite, qui garde la sienne.
- **Vitest** : 18 tests, dont 3 pour `moveItem`.

### Ce qui a été vérifié dans le navigateur

Avec un compte de test, en contrôlant à chaque fois ce que le serveur a enregistré :
- ajouter le niveau « Critique », le passer en violet, le glisser tout en haut : la ligne bleue s'affiche, et les rangs deviennent 0 à 3 dans le bon ordre ;
- renommer « Basse » en « Faible » : la pastille des cartes change ;
- renommer le tag `courses` en `achats` : les cartes affichent `#achats` après fermeture ;
- supprimer le tag `papiers` : la confirmation indique qu'il sera enlevé d'une tâche ;
- vue par défaut « Sections » : une nouvelle liste s'ouvre en sections ;
- le nom affiché change aussi en bas de la barre latérale ;
- Échap dans un champ ne ferme pas la fenêtre, alors qu'Échap hors des champs la ferme ;
- l'écran reste lisible à 600 px de large ;
- aucune erreur 500 côté serveur.

> Pendant les tests, un faux appui sur Échap créé en JavaScript **fermait** la fenêtre. Ce n'était pas un bug : l'événement simulé n'était pas « annulable » (`cancelable: false`), donc `preventDefault()` n'avait aucun effet. Une vraie touche l'est toujours. Avec un événement réaliste, le comportement est le bon.

## En cas de problème

| Symptôme | Cause probable | Solution |
|---|---|---|
| `migration 003_user_default_view.sql : … already exists` | Colonne ajoutée à la main | Ne jamais modifier le schéma à la main : passer par une migration |
| `400 default_view doit valoir columns ou sections` | Faute de frappe dans la valeur | Seules ces deux valeurs existent |
| `409 Ce tag existe déjà` en renommant | Un autre tag porte déjà ce nom | Choisir un autre nom, ou supprimer l'autre tag |
| Une carte garde l'ancien nom de tag | Liste rechargée seulement à la fermeture des Paramètres | Fermer la fenêtre (ou recharger la page) |
| Le nom en bas de la barre latérale ne change pas | `onUserChanged` n'arrive pas jusqu'à `App` | Vérifier la chaîne App → Workspace → SettingsDialog |
| Échap ferme la fenêtre au lieu d'annuler le champ | Le champ n'appelle pas `preventDefault()` | Voir `InlineInput.onKeyDown` |

## Et après ?

**3c-3 : la vue scindée**
- glisser une liste à droite, ou Cmd+clic, pour avoir deux listes côte à côte ;
- glisser une tâche d'une liste à l'autre.
