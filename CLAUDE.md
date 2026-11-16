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
  - classe `ApiClient` pour tous les appels ; seuls `App` et `Workspace` parlent à l'API, sauf `SettingsDialog` (écran à part avec ses propres données, il prévient Workspace par des callbacks) ;
  - `lucide-react` avec une liste fixe d'icônes (`listIcons.ts`) ;
  - linter : oxlint (installé par Vite) ; tests : Vitest (`npm test`), surtout sur les fonctions pures de `utils/` ;
  - modifications « optimistes » dans `Workspace` : l'écran d'abord, puis l'API, et rechargement du board en cas d'erreur ;
  - ne jamais relire `this.state` juste après `setState` : passer la valeur en paramètre, et pour calculer un nouvel état à partir de l'ancien, toujours `setState(state => …)` (bug rencontré deux fois) ;
  - vue scindée : `Workspace.panes` (1 ou 2 moitiés, un board chacune) ; les modifications de tâches s'appliquent à tous les boards (`updateBoards`) ;
  - l'interface doit rester lisible en fenêtre étroite (demande de l'utilisateur) :
    - barre latérale redimensionnable, avec un mode icônes (`utils/sidebarLayout.ts`) ;
    - en-tête en icônes seules via `@container main` ;
    - pas de texte qui passe à la ligne : on coupe avec « … » ;
  - servi par nginx sur `localhost:8000`, qui transmet `/api` au conteneur `api`.
  - **jamais de `window.confirm` / `window.alert`** : le navigateur intégré de l'app Claude les désactive (confirm répond « non » sans rien afficher). Utiliser `confirmDialog.ask(…)` / `confirmDialog.inform(…)` (`components/confirm.ts`, affiché par `ConfirmHost`) ;
  - **tester avec de vrais clics** dans le navigateur. Ne pas remplacer `window.confirm` ni simuler ce qui peut être fait pour de vrai : deux bugs (vider les tâches faites, vue scindée) étaient masqués par la simulation ;
  - vue scindée : possible dès que la place pour les listes (fenêtre moins barre latérale) atteint 640 px ; sinon, un message l'explique.
