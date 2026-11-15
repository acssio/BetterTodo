# Étape 3c-2b : tâches faites, groupe par défaut, langue du compte

## Objectif

Quatre petites demandes après la 3c-2 :
1. **Afficher ou masquer les tâches faites**, pour chaque liste, avec un choix retenu sur tous les appareils.
2. **Vider les tâches faites** d'une liste.
3. Chaque nouvelle liste reçoit un **groupe par défaut**, pour pouvoir y ajouter des tâches tout de suite.
4. Ce groupe est nommé dans **la langue du compte** (« Tâches » / « Tasks »). La langue se choisit dans les Paramètres. La traduction complète de l'interface viendra dans une étape à part.

## Comment s'en servir

| Action | Comment |
|---|---|
| Masquer / afficher les tâches faites | Bouton œil dans l'en-tête (il reste bleu tant qu'elles sont masquées), ou menu « … » |
| Vider les tâches faites | Menu « … » de l'en-tête → « Vider les tâches faites (N) », avec confirmation. **Définitif** |
| Langue | Paramètres → Compte → Langue |

## Fichiers

```
api/
  migrations/004_language_show_done.sql  → users.language ('fr' par défaut), lists.show_done (true par défaut)
  internal/handlers/auth.go              → language dans l'utilisateur, modifiable par PATCH /me
  internal/handlers/lists.go             → show_done ; groupe par défaut à la création ; route ClearDone
  cmd/server/main.go                     → route DELETE /api/v1/lists/{id}/done-tasks
  tests/generer_collection_donnees.py    → le script qui génère la collection « données » (nouveau dans le dépôt)
  tests/*.postman_collection.json        → mises à jour (groupe par défaut, langue, masquer, vider)
web/src/
  api/types.ts, api/ApiClient.ts         → Language, show_done, clearDoneTasks
  utils/board.ts (+ tests)               → countDoneTasks, hideDoneTasks, visibleToFullIndex
  components/ListHeader.tsx/.css         → bouton œil + menu « … » de la liste
  components/Workspace.tsx               → onToggleShowDone, onClearDone, glisser-déposer adapté
  components/SettingsDialog.tsx/.css     → choix de la langue
```

## Comment ça marche

### Groupe par défaut (serveur)

`POST /lists` crée la liste **et** son groupe, dans la même transaction :

```go
var defaultGroupName = map[string]string{"fr": "Tâches", "en": "Tasks"}
```

C'est le serveur qui le fait, pas la page web : les futures apps natives (Swift, Qt) n'auront rien à coder pour en profiter. La même table sert à vérifier les langues acceptées par `PATCH /me` : ajouter une langue se fait donc à un seul endroit. Il faudra aussi l'ajouter au `CHECK` de la colonne, avec une nouvelle migration.

### Masquer les tâches faites

`lists.show_done` est enregistré par liste, comme Colonnes/Sections. Le **board complet** reste dans l'état de `Workspace`, et seul l'affichage est filtré (`hideDoneTasks` dans `renderView`). Ainsi :
- les compteurs, « Vider (N) » et l'overlay connaissent toujours toutes les tâches ;
- réafficher les tâches faites ne demande aucun appel au serveur pour les retrouver.

### Le piège : glisser-déposer avec des tâches masquées

Quand des tâches faites sont masquées, l'index sous la souris est compté parmi les tâches **visibles**, alors que l'API attend un index parmi **toutes** les tâches du groupe. Sans conversion, une tâche déposée « en bas » pourrait atterrir au milieu des tâches masquées. `visibleToFullIndex()` fait la conversion :

```
Groupe : [A, x, B, y, C]       (x et y faites, masquées)
On déplace C. Visibles sans C : [A, B]   Toutes sans C : [A, x, B, y]

index visible 0 (avant A)  → index réel 0
index visible 1 (avant B)  → index réel 2   (après x)
index visible 2 (en bas)   → index réel 3   (juste après B, avant y)
```

Cette fonction est testée dans `board.test.ts`, et vérifiée dans le navigateur (voir plus bas).

### Vider les tâches faites

`DELETE /lists/{id}/done-tasks` supprime toutes les tâches faites de la liste en **une seule requête SQL**, donc tout ou rien, et répond `{"deleted": n}`. La page enlève d'abord les tâches de l'écran, puis appelle l'API. En cas d'erreur, elle recharge la liste.

## Lancer

```bash
docker compose up -d --build --wait
```

