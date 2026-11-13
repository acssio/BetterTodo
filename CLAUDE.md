# BetterTodo

Application de todo list **self-hosted**, **cross-platform** et **open source**, pour organiser des tâches personnelles.

## Vision

- Aucune app de todo existante ne convient vraiment à l'utilisateur : le but est d'en construire une qui corresponde exactement à ses attentes.
- **Self-hosted** : l'utilisateur héberge lui-même le serveur et garde la maîtrise de ses données.
- **Apps natives sur chaque plateforme**, avec le langage le plus adapté à chacune (pas de framework « un seul code pour tout »).
- **Open source**.

## Façon de travailler (important)

- **Avancer étape par étape.** Une étape à la fois, validée avant de passer à la suivante.
- **Planifier avant chaque étape.** Avant d'écrire du code ou de prendre une décision, proposer un plan (objectif, options, recommandation) et attendre la validation de l'utilisateur.
- **Ne pas prendre de décision technique structurante sans l'accord de l'utilisateur** (stack, base de données, architecture, hébergement…). Proposer, expliquer les compromis, recommander, puis laisser l'utilisateur trancher.
- **Un README par étape.** L'utilisateur aime programmer et apprendre, et veut pouvoir déboguer seul. Chaque étape livre un fichier `docs/etapes/NN-nom-de-l-etape.md` qui explique : ce qui a été fait et pourquoi, comment le code est organisé, comment le lancer et le tester, et où regarder en cas de problème.
- **Communiquer en français.** Commentaires du code en français.
- Consigner les décisions validées dans la section « Décisions » ci-dessous.

## Style de code de l'utilisateur

Observé dans ses autres projets (C++ embarqué, React/TypeScript). Écrire du code qui lui ressemble :

- **Simple et lisible avant tout.** Le code minimal qui marche, sans helpers ni abstractions en plus.
- **Orienté objet.** Des classes avec des responsabilités claires, y compris en React (composants classe : `interface XxxProps`, `class XxxState` avec valeurs par défaut).
- **Machines d'état** quand un comportement a des états (l'utilisateur en utilise beaucoup).
- **C++** : membres `_nom`, en-têtes Doxygen (`@file`, `@brief`, `@param`, `@return`), header guards, courts commentaires en fin de ligne.
- **TypeScript** : indentation 4 espaces, gestionnaires d'événements en fonctions fléchées nommées `onXxx` / `handleXxx`.
- **Ne modifier que ce qui est demandé.** Signaler les autres problèmes sans les corriger.
- Le code fait référence : relire les fichiers avant de proposer quoi que ce soit, l'utilisateur peut les avoir modifiés entre deux demandes.

## Fonctionnalités voulues

