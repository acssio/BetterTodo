# Étape 3d : listes intelligentes « Aujourd'hui » et « Planifié »

## Objectif

Deux vues en haut de la barre latérale, qui rassemblent les tâches de **toutes** les listes :
- **Aujourd'hui** : les tâches à faire dont l'échéance est aujourd'hui **ou dépassée**, en deux sections, « En retard » (en rouge) puis « Aujourd'hui » ;
- **Planifié** : toutes les tâches à faire qui ont une échéance, avec une section par date (« En retard », « Aujourd'hui », « Demain », « Mer. 18 nov. »…).

Chaque tâche montre sa **liste d'origine**. On peut la cocher (elle s'efface puis disparaît) et ouvrir son overlay. Changer l'échéance la range au bon endroit. Ces vues s'ouvrent comme une liste : clic, Cmd+clic, ou glisser vers une moitié de la vue scindée.

Hors de cette étape, comme décidé : réordonner, ajouter une tâche, et glisser une tâche de ces vues vers une liste.

## Fichiers

```
api/
  internal/handlers/due.go             → DueHandler : List (/tasks/due) et Counts (/tasks/due/counts)
  cmd/server/main.go                   → les deux routes
  tests/generer_collection_donnees.py  → + 13 requêtes sur les échéances
web/src/
  api/types.ts, api/ApiClient.ts       → DueTask, DueCounts, getDueTasks, getDueCounts
  utils/dates.ts                       → todayIso() : la date du jour, en heure LOCALE
  utils/smartLists.ts (+ tests)        → SMART_TODAY / SMART_SCHEDULED, isSmart, groupDueTasks (sections)
  components/smartInfo.ts              → titre, icône Lucide et couleur de chaque vue
  components/SmartView.tsx/.css        → la vue (en-tête + sections de TaskRow)
  components/TaskRow.tsx/.css          → + badge de liste, animation de sortie, glisser désactivable
  components/Sidebar.tsx/.css          → « Aujourd'hui » et « Planifié » avec leurs compteurs
  components/Workspace.tsx             → moitiés « intelligentes », compteurs, cocher avec animation
```

## Côté API

| Méthode | Route | Effet |
|---|---|---|
| GET | `/tasks/due?until=AAAA-MM-JJ` | tâches à faire avec une échéance ≤ `until` (sans `until` : toutes), triées par date, avec `list` (id, nom, couleur, icône) et `group_name` |
| GET | `/tasks/due/counts?today=AAAA-MM-JJ` | `{"today": n, "scheduled": n}`, où `today` compte aussi les retards |

### Pourquoi c'est le client qui envoie la date du jour

Le serveur tourne en UTC, dans Docker. S'il calculait « aujourd'hui » lui-même, une tâche du 16 novembre deviendrait « aujourd'hui » dès 1 h du matin le 16 en Suisse… mais aussi « en retard » le 16 à 23 h, heure de New York. La page envoie donc **sa** date (`todayIso()`, qui utilise le fuseau de l'ordinateur). Les futures apps natives feront pareil.

### `/tasks/due` et `/tasks/{id}`

