# Étape 3b : la page web (modifications)

## Objectif

Rendre la page interactive :
- cocher une tâche ;
- ajouter une tâche, un groupe, une liste ou un groupe de listes ;
- renommer et supprimer un groupe ;
- **l'overlay d'une tâche** : titre, échéance, groupe, urgence, tags, notes et suppression, enregistrés automatiquement ;
- **le glisser-déposer** des tâches, dans un groupe et d'un groupe à l'autre.

## Fichiers

```
web/src/
  api/types.ts              → + TaskChanges (champs modifiables d'une tâche)
  api/ApiClient.ts          → + createFolder, createList, createGroup, renameGroup, deleteGroup,
                               createTask, updateTask, deleteTask
  utils/board.ts            → fonctions qui modifient le board localement (sans effet de bord)
  utils/board.test.ts       → tests de ces fonctions
  utils/dates.test.ts       → tests des dates
  components/
    Workspace.tsx           → toutes les modifications : écran mis à jour, puis appel à l'API
    Board.tsx/.css          → ajout de tâche et de groupe, menu « … », glisser-déposer
    TaskCard.tsx/.css       → case à cocher, clic → overlay, carte glissable
    TaskPopover.tsx/.css    → l'overlay d'édition d'une tâche (nouveau)
    InlineInput.tsx/.css    → petit champ « sur place » réutilisable (nouveau)
    Sidebar.tsx/.css        → + nouvelle liste, nouveau groupe de listes, + au survol d'un groupe de listes
    ListHeader.tsx          → bouton « Nouveau groupe » actif, nombre de tâches calculé depuis le board
.claude/launch.json         → permet à Claude de lancer « npm run dev » dans son navigateur
```

> **Ajouté après les premiers retours :**
> - barre latérale **redimensionnable**, avec un mode **icônes seules** ;
> - en-tête qui passe en **icônes** quand la place manque ;
> - en fenêtre étroite, barre latérale **par-dessus** le contenu.
>
> Voir « Barre latérale et petits écrans » plus bas.

## Comment s'en servir

| Action | Comment |
|---|---|
| Cocher / décocher | Clic sur le rond de la carte |
| Ajouter des tâches | « + Ajouter une tâche » en bas d'une colonne. Entrée = ajoute et garde le champ ouvert pour la suivante. Échap = ferme |
| Modifier une tâche | Clic sur la carte → overlay. Échap, la croix ou un clic à côté pour fermer |
| Déplacer une tâche | La glisser : une ligne bleue montre où elle sera déposée |
| Nouveau groupe | Bouton « Nouveau groupe » en haut à droite |
| Renommer / supprimer un groupe | Bouton « … » de la colonne |
| Nouvelle liste | « + » à côté de « Listes » (hors groupe), ou « + » au survol d'un groupe de listes |
| Nouveau groupe de listes | Icône dossier à côté de « Listes » |
| Changer la largeur de la barre | Tirer son bord droit (un trait bleu apparaît au survol), ou ← / → au clavier |
| Barre en icônes seules | Bouton en haut de la barre, double-clic sur le bord, ou la rétrécir sous 150 px |

## Comment ça marche

### Mise à jour optimiste (`Workspace.tsx`)

Toutes les modifications suivent le même schéma :

```
1. l'écran change tout de suite          → this.updateBoard(board => replaceTask(...))
2. la requête part vers l'API            → await api.updateTask(...)
3a. réponse OK : la version du serveur remplace la version locale
3b. erreur : bandeau rouge + rechargement du board → l'écran revient à la vérité du serveur
```

L'application paraît instantanée, même si le serveur met 100 ms à répondre.

`updateBoard` reçoit une fonction de `utils/board.ts` (`replaceTask`, `moveTask`, `removeTask`…). Ces fonctions ne modifient jamais le board existant : elles en renvoient **un nouveau**. React ne redessine que s'il reçoit un nouvel objet, et c'est aussi ce qui les rend faciles à tester (`npm test`).

### Le piège de `setState` (bug trouvé pendant les tests)

```ts
this.setState({ selectedListId: listId })
console.log(this.state.selectedListId) // ← encore l'ANCIENNE valeur !
```

`setState` ne change pas `this.state` tout de suite : React regroupe les mises à jour et les applique ensuite (même dans une fonction `async`, depuis React 18). Après la création d'une liste, `reloadBoard()` lisait `this.state.selectedListId` et le trouvait encore vide. Résultat : la nouvelle liste était sélectionnée, mais son contenu ne s'affichait pas. **La correction** : passer l'identifiant en paramètre, `reloadBoard(listId)`.

