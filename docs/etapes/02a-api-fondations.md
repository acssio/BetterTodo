# Étape 2a : serveur API, migrations et comptes

## Objectif

Poser les fondations du serveur API en Go :
- un 2ᵉ conteneur `api`, à côté de la base ;
- des **migrations** pour faire évoluer le schéma sans effacer la base ;
- des **comptes** : inscription, connexion, déconnexion, « qui suis-je ? ».

Les routes pour les listes et les tâches arriveront à l'étape 2b.

## Fichiers

```
docker-compose.yml                 → ajoute le service "api"
.env.example                       → + SESSION_DAYS et COOKIE_SECURE
api/
  go.mod, go.sum                   → module Go "bettertodo/api" et ses dépendances
  Dockerfile, .dockerignore        → construction de l'image
  cmd/server/main.go               → point d'entrée : config → base → migrations → routes → écoute
  migrations/
    embed.go                       → inclut les *.sql dans le binaire
    001_schema.sql                 → le schéma de l'étape 1 (déplacé depuis db/init/)
    002_sessions.sql               → table des sessions de connexion
  internal/
    config/config.go               → lecture des variables d'environnement
    database/database.go           → connexion (pool pgx)
    database/migrator.go           → gestionnaire de migrations
    httpjson/httpjson.go           → lire et écrire du JSON, réponses d'erreur
    auth/password.go               → hash bcrypt des mots de passe
    auth/token.go                  → jetons de session aléatoires + leur hash
    auth/sessions.go               → SessionStore : créer, vérifier, supprimer une session
    auth/middleware.go             → RequireUser : bloque les requêtes non connectées
    handlers/auth.go               → AuthHandler : register, login, logout, me
    handlers/health.go             → HealthHandler : /health
    handlers/logging.go            → une ligne de log par requête
  tests/bettertodo-api.postman_collection.json → tests de bout en bout
```

`internal/` est une règle de Go : ces paquets ne peuvent être importés que par le module `bettertodo/api` lui-même.

## Les routes

Toutes les routes sont sous `/api/v1`. Corps et réponses sont en JSON. Les erreurs ont la forme `{"error": "message"}`.

