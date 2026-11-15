# BetterTodo
A self hosted, cross platform, open source Todo List

## Installer

Pas encore Docker sur ta machine ? Suivre le tuto **[Installer BetterTodo avec Docker](docs/installation.md)** : Docker sur macOS, Windows ou Linux, premier démarrage, sauvegardes et dépannage.

En bref, si Docker est déjà là :

```bash
cp .env.example .env          # puis changer le mot de passe
docker compose up -d --build --wait
```

Puis ouvrir http://localhost:8000.

## Connecter Claude (MCP)

Claude (Claude Code, Claude Desktop) peut lire, créer, modifier et cocher tes tâches, et créer ou renommer des listes et des groupes, mais rien supprimer. Il passe par un jeton d'accès créé dans les Paramètres. Tuto : **[Connecter Claude à BetterTodo](docs/connecter-claude.md)**.

## Comprendre le code

Voir [docs/etapes](docs/etapes) : un document par étape du développement, qui explique ce qui a été fait et comment le lancer.

1. [Base de données PostgreSQL](docs/etapes/01-base-de-donnees.md)
2. [Serveur API : fondations, migrations et comptes](docs/etapes/02a-api-fondations.md)
3. [Serveur API : listes, groupes et tâches](docs/etapes/02b-api-donnees.md)
4. [Page web : affichage](docs/etapes/03a-web-affichage.md)
5. [Page web : modifications](docs/etapes/03b-web-modifications.md)
6. [Page web : vue sections et gestion des listes](docs/etapes/03c1-web-sections-listes.md)
7. [Page web : paramètres](docs/etapes/03c2-web-parametres.md)
8. [Tâches faites, groupe par défaut, langue](docs/etapes/03c2b-web-taches-faites-langue.md)
9. [Vue scindée](docs/etapes/03c3-web-vue-scindee.md)
10. [Listes intelligentes : Aujourd'hui et Planifié](docs/etapes/03d-listes-intelligentes.md)
11. [Compte administrateur, console admin, plus d'icônes](docs/etapes/03e-admin-et-icones.md)
12. [Changer son mot de passe](docs/etapes/03f-mot-de-passe.md)
13. [Intégration MCP : jetons d'accès et serveur MCP pour Claude](docs/etapes/04-integration-mcp.md)