- **Groupes de listes** (repliables, dans la barre latérale) → contiennent des **listes** → qui contiennent des **groupes** → qui contiennent des **tâches**.
- Chaque liste a un nom, une couleur et une **icône open source [Lucide](https://lucide.dev)** (licence ISC). Toutes les icônes de l'interface viennent de Lucide, pour garder un style cohérent.
- Pour chaque tâche :
  - date d'échéance ;
  - tags ;
  - notes (facultatives) ;
  - niveau d'urgence : 3 niveaux par défaut, ajoutables ou supprimables dans les paramètres.
- **Deux façons d'afficher les groupes d'une liste :**
  - en colonnes côte à côte (style kanban) ;
  - en sections empilées verticalement.
- **Vue scindée** : glisser une liste pour diviser l'écran en deux (gauche / droite), comme les éditeurs côte à côte de VS Code.
- **Modifier une tâche avec un petit overlay** (popover) ouvert à côté de la tâche, et non dans une vue de détail plein écran ou un gros panneau à la OpenProject. Il doit rester léger et rapide.

## Ressources

- Maquette web (Figma, page « Web – Maquette v2 », en cours de validation) : https://www.figma.com/design/JgYAUOZOcp85qztm5JHHwq

## Pistes techniques (à confirmer étape par étape)

- **macOS / iOS** : app native avec Xcode (Swift).
- **Linux / Windows** : C++, probablement avec Qt.
- **Web** : un site aussi.
- **Serveur** : Docker, avec une base de données open source, et certainement une image pour le serveur web.

## Décisions

- **2026-11-13 – Base de données : PostgreSQL, dans Docker** (image officielle, données dans un volume). Raison : données très relationnelles (listes, groupes, tâches, tags), open source, référence du self-hosted.
- **2026-11-13 – Les apps ne parlent jamais directement à la base.** Elles passent par un serveur API, qui est le seul à accéder à PostgreSQL. La base n'est pas exposée hors de Docker.
- **2026-11-13 – Serveur API en Go.** Raison : déjà connu de l'utilisateur, bibliothèque standard HTTP complète, un seul binaire, petite image Docker.
- **2026-11-13 – Page web en React + TypeScript (Vite)**, servie par un conteneur nginx qui redirige aussi `/api` vers le serveur API.
- **2026-11-13 – Plusieurs comptes utilisateurs dès le départ.** Chaque utilisateur a ses propres listes, tags et niveaux d'urgence.
- **2026-11-13 – Découpage :** étape 1 = Docker + PostgreSQL + modèle de données ; étape 2 = API ; étape 3 = page web (vue colonnes). Chaque étape est validée avant la suivante.
- **2026-11-13 – API Go : `net/http` standard (pas de framework), `pgx` sans ORM (SQL écrit à la main), bcrypt pour les mots de passe.**
- **2026-11-13 – Migrations maison** dans `api/migrations/NNN_nom.sql`, incluses dans le binaire et appliquées au démarrage. Ne jamais modifier une migration déjà appliquée : en ajouter une nouvelle.
- **2026-11-13 – Sessions :** jeton aléatoire, seul son hash SHA-256 est en base. Envoyé en cookie `HttpOnly` (web) ou en `Authorization: Bearer` (apps natives).
- **2026-11-13 – Conventions de l'API :**
  - un `XxxHandler` par ressource dans `api/internal/handlers/` ;
  - PATCH partiels avec `Optional[T]` (absent / null / valeur) ;
  - ordre géré par `ordering.move` (positions renumérotées 0, 1, 2…) ;
  - appartenance vérifiée à chaque requête : 404 pour une ressource de l'URL, 400 pour un id envoyé dans le corps.
- **2026-11-13 – Tests API :** tests Go (`go test ./...`) et collection Postman `api/tests/`, lancée avec le Postman CLI.
- **2026-11-13 – Web :**
  - composants classe (`interface XxxProps`, `class XxxState`), CSS simple avec un fichier par composant ;
  - classe `ApiClient` pour tous les appels ; seuls `App` et `Workspace` ont un état ;
  - `lucide-react` avec une liste fixe d'icônes (`listIcons.ts`) ;
  - linter : oxlint (installé par Vite) ; tests : Vitest (`npm test`), surtout sur les fonctions pures de `utils/` ;
  - modifications « optimistes » dans `Workspace` : l'écran d'abord, puis l'API, et rechargement du board en cas d'erreur ;
  - ne jamais relire `this.state` juste après `setState` : passer la valeur en paramètre ;
  - l'interface doit rester lisible en fenêtre étroite (demande de l'utilisateur) :
    - barre latérale redimensionnable, avec un mode icônes (`utils/sidebarLayout.ts`) ;
    - en-tête en icônes seules via `@container main` ;
    - pas de texte qui passe à la ligne : on coupe avec « … » ;
  - servi par nginx sur `localhost:8000`, qui transmet `/api` au conteneur `api`.
- **2026-11-13 – Découpage de l'étape 3 :** 3a affichage, 3b modifications (overlay, glisser-déposer natif HTML5), 3c vue sections, vue scindée, paramètres.
- **2026-11-13 – Maquette web validée** (Figma, page « Web – Maquette v2 »).

## Questions ouvertes

- Synchronisation entre appareils (fonctionnement hors ligne ?).
