# BetterTodo
A self hosted, cross platform, open source Todo List

## Démarrer

Voir [docs/etapes](docs/etapes) : un document par étape du développement, qui explique ce qui a été fait et comment le lancer.

1. [Base de données PostgreSQL](docs/etapes/01-base-de-donnees.md)
2. [Serveur API : fondations, migrations et comptes](docs/etapes/02a-api-fondations.md)
3. [Serveur API : listes, groupes et tâches](docs/etapes/02b-api-donnees.md)
4. [Page web : affichage](docs/etapes/03a-web-affichage.md)
5. [Page web : modifications](docs/etapes/03b-web-modifications.md)
6. [Page web : vue sections et gestion des listes](docs/etapes/03c1-web-sections-listes.md)

En bref :

```bash
cp .env.example .env          # puis changer le mot de passe
docker compose up -d --build --wait
```

Puis ouvrir http://localhost:8000.