| Méthode | Route | Connexion | Effet |
|---|---|---|---|
| GET | `/health` | non | `{"status":"ok"}` si le serveur **et** la base répondent, sinon 503 |
| POST | `/auth/register` | non | crée un compte et ses 3 niveaux d'urgence par défaut, puis connecte (201). **Remplacée à l'étape 3e** par `/auth/setup` (premier compte, admin) et la console admin |
| POST | `/auth/login` | non | connecte (200), ou 401 si email ou mot de passe incorrect |
| POST | `/auth/logout` | oui | ferme **cette** session (204) |
| GET | `/me` | oui | l'utilisateur connecté |
| PATCH | `/me` | oui | nom affiché et vue par défaut (ajouté à l'étape 3c-2) |

Exemple d'inscription :

```http
POST /api/v1/auth/register
Content-Type: application/json

{ "email": "alice@example.com", "password": "un-long-mot-de-passe", "display_name": "Alice" }
```

```json
{
  "user": { "id": "…", "email": "alice@example.com", "display_name": "Alice", "created_at": "…" },
  "token": "fv6F2CRV…",
  "expires_at": "2026-12-13T18:38:40Z"
}
```

## Comment ça marche

### Le démarrage (`main.go`)

1. `config.Load()` lit les variables d'environnement. Le serveur refuse de démarrer si `DATABASE_URL` manque.
2. `database.Connect()` ouvre un **pool** de connexions : quelques connexions restent ouvertes et sont prêtées aux requêtes.
3. `Migrator.Run()` applique les migrations en attente.
4. On crée les objets (`SessionStore`, `AuthHandler`…) et on branche les routes sur un `http.ServeMux`. Depuis Go 1.22, le mux de la bibliothèque standard comprend `"GET /api/v1/me"`, donc pas besoin de framework.
5. Le serveur écoute. Sur Ctrl+C ou `docker stop`, `server.Shutdown()` laisse finir les requêtes en cours avant de quitter.

### Les migrations (`migrator.go`)

- La table `schema_migrations` retient le nom des fichiers déjà appliqués.
- À chaque démarrage, les `*.sql` sont triés par nom, et ceux qui ne sont pas encore dans la table sont exécutés **chacun dans une transaction** : si une migration échoue, rien n'est à moitié appliqué.
- Les fichiers sont **inclus dans le binaire** (`//go:embed *.sql`) : l'image Docker n'a besoin que du binaire.

> **Règle d'or :** ne jamais modifier une migration déjà appliquée (elle ne serait pas rejouée). Pour changer le schéma, on ajoute un nouveau fichier `003_quelque-chose.sql`.

### Les mots de passe (`password.go`)

- On ne stocke que le **hash bcrypt**. Il contient un sel aléatoire, donc deux comptes avec le même mot de passe ont des hash différents.
- bcrypt est volontairement lent (~50 ms), ce qui rend très coûteux d'essayer des millions de mots de passe.
- Contraintes : 8 caractères minimum, et 72 octets maximum (limite de bcrypt).

### Les sessions (`token.go`, `sessions.go`)

1. À la connexion, le serveur génère un **jeton aléatoire** de 256 bits.
2. Il le donne au client, mais ne garde dans la table `sessions` que son **hash SHA-256**. Si la base fuit, les jetons ne sont pas utilisables.
3. À chaque requête, le middleware hache le jeton reçu et cherche une session non expirée qui a ce hash.
4. La déconnexion supprime la ligne : le jeton ne marche plus, même s'il n'a pas expiré. Les autres appareils restent connectés.

Ici, un simple SHA-256 suffit (pas besoin de bcrypt) : le jeton est aléatoire et long, il ne peut pas être deviné comme un mot de passe.

### Deux façons d'envoyer le jeton (`middleware.go`)

| Client | Comment | Pourquoi |
|---|---|---|
| Apps natives, Postman, curl | En-tête `Authorization: Bearer <jeton>` | Le jeton est renvoyé dans le corps de la réponse de connexion |
| Page web (étape 3) | Cookie `bettertodo_session` | Posé automatiquement. `HttpOnly` = le JavaScript de la page ne peut pas le lire, donc une faille XSS ne peut pas le voler |

Le cookie est en `SameSite=Lax`, et l'API exige `Content-Type: application/json`. Ensemble, ces deux réglages empêchent un autre site de faire agir ton navigateur à ta place (attaque CSRF).

### Les petites protections

- **Même message d'erreur** pour un email inconnu et un mauvais mot de passe. Pour un email inconnu, on compare quand même le mot de passe avec un hash factice : la réponse prend le même temps, et on ne peut pas deviner quels emails sont inscrits.
- **Champs inconnus refusés** : `"pasword"` (faute de frappe) donne une erreur 400 au lieu d'être ignoré en silence.
- **Corps limité à 1 Mo**, et `ReadHeaderTimeout` de 5 s contre les clients qui envoient très lentement.
- **Jamais de détail interne** dans une erreur 500 : le détail va dans les logs du serveur, le client reçoit « Erreur interne du serveur ».

### L'image Docker

Le `Dockerfile` travaille en deux temps :
1. une image Go complète compile le binaire ;
2. le binaire seul est copié dans une image **distroless** : pas de shell, pas de gestionnaire de paquets, utilisateur non-root.

Résultat : une image d'environ **21 Mo**. Le healthcheck Docker appelle `/bettertodo-api healthcheck`, un mode du binaire qui interroge `/api/v1/health`. Il n'y a pas de `curl` dans l'image.

## Lancer

> **Première fois après l'étape 1 :** le schéma vient maintenant des migrations. Il faut effacer une fois la base créée par l'ancien script d'init :
> ```bash
> docker compose down -v
> ```

```bash
docker compose up -d --build --wait
```

`--build` reconstruit l'image de l'API, à refaire après chaque modification du code Go. Pour voir les logs :

```bash
docker compose logs -f api
```

Au 1er démarrage, on voit `migrations appliquées : [001_schema.sql 002_sessions.sql]`, puis `base de données à jour` aux démarrages suivants.

Les données d'exemple se chargent toujours de la même façon, **une fois l'API démarrée** (elle crée les tables) :

```bash
docker compose exec -T db sh -c 'psql -U "$POSTGRES_USER" -d "$POSTGRES_DB"' < db/exemple/exemple.sql
```

## Tester

### Tests Go

```bash
cd api
go test ./...
```

Ils vérifient le hash des mots de passe, les jetons, la lecture du jeton dans une requête et le tri des migrations.

Le test du gestionnaire de migrations sur une **vraie base** a besoin d'une base de test, **qu'il vide entièrement**. Pour la créer (une seule fois) :

```bash
docker compose exec db sh -c 'createdb -U "$POSTGRES_USER" bettertodo_test'
```

Puis, depuis la racine du projet :

```bash
set -a; . ./.env; set +a
cd api && TEST_DATABASE_URL="postgres://$POSTGRES_USER:$POSTGRES_PASSWORD@127.0.0.1:5432/bettertodo_test?sslmode=disable" go test ./... -v
```

Sans `TEST_DATABASE_URL`, ce test est simplement ignoré (`SKIP`).

### Tests Postman (de bout en bout)

Avec les conteneurs démarrés, depuis la racine du projet :

```bash
/Users/acssiociardo/.local/bin/postman collection run api/tests/bettertodo-api.postman_collection.json
```

14 requêtes et 16 vérifications, dans l'ordre :
- santé, et refus sans jeton ;
- inscription, avec ses cas d'erreur : doublon, mot de passe court, champ inconnu ;
- profil, avec un jeton inventé ;
- connexion, avec ses cas d'erreur : mauvais mot de passe, email inconnu ;
- déconnexion, puis vérification que la 1re session marche toujours.

Chaque exécution crée un compte `test-…@example.test`, ce qui permet de la relancer à volonté. On peut aussi importer le fichier dans l'app Postman pour jouer les requêtes une par une.

### Ce qui a été vérifié

- Les migrations s'appliquent au 1er démarrage, et ne sont pas rejouées au redémarrage.
- Le compte de démo (hash bcrypt créé par PostgreSQL) se connecte bien depuis Go.
- La connexion par cookie marche, et le cookie est `HttpOnly`, limité à `/api`.
- Une requête sans `Content-Type: application/json` est refusée (415).
- Chaque nouveau compte reçoit les niveaux Basse, Moyenne et Haute.
- La table `sessions` ne contient que des hash de 32 octets, jamais les jetons.
- Les tests Go et les 16 vérifications Postman passent.

## En cas de problème

| Symptôme | Cause probable | Solution |
|---|---|---|
| `migration 001_schema.sql : … already exists` | La base a été créée par le script d'init de l'étape 1 | `docker compose down -v` puis relancer |
| L'API redémarre en boucle | Voir la cause dans les logs | `docker compose logs api` |
| `la variable DATABASE_URL est manquante` | Lancement hors Docker sans la variable | La définir, ou passer par `docker compose` |
| `password authentication failed` | Le mot de passe de `.env` a changé après la création de la base | `docker compose down -v`, ou `ALTER USER` dans psql |
| Mot de passe de base avec `@`, `/`, `:`… | Il casse l'URL de connexion | Utiliser seulement lettres et chiffres |
| Une modif du code Go n'a aucun effet | L'image n'a pas été reconstruite | `docker compose up -d --build` |
| `401 Session invalide ou expirée` | Jeton fermé (logout) ou trop vieux | Se reconnecter |
| `415` | En-tête `Content-Type: application/json` oublié | L'ajouter |

## Limites connues (pour plus tard)

- **Pas de limite de tentatives** de connexion. À ajouter avant d'exposer le serveur sur Internet, par exemple avec un blocage temporaire après 5 échecs.
- **Pas de HTTPS** dans le serveur lui-même. En production, on mettra un reverse proxy (nginx à l'étape 3, ou Caddy/Traefik) devant, avec `COOKIE_SECURE=true`.
- **Pas de changement de mot de passe, ni de suppression de compte** pour l'instant.
- Si plusieurs instances de l'API démarrent **en même temps** sur une base vide, elles pourraient appliquer la même migration ensemble. Ce n'est pas un problème avec un seul conteneur.

## Et après ?

L'**étape 2b** ajoutera :
- les routes pour les groupes de listes, les listes, les groupes, les tâches, les niveaux d'urgence et les tags ;
- la route `GET /lists/{id}/board`, qui renvoie tout ce qu'il faut pour afficher une liste ;
- les contrôles d'appartenance : chacun ne voit et ne modifie que ses propres données.
