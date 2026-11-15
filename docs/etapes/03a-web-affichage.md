# Étape 3a : la page web (affichage)

## Objectif

Afficher ses vraies données dans le navigateur, comme dans la maquette Figma :
- connexion et inscription ;
- barre latérale avec les groupes de listes (repliables) et les listes avec leur icône Lucide ;
- vue colonnes de la liste sélectionnée.

On ne peut encore rien modifier, sauf replier un groupe de listes. Les boutons de modification sont visibles mais grisés : leur info-bulle indique l'étape où ils arriveront (3b ou 3c).

## Fichiers

```
docker-compose.yml          → ajoute le service "web" (http://localhost:8000)
web/
  package.json              → dépendances : react, lucide-react ; outils : vite, typescript, oxlint
  vite.config.ts            → en dev, redirige /api vers le serveur Go (localhost:8080)
  index.html                → la page HTML de départ (titre, icône)
  public/favicon.svg        → icône de l'onglet
  nginx.conf, Dockerfile    → l'image de production
  src/
    main.tsx                → démarre React
    index.css               → thème : couleurs de la maquette en variables CSS
    App.tsx                 → machine d'état : chargement / déconnecté / connecté
    api/
      types.ts              → les types des données de l'API
      ApiClient.ts          → classe ApiClient : un appel = une méthode
    utils/dates.ts          → « Aujourd'hui », « Demain », « 18 nov. », retard
    components/
      LoginPage.tsx/.css    → connexion et inscription
      Workspace.tsx/.css    → écran principal : charge les données et les distribue
      Sidebar.tsx/.css      → barre latérale
      ListHeader.tsx/.css   → en-tête de la liste
      Board.tsx/.css        → vue colonnes
      TaskCard.tsx/.css     → une carte de tâche
      ListIcon.tsx/.css     → icône d'une liste (Lucide, dans sa couleur)
      listIcons.ts          → les icônes Lucide disponibles pour les listes
```

## Comment ça marche

### L'architecture

```
App                      ← sait si on est connecté (machine d'état)
├─ LoginPage             ← si déconnecté
└─ Workspace             ← si connecté : charge dossiers, listes, urgences et board
   ├─ Sidebar            ← affiche ; prévient Workspace des clics (onSelectList…)
   └─ main
      ├─ ListHeader
      └─ Board
         └─ TaskCard × n
```

Les données **descendent** par les props, et les actions **remontent** par des fonctions (`onSelectList`, `onToggleFolder`, `onLogout`). Seuls `App` et `Workspace` ont un état et parlent à l'API. Les autres composants ne font qu'afficher ce qu'on leur donne. Pour comprendre un bug d'affichage, il suffit donc de regarder les props du composant ; pour un bug de données, il faut regarder `Workspace`.

Tous les composants sont des **classes**, comme dans tes exercices :
- `interface XxxProps` pour les props ;
- `class XxxState` avec des valeurs par défaut pour l'état ;
- des gestionnaires en fonctions fléchées `onXxx = () => {…}`, pour que `this` reste le composant sans `.bind(this)`.

### La machine d'état de `App`

```
loading ──(GET /me réussit)──► loggedIn
   │                              │   ▲
   └──(401)──► loggedOut ◄────────┘   │ (connexion / inscription)
                    │ (déconnexion,   │
                    │  session expirée)
                    └─────────────────┘
```

Au démarrage, `App` appelle `/me`. Si le cookie de session est encore valide, on arrive directement sur ses listes : **recharger la page ne déconnecte pas**.

### Le chemin d'une requête

```
Navigateur ──► localhost:8000 (nginx, conteneur web)
                 ├─ /             → fichiers de la page (index.html, assets/…)
                 └─ /api/…        → transmis à api:8080 (conteneur api)
```

La page et l'API ont la **même adresse** : le navigateur envoie le cookie de session sans configuration particulière (pas de CORS). En développement, `vite.config.ts` fait la même chose que nginx.

### `ApiClient`

Chaque route est une méthode typée : `api.getBoard(listId)` renvoie une `Promise<Board>`. Si le serveur répond avec une erreur, la méthode lance une `ApiError`, qui contient le code HTTP et le message du serveur (`"Email ou mot de passe incorrect"`…). `Workspace.handleError` s'en sert :
- **401** : la session a expiré, retour à la connexion avec un message ;
- autre erreur : bandeau rouge en haut de l'écran.

### Petits détails

