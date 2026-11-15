# Installer BetterTodo avec Docker

Ce tuto part d'une machine **sans Docker** et va jusqu'à BetterTodo ouvert dans le navigateur. Compter 10 à 15 minutes, surtout pour l'installation de Docker.

BetterTodo tourne dans **trois conteneurs** (voir `docker-compose.yml`) :

| Conteneur | Rôle | Port sur ta machine |
|---|---|---|
| `db` | PostgreSQL, les données | `127.0.0.1:5432` |
| `api` | Le serveur API en Go | `127.0.0.1:8080` |
| `web` | La page web (nginx), qui transmet `/api` au serveur | `127.0.0.1:8000` |

Tu n'as besoin d'installer **ni Go, ni Node, ni PostgreSQL** : Docker télécharge et compile tout lui-même.

## 1. Installer Docker

Il faut **Docker** et son outil **Docker Compose** (la commande `docker compose`).

### macOS

1. Télécharger **Docker Desktop** sur https://docs.docker.com/desktop/setup/install/mac-install/
   - choisir **Apple Silicon** (puces M1, M2…) ou **Intel** selon ton Mac ( → À propos de ce Mac) ;
   - ou avec Homebrew : `brew install --cask docker`.
2. Glisser **Docker.app** dans Applications, puis l'ouvrir.
3. Accepter les conditions et attendre que la baleine de la barre des menus arrête de s'animer : Docker est prêt.
4. Conseillé : Docker Desktop → Réglages → General → cocher **Start Docker Desktop when you sign in**, pour que BetterTodo redémarre avec ton Mac.

### Windows

1. Télécharger **Docker Desktop** sur https://docs.docker.com/desktop/setup/install/windows-install/
2. Pendant l'installation, garder l'option **Use WSL 2** cochée. Si Windows le demande, installer WSL :
   ```powershell
   wsl --install
   ```
   puis redémarrer le PC.
3. Lancer Docker Desktop et attendre qu'il affiche **Engine running**.
4. Les commandes de la suite se tapent dans **PowerShell**. Seule différence : `cp` devient `copy`.

### Linux (Debian / Ubuntu)

Sur un serveur, pas besoin de Docker Desktop : on installe **Docker Engine**, avec le script officiel :

```bash
curl -fsSL https://get.docker.com -o get-docker.sh
sudo sh get-docker.sh
```

Puis, pour lancer `docker` sans `sudo` (se déconnecter et se reconnecter ensuite) :

```bash
sudo usermod -aG docker $USER
```

Le script installe aussi Docker Compose et démarre Docker automatiquement au boot. Pour les autres distributions : https://docs.docker.com/engine/install/

### Vérifier

```bash
docker --version
docker compose version
docker run --rm hello-world
```

La dernière commande doit afficher **Hello from Docker!**. Sinon, voir « En cas de problème » plus bas.

> Attention : c'est `docker compose` (avec un espace). L'ancienne commande `docker-compose` (avec un tiret) n'est plus maintenue.

## 2. Récupérer BetterTodo

Avec git :

```bash
git clone https://github.com/acssio/BetterTodo.git
cd BetterTodo
```

Sans git : sur la page GitHub, **Code → Download ZIP**, décompresser, et ouvrir un terminal dans le dossier.

## 3. Choisir le mot de passe de la base

Le fichier `.env` contient les réglages et le mot de passe de PostgreSQL. Il n'est jamais commité.

```bash
cp .env.example .env
```

Ouvrir `.env` et remplacer `change-moi` par un vrai mot de passe, **lettres et chiffres seulement** (il est placé dans une URL de connexion). Pour en générer un :

```bash
openssl rand -hex 24
```

> Ce mot de passe est fixé **au premier démarrage**. Le changer ensuite dans `.env` ne suffit plus (voir « En cas de problème »).

Les autres réglages de `.env` :

| Variable | Rôle |
|---|---|
| `SESSION_DAYS` | Combien de jours on reste connecté (30 par défaut) |
| `COOKIE_SECURE` | Mettre `true` quand le site est servi en HTTPS |

## 4. Démarrer

```bash
docker compose up -d --build --wait
```

- `--build` : compile l'API et la page web. Le premier démarrage prend quelques minutes ; les suivants sont rapides ;
- `-d` : les conteneurs tournent en arrière-plan, le terminal est libéré ;
- `--wait` : la commande attend que les trois conteneurs soient **prêts** (healthcheck).

Vérifier que tout va bien :

```bash
docker compose ps
```

Les trois lignes doivent afficher **healthy**.

## 5. Créer le compte administrateur

1. Ouvrir http://localhost:8000
2. Au premier lancement, la page affiche **« Créer le compte administrateur »**.
3. Nom, email, mot de passe (8 caractères minimum).