À retenir : juste après un `setState`, on utilise les valeurs qu'on vient de calculer, pas `this.state`.

### L'overlay (`TaskPopover.tsx`)

- **Position** : à droite de la carte s'il y a la place, sinon à gauche, et toujours dans la fenêtre (`position()`).
- **Enregistrement automatique** :
  - échéance, groupe, urgence et tags partent **dès qu'on les change** ;
  - le titre part en quittant le champ (ou avec Entrée). Un titre vide est refusé, et l'ancien revient ;
  - les notes partent **0,8 s après la dernière frappe** : on ne fait pas une requête par lettre ;
  - si l'overlay se ferme pendant la frappe, `componentWillUnmount` envoie ce qui restait.
- **Fermeture** : Échap, la croix, ou un `mousedown` en dehors de l'overlay. Un clic sur sa propre carte ne le ferme pas.
- `key={task.id}` dans `Workspace` : ouvrir une autre tâche crée un **nouveau** composant. Sinon, il garderait le titre et les notes de la précédente dans son état.
- En bas, « Enregistrement… » s'affiche tant qu'une requête est en cours.

### Le glisser-déposer (`Board.tsx`)

On utilise l'API native du navigateur, sans bibliothèque :

```
TaskCard  draggable  → dragstart : on retient la tâche glissée (dragTaskId)
colonne   dragover   → calcule l'index sous la souris → dropTarget → ligne bleue
colonne   drop       → actions.moveTask(tâche, groupe, index)
TaskCard  dragend    → on efface tout
```

- **Calcul de l'index** (`dropIndex`) : on parcourt les cartes de la colonne, **sans la carte glissée**, et on s'arrête à la première dont le milieu est sous la souris. C'est le même index que celui qu'attend l'API (étape 2b).
- `event.preventDefault()` dans `dragover` : sans ça, le navigateur refuse le dépôt.
- `dragleave` se déclenche aussi en passant d'une carte à l'autre. On ne l'écoute donc que quand la souris **sort vraiment** de la colonne (`contains(relatedTarget)`).
- Après le dépôt, `Workspace.moveTask` met l'écran à jour, envoie `PATCH /tasks/{id}` avec `position` (et `group_id` si le groupe change), puis recharge le board pour récupérer les positions renumérotées par le serveur.

### `InlineInput`

Un seul petit composant pour tous les champs « sur place » (tâche, groupe, liste, renommage) :
- Entrée valide, Échap annule, et un clic ailleurs valide si le champ n'est pas vide ;
- `keepOpen` le garde ouvert après Entrée, pour enchaîner les ajouts ;
- en renommage, le texte est sélectionné d'office : on tape directement par-dessus.

### Barre latérale et petits écrans

**Trois modes** (`utils/sidebarLayout.ts`, testé dans `sidebarLayout.test.ts`) :

| Mode | Quand | Affichage |
|---|---|---|
| `expanded` | fenêtre ≥ 760 px et barre ≥ 150 px | normale, largeur choisie (150 à 420 px) |
| `compact` | barre rétrécie sous 150 px | icônes seules (60 px), noms en info-bulle |
| `narrow` | fenêtre < 760 px | icônes seules ; « Agrandir » ouvre la barre **par-dessus** le contenu, et choisir une liste (ou cliquer à côté) la referme |

- **La poignée** (`SidebarResizer.tsx`) écoute `mousemove` sur **tout le document** pendant qu'on tire. Sinon, une souris rapide sortirait de la poignée de 8 px et on la « perdrait ». Au relâchement, une largeur entre 60 et 150 px est **aimantée** sur 60 (mode icônes), pour ne pas rester dans un entre-deux illisible.
- **La largeur** est retenue dans le `localStorage`, donc par navigateur. Le double-clic ou le bouton « Agrandir » retrouvent la dernière largeur normale.
- **Le mode compact** est géré en CSS : la classe `.compact` cache les textes (`Sidebar.css`, en bas du fichier). Le composant ne fait qu'ajouter des info-bulles et remplacer les « + » par le bouton « Agrandir » : on n'ajoute pas une liste sans voir son nom.
- **L'en-tête** utilise des **container queries** (`@container main` dans `ListHeader.css`). Elles regardent la largeur de la **zone principale**, pas de la fenêtre :
  - sous 860 px, la recherche disparaît ;
  - sous 640 px, les boutons n'affichent plus que leur icône ;
  - le titre et « N tâches à faire » sont coupés avec « … » au lieu de passer à la ligne.

  Comme elles regardent la zone principale, élargir la barre latérale déclenche le même effet que rétrécir la fenêtre.