- **2026-11-13 – Découpage de l'étape 3 :** 3a affichage, 3b modifications (overlay, glisser-déposer natif HTML5), 3c vue sections, vue scindée, paramètres.
- **2026-11-13 – Étape 3c en trois morceaux**, livrés dans cet ordre, chacun avec son commit et son README :
  - **3c-1** : vue sections (glisser-déposer commun aux deux vues), bascule Colonnes/Sections enregistrée sur la liste (`default_view`), overlay « Modifier une liste », renommer / supprimer un groupe de listes ;
  - **3c-2** : écran Paramètres (niveaux d'urgence, tags, vue par défaut). La vue par défaut est une **préférence du compte** : migration 003 `users.default_view` + route `PATCH /me`, qui permet aussi de changer le nom affiché ;
  - **3c-3** : vue scindée (glisser une liste à droite ou Cmd+clic), deux boards dans `Workspace`, **glisser une tâche d'une liste à l'autre** inclus.
- **2026-11-15 – Nouveaux morceaux, dans cet ordre : 3c-2b, 3c-3, 3d, puis la traduction.**
  - **3c-2b** :
    - chaque nouvelle liste reçoit un **groupe par défaut**, créé par le serveur et nommé selon la langue du compte (« Tâches » / « Tasks ») ;
    - **afficher / masquer les tâches faites**, par liste, retenu sur le serveur (`lists.show_done`) ;
    - **vider les tâches faites** d'une liste (route dédiée, une seule transaction) ;
    - **langue du compte** (`users.language`, 'fr' ou 'en', migration 004), choisie dans les Paramètres.
  - **3d** : listes intelligentes « **Aujourd'hui** » (échéance aujourd'hui **ou en retard**, pas faites) et « **Planifié** » (toutes les tâches avec une échéance), en haut de la barre latérale. Plan validé le 2026-11-15 :
    - **Aujourd'hui** : deux sections, « En retard » puis « Aujourd'hui » ;
    - **Planifié** : toutes les échéances, une section par date (En retard, Aujourd'hui, Demain, puis chaque date) ;
    - **seulement les tâches à faire** : une tâche cochée disparaît après une courte animation ;
    - chaque tâche affiche sa liste d'origine ; on peut cocher et ouvrir l'overlay ; ces vues s'ouvrent aussi en vue scindée ;
    - API : `GET /tasks/due?until=` et `GET /tasks/due/counts?today=` ; **la date du jour est envoyée par le client** (fuseau horaire local) ;
    - **pas** de réordonnancement, d'ajout de tâche ni de glisser vers une liste dans ces vues (peut-être plus tard).
  - **Traduction complète de l'interface** (fr / en) : étape à part, après la 3d. D'ici là, la langue ne sert qu'au nom du groupe par défaut.
- **2026-11-15 – Étape 3e : compte admin et icônes** (plan validé le 2026-11-15) :
  - **plus d'inscription libre** : `POST /auth/setup` crée le **premier** compte, qui est admin (`users.is_admin`, migration 005), puis répond 403. La page affiche « Créer le compte administrateur » tant que `GET /auth/setup` renvoie `needs_setup` ;
  - installations existantes : le compte **le plus ancien** devient admin ;
  - **console admin** (`AdminDialog`, bouton au-dessus de « Paramètres », visible seulement pour l'admin) : lister, créer, supprimer un compte (`ON DELETE CASCADE`, pas sur soi-même) et **changer un mot de passe** (ferme les sessions du compte) ; routes `/admin/users` protégées par `AdminHandler.RequireAdmin` ;
  - collections Postman : lancées avec `--env-var adminEmail=… --env-var adminPassword=…`, elles créent puis suppriment leurs comptes de test via l'admin ;
  - icônes : liste fixe étendue à environ 140 icônes Lucide classées par thème (`listIcons.ts`), recherche en français. Ne jamais retirer une icône déjà proposée.
- **2026-11-15 – Étape 3f : changer son mot de passe** (plan validé le 2026-11-15) :
  - `users.must_change_password` (migration 006) : vrai quand l'admin crée un compte ou change le mot de passe de quelqu'un d'autre ; la page web affiche alors `ChangePasswordPage` (état `mustChangePassword` de `App`) avant les listes ;
  - obligation **côté interface seulement** : l'API envoie le drapeau mais ne bloque pas les autres routes ;
  - `POST /me/password` demande **toujours** l'ancien mot de passe (même sur l'écran obligatoire), et ferme les autres sessions du compte ;
  - partie « Mot de passe » dans les Paramètres, erreurs affichées sous le formulaire.
- **2026-11-15 – Étape 4 : intégration MCP** (Claude lit et écrit les tâches) :
  - **jetons d'accès personnels** (`api_tokens`, migration 007) : préfixe `btd_`, seul le hash SHA-256 en base, pas d'expiration, créés / révoqués dans Paramètres › Jetons d'accès (affichés une seule fois) ;
  - un jeton marche partout **sauf** sur les routes sensibles (`RequireSession` → 403) : mot de passe, `/me/tokens`, console admin, déconnexion ;
  - **serveur MCP local (stdio)** en Go, module à part dans `mcp/`, SDK officiel `modelcontextprotocol/go-sdk`. Client de l'API (`BETTERTODO_URL` + `BETTERTODO_TOKEN`), jamais de la base ;
  - outils : lire listes / liste / échéances / tags et urgences, créer et modifier une tâche (cocher compris). **Aucune suppression** ;
  - ajout validé le 2026-11-15 : **créer et renommer** groupes de listes, listes et groupes de tâches. Couleur et icône d'une liste choisies **dans la palette de la page web** : copie dans `mcp/palette.go`, vérifiée par un test contre `listIcons.ts` (à mettre à jour ensemble) ;
  - l'instance de l'utilisateur tourne sur un **serveur distant** : le binaire tourne sur le Mac et appelle l'API en HTTPS. Tuto : `docs/connecter-claude.md` ;
  - plus tard peut-être : mode MCP distant (HTTP + OAuth) pour claude.ai web / mobile.
- **2026-11-16 – Étape 3g : édition rapide et sélection**, en deux morceaux :
  - **3g-1** : colonnes cachées signalées par une **barre de défilement fine toujours visible** et un **fondu + bouton chevron** sur le bord (`utils/scrollEdges.ts`, états none / left / right / both) ;
  - **3g-2** : clic sur une tâche = modifier le titre sur place (`TitleEditor`) ; Cmd/Ctrl+clic = sélection multiple (`Workspace.selectedTaskIds`) pour **supprimer** (Suppr / ⌫ ou barre en bas) et **déplacer** (glisser une tâche sélectionnée) plusieurs tâches ; l'overlay s'ouvre par un **petit bouton affiché au survol** (pas de double-clic) ou Entrée ;
  - déplacement multiple **sans nouvelle route d'API** : un `PATCH /tasks/{id}` par tâche, envoyés **l'un après l'autre** (`dropAnchor` + `multiMovePositions`). Une route en lot reste possible plus tard ;
  - props des tâches : `opened` = overlay ouvert, `selected` = dans la sélection.
- **2026-11-16 – Étape 5 : version mobile du site** (plan validé le 2026-11-16, avant la traduction) :
  - maquette : page de maquettes « BetterTodo Mobile » (https://claude.ai/artifact/7kQ8j1JvhKTZ5TkMjEwLDM), pas de version Figma ;
  - appareils visés : iPhone 13 mini (375 × 812) → Galaxy Z Flip 6 ouvert (~412 × 1000) → iPhone 17 Pro Max (440 × 956). Largeurs fluides, `100dvh`, `env(safe-area-inset-*)`, champs en 16 px minimum (sinon iOS zoome) ;
  - **même application React** : sous 600 px de large (ou écran tactile de moins de 500 px de haut), `Workspace` passe en mode mobile (`utils/mobileLayout.ts`, classe `.Workspace.mobile`). Il garde toute la logique ; `renderMobile` affiche un écran à la fois, machine d'état `mobileScreen` (home → list), avec une étape dans l'historique du navigateur pour que « retour » revienne à l'accueil. L'accueil est `MobileHome` ; les listes réutilisent `ListHeader` (flèche ‹ via `onBack`), `Board`, `SectionsView`, `SmartView` (prop `mobile`) ; Paramètres et console admin en plein écran ;
  - en mode mobile, **toucher une tâche ouvre l'overlay** (`tapToOpen`), il n'y a pas de titre modifié sur place ; l'overlay est `TaskPopover` avec `sheet`, remonté au-dessus du clavier (`visualViewport`) ;
  - navigation par écrans (Accueil → Liste, flèche ‹) ; vue colonnes = un groupe à la fois, pleine largeur, `scroll-snap` + onglets ; overlay de tâche = panneau qui monte du bas ; pas de vue scindée ; pas de glisser-déposer au doigt (on déplace avec « Déplacer ») ;
  - **5-1** mode mobile et écrans ; **5-2** Quick task (bouton flottant sur l'accueil, liste / groupe / échéance, dernier groupe utilisé retenu dans `localStorage`, par appareil) ; **5-3** sélection par appui long et « Déplacer ».
  - **5-2 (validé le 2026-11-16)** : `QuickTaskSheet` (panneau du bas) ; liste et groupe en menus déroulants natifs ; échéance « Aujourd'hui » par défaut (Demain / Date / Aucune) ; après création, panneau fermé + message ; mémoire `bettertodo.quickTask` écrite seulement après une création réussie (`utils/quickTask.ts`). Pas de changement d'API (`due_date` déjà accepté à la création).
- **2026-11-16 – Tâches faites masquées par défaut** : une nouvelle liste est créée avec `show_done = false` (migration 008, valeur par défaut seulement : les listes existantes gardent leur réglage).
- **2026-11-13 – Maquette web validée** (Figma, page « Web – Maquette v2 »).

## Questions ouvertes

- Synchronisation entre appareils (fonctionnement hors ligne ?).
