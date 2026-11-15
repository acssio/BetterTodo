# Étape 3f : changer son mot de passe

## Objectif

- Un compte créé par l'admin doit **choisir son propre mot de passe à la première connexion**. C'est pareil après un changement de mot de passe fait par l'admin.
- Chacun peut **changer son mot de passe** dans **Paramètres › Mot de passe**.

## Fichiers

```
api/
  migrations/006_must_change_password.sql → users.must_change_password
  internal/handlers/auth.go               → ChangePassword (POST /me/password) ; createUser met le drapeau
  internal/handlers/admin.go              → ResetPassword met le drapeau (sauf sur soi-même)
  cmd/server/main.go                      → la route
  tests/bettertodo-api.postman_collection.json → + 8 requêtes sur le changement de mot de passe
web/src/
  api/types.ts, api/ApiClient.ts          → User.must_change_password, changePassword
  App.tsx                                 → nouvel état « mustChangePassword »
  components/ChangePasswordPage.tsx/.css  → l'écran obligatoire
  components/SettingsDialog.tsx/.css      → partie « Mot de passe »
```

## Le drapeau `must_change_password`

| Événement | Valeur |
|---|---|
| Compte admin créé à l'installation | `false` |
| Compte créé par l'admin dans la console | `true` |
| L'admin change le mot de passe de quelqu'un | `true` |
| L'admin change **son propre** mot de passe (clé sur sa ligne) | `false` |
| La personne choisit son mot de passe (`POST /me/password`) | `false` |
| Comptes qui existaient avant la migration 006 | `false` |

Comme décidé, c'est **la page web** qui oblige à changer le mot de passe : l'API envoie le drapeau (dans `/me` et dans la réponse de connexion), mais ne bloque pas les autres routes. Les futures apps natives devront faire pareil.

## Côté API

| Méthode | Route | Effet |
|---|---|---|
| POST | `/me/password` | `{"current_password": "…", "new_password": "…"}` → l'utilisateur à jour (200) |

Dans l'ordre :
1. l'ancien mot de passe est vérifié (400 « Mot de passe actuel incorrect ») ;
2. le nouveau doit faire 8 caractères au moins, et être différent de l'ancien (400) ;
3. le hash est remplacé et `must_change_password` repasse à `false` ;
4. **les autres sessions** du compte sont supprimées. Celle qui a fait la demande reste ouverte : on la reconnaît à son jeton (`token_hash <> HashToken(jeton de la requête)`).

On redemande l'ancien mot de passe même sur l'écran obligatoire : la règle est la même partout. Quelqu'un qui tomberait sur une session restée ouverte ne pourrait donc pas changer le mot de passe.

## Côté page web

### La machine d'état de `App`

```
             ┌──(must_change_password)──► mustChangePassword ──(mot de passe choisi)──┐
session ─────┤                                    │                                    ▼
             └────────────────────────────────────┼─────────────────────────────► loggedIn
                                                  └──(Se déconnecter)──► loggedOut
```

« session », c'est la réponse de `/me` au chargement, ou une connexion réussie. Les deux passent par `onLoggedIn(user)`, qui choisit l'état. Un rechargement de la page ramène donc sur l'écran obligatoire tant que le mot de passe n'a pas été changé.

### `ChangePasswordPage`

Elle reprend la carte de l'écran de connexion (`LoginPage.css`), avec trois champs : mot de passe provisoire, nouveau, confirmation. Si les deux nouveaux sont différents, l'erreur s'affiche sans appeler l'API. Un lien « Se déconnecter » permet de partir sans rien changer.

### Paramètres › Mot de passe

Les mêmes trois champs. Les erreurs s'affichent **sous** le formulaire (`passwordError`) et non en haut de la fenêtre comme les autres : en haut, on ne les voyait pas, puisque la fenêtre défile. Après un changement réussi, les champs sont vidés et « Mot de passe changé » s'affiche à côté du bouton.

## Lancer et tester

```bash
docker compose up -d --build
cd api && go test ./...
cd web && npm test && npm run lint
```

```bash
/Users/acssiociardo/.local/bin/postman collection run api/tests/bettertodo-api.postman_collection.json --env-var adminEmail=… --env-var adminPassword=…
```

La collection des comptes passe à 49 requêtes et 55 vérifications. Elle vérifie notamment :
- le drapeau : faux pour l'admin, vrai pour un compte créé par l'admin, de nouveau vrai après une réinitialisation ;
- les refus : ancien mot de passe faux, nouveau trop court, nouveau identique à l'ancien ;
- après un changement : la session en cours marche, une autre session est fermée, et on se connecte avec le nouveau mot de passe.

### Ce qui a été vérifié

Sur une pile Docker de test à part, avec de vrais clics :
- un compte créé par l'admin arrive sur « Choisis ton mot de passe », et y revient après un rechargement ;
- des confirmations différentes sont refusées ; avec les bonnes, les listes s'ouvrent ;
- dans les Paramètres, un mot de passe actuel faux affiche l'erreur sous le formulaire ; avec le bon, « Mot de passe changé » s'affiche ;
- la connexion avec le nouveau mot de passe ne demande plus de le changer.

## En cas de problème

| Symptôme | Cause probable | Où regarder |
|---|---|---|
| L'écran « Choisis ton mot de passe » revient sans arrêt | Le changement échoue (message rouge) | Onglet Réseau : réponse de `POST /me/password` |
| Déconnecté d'un autre navigateur | Normal après un changement de mot de passe | — |
| Un compte doit changer son mot de passe alors qu'il ne devrait pas | Drapeau resté à vrai | `SELECT email, must_change_password FROM users;` |