## Lancer

```bash
docker compose up -d --build --wait
```

> Après une nouvelle version de la page, **recharger l'onglet** (Cmd+R). Un onglet resté ouvert garde l'ancien code JavaScript.

Ou, pour développer avec rechargement instantané :

```bash
cd web
npm run dev
```

## Tester

```bash
cd web
npm test
npm run build
npm run lint
```

- `npm test` lance Vitest : 12 tests sur `moveTask` (vers le haut, vers le bas, autre groupe, fin), `replaceTask` et les dates (y compris un « aujourd'hui » à 23 h 30).
- `npm run build` vérifie aussi les types TypeScript.

### Ce qui a été vérifié dans le navigateur

Avec un compte de test, sur une liste partie de zéro :
- création d'un groupe de listes, d'une liste dedans (qui s'ouvre aussitôt), de deux groupes et de trois tâches à la suite ;
- cocher : la carte se barre, et les compteurs de l'en-tête et de la barre latérale diminuent ;
- overlay : urgence, échéance (« Aujourd'hui » en rouge), tag, note et titre se reflètent **en direct** sur la carte, et tout est retrouvé après rechargement ;
- changement de groupe depuis l'overlay ;
- glisser-déposer : vers le haut dans un groupe, vers un autre groupe entre deux cartes, puis à la fin d'un groupe. La ligne bleue s'affiche, et l'ordre est le bon après rechargement ;
- renommer un groupe, puis supprimer une tâche et un groupe, avec leur confirmation ;
- aucune erreur 500 côté serveur.

Pour la barre latérale et les petits écrans :
- à 660 px de large, la barre est en icônes, l'en-tête aussi, et le titre reste lisible ;
- « Agrandir » ouvre la barre par-dessus le contenu, et un **seul clic** sur une liste l'affiche et referme la barre ;
- à 1 280 px, tirer la poignée donne 360 px, et rétrécir sous 150 px aimante sur 60 px. Le double-clic revient à 360 px, et la largeur est retrouvée après rechargement.

> Le glisser-déposer a été testé en **simulant** les événements `dragstart`, `dragover` et `drop` en JavaScript : la souris automatisée du navigateur de test ne déclenche pas le glisser-déposer natif. Le code exécuté est le même, mais un essai à la main reste utile.

## En cas de problème

| Symptôme | Cause probable | Solution |
|---|---|---|
| Une modification revient en arrière + bandeau rouge | L'API a refusé (message dans le bandeau) | Lire le message, puis `docker compose logs api` |
| Le glisser-déposer ne fait rien | Le `dragover` n'appelle pas `preventDefault()`, ou ce n'est pas une carte de BetterTodo | Console du navigateur, vérifier `dragTaskId` |
| La carte revient à sa place après le dépôt | Le `PATCH` a échoué, et le rechargement a remis l'ordre du serveur | Onglet Réseau : regarder la réponse de `PATCH /tasks/…` |
| L'overlay garde l'ancien titre en changeant de tâche | `key={task.id}` enlevé dans `Workspace` | Le remettre |
| Les notes ne s'enregistrent pas | On a fermé l'onglet dans les 0,8 s | Normal : quitter le champ suffit à enregistrer |
| `this.state` « pas à jour » dans une méthode | Lu juste après `setState` | Utiliser la valeur calculée, ou `setState(state => …)` |
| Il faut cliquer deux fois sur une liste | Onglet ouvert avant la correction : il garde l'ancien JavaScript | Recharger l'onglet |
| La barre latérale a une largeur bizarre | Largeur mémorisée dans le navigateur | Double-clic sur son bord, ou supprimer `bettertodo.sidebarWidth` (outils de développement → Application → Local Storage) |

## Limites connues

- **Pas d'annulation** (Cmd+Z) après une suppression : la confirmation est la seule protection.
- Seules les **tâches** se glissent. Réordonner les groupes et les listes se fera plus tard (l'API le permet déjà avec `position`).
- Une nouvelle liste a la couleur et l'icône par défaut. On pourra les changer avec l'overlay « Modifier une liste » de l'étape 3c.
- Le glisser-déposer natif ne marche pas au doigt sur mobile.
- La poignée de la barre latérale se tire à la souris ou au clavier, pas encore au doigt.

## Et après ?

L'**étape 3c** terminera la maquette :
- la vue **sections** ;
- la **vue scindée**, en glissant une liste à droite ;
- l'écran **Paramètres** (niveaux d'urgence, tags, vue par défaut) ;
- l'overlay **« Modifier une liste »** (nom, groupe de listes, couleur, icône).
