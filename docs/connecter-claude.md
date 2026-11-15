# Connecter Claude à BetterTodo (MCP)

Ce tuto branche **Claude Code** et/ou **Claude Desktop** sur **ton instance BetterTodo installée sur un serveur distant**. Une fois fait, tu peux demander à Claude :

> « Qu'est-ce que j'ai à faire aujourd'hui ? »
> « Ajoute *Appeler le garage* dans Perso pour vendredi, urgence haute, tag #voiture. »
> « Coche *Acheter du pain*. »

Comment ça marche : sur ton Mac, Claude lance un petit programme, `bettertodo-mcp`. Ce programme appelle l'API de ton serveur avec un **jeton d'accès**, sans ton mot de passe. Claude peut lire, créer, modifier et cocher des tâches, mais **jamais en supprimer**. Les détails techniques sont dans [etapes/04-integration-mcp.md](etapes/04-integration-mcp.md).

## Ce qu'il te faut

- ton serveur BetterTodo accessible en **HTTPS**, par exemple `https://todo.exemple.fr`. Le jeton voyage dans chaque requête : en HTTP simple, il circulerait en clair ;
- sur le Mac : **Go**, pour compiler le programme (`brew install go`), et le dépôt BetterTodo cloné ;
- Claude Code et/ou Claude Desktop.

Dans la suite, remplace `https://todo.exemple.fr` par l'adresse de **ta page web**, celle que tu ouvres dans le navigateur, **sans** `/api`.

## 1. Mettre le serveur à jour

Sur le serveur, dans le dossier de BetterTodo :

```bash
git pull
```

```bash
docker compose up -d --build --wait
```

Au démarrage, l'API applique la migration `007_api_tokens.sql`. Pour vérifier : `docker compose logs api | grep migrations` doit afficher `migrations appliquées : [007_api_tokens.sql]`.

## 2. Créer un jeton d'accès

1. Ouvre BetterTodo dans le navigateur, puis **Paramètres** (en bas de la barre latérale).
2. Tout en bas, dans **Jetons d'accès**, clique sur **Créer un jeton**, donne-lui un nom (par exemple « Claude sur le MacBook ») et appuie sur Entrée.
3. **Copie le jeton** (`btd_…`) tout de suite : il ne sera plus jamais affiché. Si tu le perds, révoque-le et crée-en un autre.

Prends un jeton par appareil : tu pourras en couper un sans toucher aux autres.

## 3. Compiler `bettertodo-mcp` sur le Mac

Depuis le dossier du dépôt cloné sur ton Mac :

```bash
mkdir -p ~/.local/bin
```

```bash
cd mcp && go build -o ~/.local/bin/bettertodo-mcp .
```

Vérifie qu'il démarre. Il doit se plaindre de la configuration manquante, c'est normal :

```bash
~/.local/bin/bettertodo-mcp
```

Tu dois voir `bettertodo-mcp : BETTERTODO_URL et BETTERTODO_TOKEN sont obligatoires…`.

Les applications Claude ont besoin du **chemin absolu** du programme. Pour l'obtenir :

```bash
echo ~/.local/bin/bettertodo-mcp
```

Il ressemble à `/Users/<toi>/.local/bin/bettertodo-mcp`.

> Après un `git pull` qui modifie `mcp/`, relance le `go build`, puis redémarre Claude.

## 4a. Brancher Claude Code

Dans un terminal, en une seule commande, avec ton adresse et ton jeton :

```bash
claude mcp add --transport stdio --scope user --env BETTERTODO_URL=https://todo.exemple.fr --env BETTERTODO_TOKEN=btd_TON_JETON bettertodo -- /Users/<toi>/.local/bin/bettertodo-mcp
```

- `--scope user` : BetterTodo sera disponible dans **tous** tes projets, pas seulement dans le dossier courant ;
- tout ce qui suit `--` est la commande que Claude Code lance.

Pour vérifier :

```bash
claude mcp list
```

La ligne `bettertodo` doit indiquer qu'il est connecté. Ouvre ensuite une **nouvelle** conversation Claude Code et demande « Quelles sont mes listes BetterTodo ? ». La première fois, Claude Code te demande l'autorisation d'utiliser chaque outil.

