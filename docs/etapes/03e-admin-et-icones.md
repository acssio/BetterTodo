# Étape 3e : compte administrateur, console admin, plus d'icônes

## Objectif

- **Plus d'inscription libre.** Au premier lancement, la page web propose de **créer le compte administrateur** (comme Portainer). Ensuite, elle n'affiche plus que « Connexion ».
- **Console admin** (bouton juste au-dessus de « Paramètres », visible seulement pour l'admin) : voir les comptes, en **créer**, en **supprimer** et **changer leur mot de passe**.
- **136 icônes** pour les listes au lieu de 16, classées par thème, avec la recherche en français.

## Fichiers

```
api/
  migrations/005_admin.sql             → users.is_admin ; le compte le plus ancien devient admin
  internal/handlers/auth.go            → SetupStatus et Setup (remplacent Register), createUser partagé
  internal/handlers/admin.go           → AdminHandler : RequireAdmin, ListUsers, CreateUser, ResetPassword, DeleteUser
  internal/handlers/helpers_test.go    → + tests de validateNewUser / validatePassword
  cmd/server/main.go                   → routes /auth/setup et /admin/users
  tests/*.postman_collection.json      → passent par l'admin au lieu de l'inscription
  tests/generer_collection_donnees.py  → idem, et supprime ses comptes de test à la fin
db/exemple/exemple.sql                 → le compte de démo est admin s'il est le premier compte
web/src/
  api/types.ts, api/ApiClient.ts       → User.is_admin, NewUser, needsSetup, setup, getUsers…
  App.tsx                              → nouvel état « setup » dans la machine d'état
  components/LoginPage.tsx/.css        → mode installation (props.setup), plus de lien d'inscription
  components/AdminDialog.tsx/.css      → la console admin
  components/Sidebar.tsx               → bouton « Console admin »
  components/Workspace.tsx             → ouvre / ferme la console (adminOpen)
  components/listIcons.ts              → les 136 icônes
  components/ListEditPopover.css       → la grille d'icônes défile
```

## Côté API

| Méthode | Route | Connexion | Effet |
|---|---|---|---|
| GET | `/auth/setup` | non | `{"needs_setup": true}` tant qu'aucun compte n'existe |
| POST | `/auth/setup` | non | crée le **premier** compte, admin, et le connecte (201). **403** dès qu'un compte existe |
| GET | `/admin/users` | admin | tous les comptes, du plus ancien au plus récent |
| POST | `/admin/users` | admin | crée un compte normal (201), 409 si l'email est pris. Ne connecte personne |
| PATCH | `/admin/users/{id}` | admin | `{"password": "…"}` : nouveau mot de passe (204). Ferme les sessions du compte |
| DELETE | `/admin/users/{id}` | admin | supprime le compte **et toutes ses données** (204). 400 sur son propre compte |

`POST /auth/register` n'existe plus. `GET /me` renvoie maintenant `is_admin`.

### Deux middlewares à la suite

```go
admin := func(route string, handler http.HandlerFunc) {
    mux.Handle(route, requireUser(adminHandler.RequireAdmin(handler)))
}
```

`requireUser` vérifie la session (401 sinon) et range l'id de l'utilisateur dans le contexte. `RequireAdmin` relit ensuite `users.is_admin` en base (403 sinon). On relit la base à chaque requête : si un jour on retire le rôle admin à quelqu'un, c'est immédiat.

### Deux installations en même temps ?

Si deux personnes ouvrent la page juste après l'installation, les deux voient « Créer le compte administrateur ». Pour qu'un seul compte soit créé, `createUser` (avec `firstAdmin = true`) fait tout dans une transaction :

```sql
LOCK TABLE users IN EXCLUSIVE MODE;   -- la 2e transaction attend ici que la 1re soit finie
SELECT EXISTS (SELECT 1 FROM users);  -- … puis voit le compte de la 1re → errSetupDone → 403
INSERT INTO users (…, is_admin) VALUES (…, true);
```

Ce verrou laisse passer les lectures (connexions, etc.), il bloque seulement les autres écritures dans `users`, et seulement le temps de cette transaction.

### Supprimer un compte

`DELETE FROM users WHERE id = $1` suffit : toutes les tables qui pointent vers `users` ont `ON DELETE CASCADE` (migration 001). Les listes, groupes, tâches, tags, niveaux d'urgence et sessions partent avec le compte.

### Changer un mot de passe

L'admin choisit le nouveau mot de passe et le transmet à la personne. Ses sessions ouvertes sont supprimées : elle doit se reconnecter. Exception : si l'admin change **son propre** mot de passe, il reste connecté.

### Installations existantes

La migration 005 rend admin le compte **le plus ancien** (`ORDER BY created_at`). Sur ta machine, c'est donc le premier compte que tu as créé. Pour changer d'admin à la main :

```bash
docker compose exec db sh -c 'psql -U "$POSTGRES_USER" -d "$POSTGRES_DB"'
```
```sql
SELECT email, is_admin FROM users ORDER BY created_at;
UPDATE users SET is_admin = (email = 'moi@exemple.fr');
```

## Côté page web

### La machine d'état de `App`

```
loading ──(session valide)──────────────► loggedIn
   ├──(aucun compte)──► setup ─────────────┘   (compte admin créé)
   └──(pas de session)──► loggedOut ───────┘   (connexion)
```

Sans session, `App` appelle `api.needsSetup()` pour choisir entre `setup` et `loggedOut`. Les deux affichent `LoginPage`, avec `setup={true}` ou `setup={false}`. En mode installation, l'écran demande aussi le nom affiché et dit que ce compte gérera les autres.

### `AdminDialog`

Il est construit comme `SettingsDialog` (même cadre, même CSS) : un écran à part qui charge et modifie ses propres données. Workspace n'a rien à recharger à la fermeture : les autres comptes n'apparaissent nulle part dans ses listes.

- **Clé** : un champ s'ouvre dans la ligne du compte (`InlineInput`). Entrée valide, Échap annule. Un message confirme le changement.
- **Corbeille** : `confirmDialog.ask(…)` avec un bouton rouge. Elle est grisée sur ton propre compte.
- **Nouveau compte** : le mot de passe est affiché en clair (`type="text"`), puisqu'il faut ensuite le donner à la personne.

### Les icônes

`listIcons.ts` liste toujours les icônes **une par une** : le site ne contient que celles-ci, et pas les 1 600 de Lucide. Elles sont rangées par thème (Général, Personnel, Maison, Travail et études, Tech, Argent et courses, Santé et sport, Cuisine, Voyage et transport, Loisirs). Le `label` contient les mots français que la recherche utilise (« plage » trouve le palmier, « courses » le panier…).

Pour en ajouter une : la choisir sur https://lucide.dev/icons, l'importer en haut du fichier, puis ajouter sa ligne `'nom-lucide': { icon: NomLucide, label: 'mots français' }`. **Ne jamais en retirer** : une liste qui l'utilise afficherait l'icône par défaut.

La grille montre 4 rangées et demie, et défile pour le reste. La demi-rangée montre qu'il y a une suite.

## Lancer et tester

```bash
docker compose up -d --build
cd api && go test ./...
cd web && npm test && npm run lint
```

Les collections Postman ont maintenant besoin du compte admin (elles s'en servent pour créer puis supprimer leurs comptes de test) :

```bash
/Users/acssiociardo/.local/bin/postman collection run api/tests/bettertodo-api.postman_collection.json --env-var adminEmail=… --env-var adminPassword=…
```

```bash
/Users/acssiociardo/.local/bin/postman collection run api/tests/bettertodo-api-donnees.postman_collection.json --env-var adminEmail=… --env-var adminPassword=…
```

Sur une base **vide**, la 1re collection crée l'admin avec ces identifiants (requête « Installation – premier compte »). Sinon, elle vérifie que l'installation est refusée (403). Pour viser un autre serveur : `--env-var baseUrl=http://…/api/v1`.

- Comptes : 41 requêtes, 44 vérifications (installation, console admin, refus pour un compte normal, changement de mot de passe, suppression).
- Données : 80 requêtes, 119 vérifications. Les comptes A et B sont supprimés à la fin.

### Ce qui a été vérifié

Sur une pile Docker de test à part (autres ports, volume à part) :
- migration 005 sur une base qui avait déjà deux comptes : le plus ancien devient admin ;
- base vide : écran « Créer le compte administrateur », puis connexion directe ;
- console admin, avec de vrais clics : créer un compte, changer son mot de passe, le supprimer (confirmation rouge) ;
- déconnexion → écran « Connexion », sans lien d'inscription ;
- un compte normal ne voit pas le bouton « Console admin », et l'API lui répond 403 ;
- icônes : recherche « plage » → palmier, choix enregistré et affiché dans la barre latérale ;
- `exemple.sql` sur une base vide : le compte de démo est admin. Sur une base avec des comptes : il ne l'est pas.

## En cas de problème

| Symptôme | Cause probable | Où regarder |
|---|---|---|
| « L'installation est déjà faite » (403) | Un compte existe déjà | `SELECT email, is_admin FROM users;` |
| Plus personne n'est admin | Base modifiée à la main | `UPDATE users SET is_admin = true WHERE email = '…';` |
| « Réservé à l'administrateur » (403) | Compte normal | `GET /me` → `is_admin` |
| Postman : « Admin – connexion » échoue | `--env-var adminEmail/adminPassword` oubliés ou faux | La commande ci-dessus |
| Une liste montre l'icône « liste » | Nom d'icône absent de `listIcons.ts` | `SELECT icon FROM lists;` |

## Limites

- Un seul rôle : admin ou non. Pas d'écran pour nommer un autre admin (SQL ci-dessus).
- Chacun ne peut pas encore changer **son** mot de passe lui-même. L'admin le fait pour lui, et pour lui-même avec la clé sur sa propre ligne.