La migration 004 s'applique au démarrage de l'API, sans toucher aux données. Les listes existantes affichent leurs tâches faites, et les comptes existants sont en français.

## Tester

```bash
cd api && go test ./...
cd web && npm test && npm run build && npm run lint
/Users/acssiociardo/.local/bin/postman collection run api/tests/bettertodo-api.postman_collection.json
/Users/acssiociardo/.local/bin/postman collection run api/tests/bettertodo-api-donnees.postman_collection.json
```

**Postman** : 25 + 59 requêtes, **120 vérifications**. Les nouvelles couvrent :
- le groupe « Tâches » d'une nouvelle liste ;
- le passage en anglais, puis le groupe « Tasks » ;
- le refus d'une langue inconnue ;
- `show_done` ;
- « Vider » (2 tâches supprimées, refusé à un autre compte avec un 404, les tâches pas faites gardées).

La collection « données » est générée par un script Python. Pour ajouter un test, on modifie `api/tests/generer_collection_donnees.py`, puis on régénère :

```bash
python3 api/tests/generer_collection_donnees.py api/tests/bettertodo-api-donnees.postman_collection.json
```

**Vitest** : 21 tests, dont 3 nouveaux pour les tâches faites.

### Ce qui a été vérifié dans le navigateur

Avec le compte de test :
- le bouton œil masque la tâche faite, reste bleu, et le choix tient après rechargement ;
- **glisser-déposer pendant que les tâches faites sont masquées** : « Fruits » déposée en bas de la colonne donne, côté serveur, « Déjà fait (faite), Acheter du pain, Fruits ». La tâche masquée garde sa place ;
- « Vider les tâches faites (1) » : confirmation, puis plus aucune tâche faite sur le serveur ;
- une nouvelle liste créée depuis la barre latérale contient un groupe « Tâches ». Après passage en anglais dans les Paramètres, elle contient « Tasks » ;
- aucune erreur 500 côté serveur.

## En cas de problème

| Symptôme | Cause probable | Solution |
|---|---|---|
| Une tâche déposée atterrit au mauvais endroit (tâches faites masquées) | Conversion d'index court-circuitée | Voir `moveTask` dans `Workspace` et `visibleToFullIndex` |
| `400 language doit valoir fr ou en` | Langue non prévue | Ajouter la langue à `defaultGroupName` **et** à la contrainte de la colonne (nouvelle migration) |
| Le groupe par défaut a le mauvais nom | Langue du compte | Paramètres → Langue (les listes déjà créées ne changent pas) |
| Les tâches faites ont disparu | Masquées (bouton œil bleu), ou vidées | Cliquer sur l'œil. Un vidage, lui, est définitif |

## Correction après tes tests : « Vider ne fonctionne pas »

**Cause** : la confirmation utilisait `window.confirm`. Le **navigateur intégré de l'application Claude désactive les boîtes de dialogue natives** : `confirm()` y répond « non » tout de suite, sans rien afficher. Le message dans la console du navigateur le dit :

```
Page dialog suppressed (confirm) — native JavaScript dialogs are disabled in this browser; confirm() returned false
```

Toutes les suppressions avec confirmation échouaient donc en silence : tâche, groupe, liste, groupe de listes, tag, niveau d'urgence, vider. Mes tests remplaçaient `window.confirm` par « oui », ce qui cachait le problème.

**Correction** : une fenêtre de confirmation **dessinée par BetterTodo**, qui marche dans tous les navigateurs :
- `components/confirm.ts` : la classe `ConfirmService` et son instance `confirmDialog`. On l'utilise avec `if (!(await confirmDialog.ask('Supprimer … ?'))) return`. Pour une simple information, c'est `confirmDialog.inform(…)` ;
- `components/ConfirmDialog.tsx` : `ConfirmHost`, placé une seule fois dans `App`, affiche la demande. Entrée confirme, Échap annule, et un clic à côté annule aussi ;
- l'attribut `data-keep-popovers` évite qu'un clic dans la confirmation ferme l'overlay ouvert derrière, qui écoute les clics « en dehors » de lui.

**Vérifié avec de vrais clics**, sans rien remplacer : menu « … » → « Vider les tâches faites (1) » → la fenêtre s'affiche → « Vider » → `DELETE …/done-tasks` part, et la tâche disparaît.

## Et après ?

**3c-3 : la vue scindée**
- deux listes côte à côte (glisser une liste à droite, ou Cmd+clic) ;
- glisser une tâche d'une liste à l'autre.