Ce premier compte est l'**admin**. Il n'y a pas d'inscription libre : pour les autres personnes, l'admin ouvre **Console admin** (en bas de la barre latérale, au-dessus de « Paramètres »). Il y crée leur compte, puis leur donne l'email et un mot de passe provisoire : à leur première connexion, elles choisissent le leur. Chacun peut ensuite le changer dans **Paramètres**. Chaque compte a ses propres listes, tags et niveaux d'urgence.

> Pour essayer avec des données toutes prêtes, on peut charger le compte de démo de la maquette (identifiants en tête du fichier). Chargé sur une installation vide, le compte de démo devient l'admin :
> ```bash
> docker compose exec -T db sh -c 'psql -U "$POSTGRES_USER" -d "$POSTGRES_DB"' < db/exemple/exemple.sql
> ```

## Au quotidien

| Je veux… | Commande |
|---|---|
| Arrêter BetterTodo (les données restent) | `docker compose stop` |
| Le redémarrer | `docker compose start` |
| Voir ce qui se passe | `docker compose logs -f` (Ctrl+C pour quitter) |
| Les logs d'un seul conteneur | `docker compose logs -f api` |
| Mettre à jour | `git pull` puis `docker compose up -d --build --wait` |

Grâce à `restart: unless-stopped`, les conteneurs redémarrent seuls quand Docker démarre (au démarrage de la machine, si Docker est lancé automatiquement).

Les mises à jour ne touchent pas aux données : elles sont dans le volume `db-data`, et les **migrations** de l'API font évoluer la base au démarrage.

## Sauvegarder ses données

Self-hosted veut dire que **la sauvegarde, c'est toi**. Pour exporter toute la base dans un fichier :

```bash
docker compose exec -T db sh -c 'pg_dump -U "$POSTGRES_USER" -d "$POSTGRES_DB"' > sauvegarde.sql
```

Pour la restaurer, dans une base vide (après `docker compose down -v`, puis `docker compose up -d --wait db`) :

```bash
docker compose exec -T db sh -c 'psql -U "$POSTGRES_USER" -d "$POSTGRES_DB"' < sauvegarde.sql
```

Puis `docker compose up -d --wait` pour relancer l'API et la page web.

## Ouvrir BetterTodo depuis un autre appareil

Par sécurité, tous les ports sont limités à **ta machine** (`127.0.0.1:`). Pour ouvrir la page web depuis ton téléphone ou un autre PC du réseau local :

1. Dans `docker-compose.yml`, service `web`, remplacer `"127.0.0.1:8000:80"` par `"8000:80"`.
2. `docker compose up -d --wait`
3. Ouvrir `http://<adresse-IP-de-la-machine>:8000`.

Ne fais pas ça pour `db` ni `api` : seule la page web doit être visible, elle transmet `/api` elle-même.

> **Sur Internet** : ne pas exposer le port 8000 tel quel. Il faut d'abord du **HTTPS**, par exemple avec un reverse proxy (Caddy, Traefik, nginx…), puis `COOKIE_SECURE=true` dans `.env`. Sans HTTPS, ton mot de passe circule en clair.

## Tout désinstaller

```bash
docker compose down -v --rmi local
```

- `down` : arrête et supprime les conteneurs ;
- `-v` : supprime le volume, donc **toutes les données** (faire une sauvegarde avant !) ;
- `--rmi local` : supprime les images compilées de l'API et de la page web.

## En cas de problème

| Symptôme | Cause probable | Solution |
|---|---|---|
| `Cannot connect to the Docker daemon` / `failed to connect to the docker API` | Docker n'est pas lancé | Ouvrir Docker Desktop et attendre qu'il soit prêt. Sous Linux : `sudo systemctl start docker` |
| `permission denied … docker.sock` (Linux) | Ton utilisateur n'est pas dans le groupe `docker` | `sudo usermod -aG docker $USER`, puis se déconnecter et se reconnecter |
| `docker: 'compose' is not a docker command` | Ancienne version de Docker | Mettre Docker à jour (Docker Desktop, ou le script `get-docker.sh`) |
| `port is already allocated` | Un autre programme utilise déjà 5432, 8080 ou 8000 | L'arrêter, ou changer le port de gauche dans `docker-compose.yml` (ex. `127.0.0.1:8001:80`) |
| `--wait` échoue, un conteneur est `unhealthy` | Voir ses logs | `docker compose logs api` (ou `db`, `web`) |
| `password authentication failed` dans les logs de l'API | Le mot de passe de `.env` a changé après le premier démarrage | Remettre l'ancien, ou repartir de zéro avec `docker compose down -v` (**efface les données**) |
| La page s'ouvre mais affiche une erreur de connexion au serveur | L'API ne tourne pas | `docker compose ps`, puis `docker compose logs api` |
| WSL 2 manquant (Windows) | WSL pas installé | `wsl --install` dans PowerShell (administrateur), puis redémarrer |
