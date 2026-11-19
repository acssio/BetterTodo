# Étape 7-4 : échéance par défaut de la Quick task

## Objectif

Demande de `ModifBetterTodo.md` : « ajouter dans les paramètres le jour par défaut. Perso je préférerais ne pas mettre d'échéance pour la Quick task ». Avant, la Quick task (accueil mobile) proposait toujours « Aujourd'hui ». Maintenant, Paramètres › **Quick task** permet de choisir **Aucune / Aujourd'hui / Demain** (maquette v3, écran 7).

Choix validés :
- c'est une **préférence du compte** (enregistrée sur le serveur, comme la vue par défaut) : la même sur tous les appareils ;
- **`today` par défaut** : les comptes existants ne voient aucun changement ;
- les puces de la Quick task restent : on peut toujours changer l'échéance au cas par cas ;
- l'app Android garde « Aujourd'hui » pour l'instant (elle ignore le nouveau champ).

## Fichiers

```
api/
  migrations/010_quick_task_due.sql     → NOUVEAU : users.quick_task_due ('none' | 'today' | 'tomorrow')
  internal/handlers/auth.go             → champ dans userResponse / userColumns, PATCH /me, validQuickTaskDue
  internal/handlers/helpers_test.go     → TestValidQuickTaskDue
  tests/bettertodo-api.postman_collection.json → 4 requêtes « Profil – Quick task : … »
web/src/
  api/types.ts                          → type QuickTaskDue, User.quick_task_due, UserChanges.quick_task_due
  utils/quickTask.ts (+ tests)          → quickTaskDueDate(réglage, aujourd'hui)
  components/SettingsDialog.tsx         → partie « Quick task »
  components/QuickTaskSheet.tsx         → prop defaultDue
  components/Workspace.tsx              → passe user.quick_task_due au panneau
```

## Comment ça marche

```
Paramètres › Quick task ──(clic)──► PATCH /me { quick_task_due } ──► App.onUserChanged (compte à jour)
                                                                          │
Accueil mobile ── « Quick task » ──► QuickTaskSheet(defaultDue = user.quick_task_due)
                                        └─ à l'ouverture : dueDate = quickTaskDueDate(defaultDue)
                                             'today' → date du jour, 'tomorrow' → demain, 'none' → null
```

- La date est calculée **à l'ouverture du panneau**, sur le téléphone (fuseau local), comme pour Aujourd'hui / Planifié.
- L'API refuse toute autre valeur que `none`, `today`, `tomorrow` (400), et refuse `null`. La base a la même contrainte (`CHECK`).
- `/me`, la connexion et la console admin renvoient tous `quick_task_due` (une seule liste de colonnes : `userColumns`).

## Lancer et tester

```bash
cd api && go test ./...
cd web && npm test && npx tsc -b
docker compose up -d --build --wait     # applique la migration 010
postman collection run api/tests/bettertodo-api.postman_collection.json \
    --env-var adminEmail=… --env-var adminPassword=…
```

Dans le navigateur, en mode mobile (fenêtre étroite) :
1. Paramètres › Quick task › **Aucune**.
2. Fermer, toucher « Quick task » : la puce « Aucune » est choisie.
3. Créer une tâche : elle n'a pas d'échéance (le compteur de Planifié ne bouge pas).
4. Recharger la page : le réglage est gardé (il est sur le serveur).

## Où regarder en cas de problème

- **La Quick task propose toujours « Aujourd'hui »** : vérifier `quick_task_due` dans la réponse de `GET /me` (onglet Réseau), puis la prop `defaultDue` passée par `Workspace`.
- **400 en changeant le réglage** : valeur autre que `none` / `today` / `tomorrow`.
- **Erreur au démarrage de l'API** : `docker compose logs api`, la ligne « migrations appliquées » doit lister `010_quick_task_due.sql`.