Les deux routes commencent par `/tasks/`. Le routeur de Go choisit toujours le motif **le plus précis** : `/tasks/due` (texte fixe) passe avant `/tasks/{id}` (n'importe quel texte). L'ordre d'enregistrement ne compte pas.

## Côté page web

### Une liste intelligente est une « moitié » comme une autre

Une moitié (`Pane`) a maintenant trois champs :

```ts
interface Pane {
    listId: string            // id d'une liste… ou 'smart:today' / 'smart:scheduled'
    board: BoardData | null   // contenu d'une vraie liste
    due: DueTask[] | null     // contenu d'une liste intelligente
}
```

C'est pour ça que ces vues profitent gratuitement de la vue scindée, du Cmd+clic, du glisser depuis la barre latérale et de la mémoire au rechargement. `isSmart(id)` sert à savoir dans quel cas on est.

### Ranger en sections (`groupDueTasks`)

L'API renvoie les tâches **déjà triées par date**. `groupDueTasks` les parcourt dans l'ordre et crée une section à chaque nouvelle clé :
- tâche en retard → clé `late` ;
- « Aujourd'hui » → clé `today` ;
- « Planifié » → la date elle-même.

Les titres se calculent en heure locale. C'est une fonction pure, testée dans `smartLists.test.ts`, y compris un « aujourd'hui » à 23 h 30.

### Cocher dans une liste intelligente

1. La case se coche, et la ligne reçoit la classe `leaving` : elle s'efface en 0,5 s (`TaskRow.css`).
2. La requête part vers l'API.
3. Après 650 ms (`LEAVING_DELAY`), les listes intelligentes et les compteurs sont rechargés : la tâche a disparu.

Sans ce délai, la ligne disparaîtrait d'un coup, et on ne verrait pas ce qui s'est passé.

### Toujours à jour

`refreshLists()`, appelée après chaque modification de tâche, recharge maintenant :
- les listes ;
- les compteurs « Aujourd'hui » / « Planifié » ;
- les listes intelligentes affichées.

Ainsi, une tâche créée, cochée, supprimée ou dont l'échéance change dans une liste normale apparaît (ou disparaît) aussitôt dans « Aujourd'hui », même affichée à côté en vue scindée.

### L'overlay depuis une liste intelligente

La vue ne charge pas toute la liste d'origine. Le menu « Groupe » de l'overlay ne propose donc que le groupe actuel de la tâche. Pour la changer de groupe, il faut l'ouvrir depuis sa liste.

## Lancer et tester

```bash
docker compose up -d --build --wait
```

```bash
cd api && go test ./...
cd web && npm test && npm run build && npm run lint
/Users/acssiociardo/.local/bin/postman collection run api/tests/bettertodo-api.postman_collection.json
/Users/acssiociardo/.local/bin/postman collection run api/tests/bettertodo-api-donnees.postman_collection.json
```

- **Postman** : 25 + 74 requêtes, **140 vérifications**. Les nouvelles fixent « aujourd'hui » au 15 juin 2026 et vérifient :
  - les compteurs (retard + jour = 2, planifié = 3) ;
  - qu'une tâche future, une tâche sans date et une tâche faite sont bien exclues ;
  - le tri, la liste et le groupe d'origine ;
  - le refus d'une date invalide ou absente ;
  - l'isolation entre comptes.
- **Vitest** : 26 tests, dont 3 pour `groupDueTasks`, `isSmart` et `todayIso`.

> En écrivant ces tests, une apostrophe mal échappée (« d'origine ») a cassé le JavaScript d'une requête Postman. L'outil le signale par `SyntaxError in test-script` : c'était le test, pas l'API.

### Ce qui a été vérifié dans le navigateur

Avec le compte de test, le 15 novembre :
- les compteurs de la barre latérale (2 et 5) correspondent aux échéances ;
- **Aujourd'hui** : « En retard » (Acheter du pain, 13 nov.) puis « Aujourd'hui » (Préparer la démo), chacune avec sa liste d'origine ;
- **Planifié**, ouvert à côté par Cmd+clic : En retard, Aujourd'hui, Demain, Mer. 18 nov., Mer. 2 déc. ;
- cocher une tâche : la ligne s'efface (`leaving`), puis disparaît des deux vues, et les compteurs baissent ;
- passer l'échéance de « Relire la PR » à aujourd'hui depuis l'overlay de « Planifié » : elle rejoint « Aujourd'hui » à gauche, et le compteur passe à 2 ;
- aucune erreur 500.

Deux défauts visuels ont été corrigés pendant ces tests :
- les deux entrées étaient sous le titre « Listes » au lieu d'être au-dessus ;
- les titres affichaient « En Retard » : `text-transform: capitalize` met une majuscule à **chaque** mot. On utilise maintenant `::first-letter`.

## En cas de problème

| Symptôme | Cause probable | Solution |
|---|---|---|
| Une tâche du jour est « en retard » (ou l'inverse) | Date de l'ordinateur ou fuseau horaire | Vérifier l'heure du système. C'est elle qui définit « aujourd'hui » |
| `400 today est obligatoire` | Appel à `/tasks/due/counts` sans `?today=` | Toujours envoyer la date locale |
| Un compteur n'est pas à jour | La modification ne passe pas par `refreshLists()` | L'appeler après la modification |
| Une tâche cochée reste affichée | L'appel a échoué (bandeau rouge) ou la vue n'a pas été rechargée | Onglet Réseau : `GET /tasks/due` après le `PATCH` |
| Pas de vue scindée | Fenêtre de moins de 1 100 px | Normal : seule la moitié active est affichée |

## Et après ?

D'après le plan noté dans le CLAUDE.md : la **traduction complète de l'interface** (fr / en), en utilisant la langue du compte ajoutée à l'étape 3c-2b.