- **Changer vite de liste** : si on clique sur une 2ᵉ liste avant que la 1re ait fini de charger, la réponse de la 1re est ignorée. Sinon, elle pourrait s'afficher par-dessus.
- **Replier un groupe de listes** : l'écran change tout de suite, puis l'API enregistre le choix. Il est retrouvé sur tous les appareils.
- **Dernière liste ouverte** : retenue dans le `localStorage` du navigateur (propre à chaque navigateur). Si le stockage est bloqué (navigation privée), on ouvre simplement la première liste.
- **Dates** : `"2026-11-18"` est lue comme une date **locale**. Avec `new Date("2026-11-18")`, JavaScript la lirait en UTC, et en Suisse on afficherait parfois la veille.
- **Couleurs de pastille** : la couleur du niveau d'urgence, avec `1F` ajouté au code hexadécimal pour le fond (≈ 12 % d'opacité).
- **Icônes** : seules les icônes de `listIcons.ts` sont incluses dans le fichier JavaScript final, pas les 1 500 de Lucide. Une icône inconnue s'affiche comme l'icône `list`.

## Lancer

### Comme en production (Docker)

```bash
docker compose up -d --build --wait
```

Puis ouvrir http://localhost:8000. Pour avoir des données, charger l'exemple (voir l'étape 1) et se connecter avec le compte de démo.

### En développement (rechargement instantané)

Avec les conteneurs `db` et `api` démarrés :

```bash
cd web
npm install
npm run dev
```

Puis ouvrir http://localhost:5173. Chaque modification d'un fichier `.tsx` ou `.css` s'affiche aussitôt, sans recharger.

### Vérifier le code

```bash
cd web
npm run build
npm run lint
```

- `npm run build` vérifie les types avec TypeScript, puis compile.
- `npm run lint` lance oxlint, le linter installé par Vite.

## Ce qui a été vérifié

Dans le navigateur intégré, sur http://localhost:8000 :
- un mauvais mot de passe affiche « Email ou mot de passe incorrect » ;
- avec le compte de démo, la liste « Perso » s'affiche **comme dans la maquette** : 4 colonnes, pastilles d'urgence, « Aujourd'hui » en rouge, tags, icônes de note, tâche cochée barrée ;
- un clic sur « Travail » affiche ses 3 colonnes ;
- déplier « Études » montre « Cours » et « Lectures », et l'état est bien enregistré en base ;
- la liste vide « Cours » affiche « Cette liste n'a pas encore de groupe. » ;
- recharger la page garde la session et rouvre la dernière liste ;
- la déconnexion ramène à l'écran de connexion, et elle tient après rechargement ;
- l'inscription d'un nouveau compte mène à « Crée ta première liste pour commencer. ».

`npm run build` et `npm run lint` passent sans erreur ni avertissement, et le serveur n'a produit aucune erreur 500.

## En cas de problème

| Symptôme | Cause probable | Solution |
|---|---|---|
| Page blanche | Erreur JavaScript | Console du navigateur (Cmd+Option+J dans Chrome) |
| « Impossible de joindre le serveur. » | Conteneur `api` arrêté, ou `npm run dev` sans l'API sur 8080 | `docker compose ps`, puis `docker compose up -d` |
| Une modification du code n'apparaît pas sur :8000 | L'image `web` n'a pas été reconstruite | `docker compose up -d --build web`, ou travailler avec `npm run dev` |
| Connecté sur :5173 mais pas sur :8000 (ou l'inverse) | Ce sont deux adresses, donc deux cookies | Se connecter sur chacune |
| Toujours renvoyé à la connexion | Cookie bloqué par le navigateur | Vérifier dans les outils de développement → Application → Cookies |
| Une icône de liste est remplacée par une liste à puces | Nom d'icône absent de `listIcons.ts` | L'y ajouter (nom Lucide en kebab-case → composant) |
| Voir les appels à l'API | — | Outils de développement → onglet Réseau, filtrer sur `api` |

## Limites connues

- **Pas encore pensé pour le téléphone** : la barre latérale garde sa largeur. Les apps natives prendront le relais sur mobile, et on pourra adapter le web plus tard si besoin.
- La police **Inter** n'est utilisée que si elle est installée sur l'ordinateur ; sinon, c'est la police du système. On ne charge pas de police depuis Google, pour rester self-hosted et ne rien envoyer à l'extérieur. On pourra embarquer Inter dans l'image si tu le souhaites.
- Les données ne se mettent pas à jour toutes seules si on les change ailleurs (autre onglet, app native) : il faut recharger.

## Et après ?

L'**étape 3b** rendra la page interactive :
- cocher une tâche ; ajouter une tâche, un groupe ou une liste ;
- l'overlay d'édition d'une tâche ;
- le glisser-déposer des tâches.