Pour le retirer : `claude mcp remove bettertodo --scope user`.

## 4b. Brancher Claude Desktop

1. Dans Claude Desktop : **Réglages › Développeur › Modifier la configuration**. Cela ouvre le dossier qui contient `claude_desktop_config.json` (`~/Library/Application Support/Claude/`).
2. Ouvre ce fichier dans un éditeur et ajoute `bettertodo` dans `mcpServers`. S'il est vide, mets tout ceci :

```json
{
    "mcpServers": {
        "bettertodo": {
            "command": "/Users/<toi>/.local/bin/bettertodo-mcp",
            "env": {
                "BETTERTODO_URL": "https://todo.exemple.fr",
                "BETTERTODO_TOKEN": "btd_TON_JETON"
            }
        }
    }
}
```

   S'il contient déjà d'autres serveurs, ajoute seulement le bloc `"bettertodo": { … }` à côté d'eux, séparé par une virgule.

3. **Quitte complètement** Claude Desktop (Cmd+Q) et relance-le.
4. Dans une conversation, le menu des outils (sous la zone de saisie) doit afficher **bettertodo** avec ses 6 outils.

## 5. Essayer

Quelques demandes pour commencer :

- « Qu'est-ce que j'ai à faire aujourd'hui dans BetterTodo ? »
- « Montre-moi toutes mes échéances de la semaine. »
- « Ajoute *Réserver le restaurant* dans la liste Perso pour samedi. »
- « Passe *Réserver le restaurant* en urgence haute et ajoute la note *4 personnes*. »
- « Coche *Réserver le restaurant*. »

La page web ne se met pas à jour toute seule : recharge-la pour voir les changements faits par Claude.

## Révoquer l'accès

Dans **Paramètres › Jetons d'accès**, clique sur la corbeille du jeton, puis sur **Révoquer**. Claude reçoit aussitôt « Jeton d'accès invalide ou révoqué ». Penses-y si tu perds ton Mac, ou si tu as partagé par erreur un fichier de configuration qui contient le jeton.

Changer ton mot de passe **ne révoque pas** les jetons.

## Sécurité, en bref

- Le jeton est écrit **en clair** dans la configuration de Claude (`~/.claude.json` pour Claude Code, `claude_desktop_config.json` pour Desktop). Ne publie pas ces fichiers.
- Un jeton ne peut **pas** changer ton mot de passe, créer d'autres jetons ni ouvrir la console admin. S'il fuit, révoque-le : ton compte reste à toi.
- Le serveur MCP n'a pas d'outil de suppression. Au pire, Claude coche ou modifie une tâche par erreur, et tu peux annuler dans la page web.

## En cas de problème

| Symptôme | Solution |
|---|---|
| `claude mcp list` indique un échec de connexion | Lance le chemin absolu à la main (étape 3) : le programme existe-t-il ? Est-il exécutable ? |
| Claude répond « 401 : Jeton d'accès invalide ou révoqué » | Jeton mal copié (il doit commencer par `btd_`) ou révoqué : crée-en un nouveau et mets à jour la configuration |
| « impossible de joindre BetterTodo » | Vérifie `BETTERTODO_URL` : adresse de la page web, en `https://`, **sans** `/api` à la fin. Teste-la dans le navigateur |
| Erreurs 404 sur tout | Il y a probablement `/api` en trop dans `BETTERTODO_URL` |
| Le serveur n'a pas la partie « Jetons d'accès » | Il n'est pas à jour : refais l'étape 1 |
| Claude Desktop ne montre pas les outils | JSON invalide (une virgule en trop ?) ou application pas vraiment relancée. Journal : `~/Library/Logs/Claude/mcp-server-bettertodo.log` |
| Tester le jeton sans Claude | `curl -H "Authorization: Bearer btd_TON_JETON" https://todo.exemple.fr/api/v1/me` doit renvoyer ton profil |

Pas encore possible : utiliser BetterTodo depuis **claude.ai** (web, téléphone). Il faudrait un serveur MCP **distant**, hébergé sur ton serveur, avec OAuth. C'est une piste pour plus tard.
