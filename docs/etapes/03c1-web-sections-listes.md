# Étape 3c-1 : vue sections et gestion des listes

## Objectif

- **La vue sections** (écran 2 de la maquette) et la bascule **Colonnes / Sections**.
- **L'overlay « Modifier une liste »** (écran 7) : nom, groupe de listes, couleur, icône, supprimer.
- **Renommer et supprimer un groupe de listes.**

## Comment s'en servir

| Action | Comment |
|---|---|
| Changer de vue | « Colonnes » / « Sections » dans l'en-tête. Le choix est enregistré **sur la liste** |
| Modifier une liste | Crayon au survol de la liste dans la barre latérale, ou clic sur l'icône de l'en-tête |
| Chercher une icône | Champ de recherche de l'overlay, en français ou en anglais (« maison », « house ») |
| Renommer / supprimer un groupe de listes | « … » au survol de son nom dans la barre latérale. Ses listes ne sont pas supprimées : elles passent hors groupe |
| Supprimer une liste | Bouton « Supprimer » de l'overlay (avec ses groupes et ses tâches, après confirmation) |

## Fichiers

```
web/src/
  api/types.ts, api/ApiClient.ts   → + ListChanges, updateList, deleteList, deleteFolder
  utils/popover.ts                 → popoverPosition() + classe DismissListener (Échap / clic dehors)
  utils/dragDrop.ts                → types DragState / DropTarget + computeDropIndex()
  components/
    actions.ts                     → interfaces TaskViewActions et TaskViewProps, communes aux deux vues
    Board.tsx                      → vue colonnes, réécrite avec les composants partagés ci-dessous
    SectionsView.tsx/.css          → vue sections (nouveau)
    TaskRow.tsx/.css               → une ligne de la vue sections (nouveau)
    TaskDropZone.tsx/.css          → un groupe où l'on peut déposer une tâche (partagé)
    GroupHeader.tsx/.css           → nom + compteur + menu « … » d'un groupe (partagé)
    AddTask.tsx/.css               → « + Ajouter une tâche » (partagé)
    NewGroup.tsx                   → champ « nouveau groupe » ou message « pas encore de groupe » (partagé)
    Menu.tsx/.css                  → petit menu déroulant réutilisable
    ListEditPopover.tsx/.css       → overlay « Modifier une liste » (nouveau)
    listIcons.ts                   → icônes avec un libellé français, et les 8 couleurs de la maquette
    TaskCard.tsx                   → + composants DueDate et UrgencyPill, réutilisés par TaskRow
    TaskPopover.tsx                → utilise maintenant popoverPosition et DismissListener
    ListHeader.tsx                 → bascule de vue active, icône cliquable
    Sidebar.tsx/.css               → crayon sur les listes, menu « … » sur les groupes de listes
    Workspace.tsx                  → nouvelles actions + état du glisser-déposer
```

## Comment ça marche

### Deux vues, un seul comportement

Les vues colonnes (`Board`) et sections (`SectionsView`) reçoivent **exactement les mêmes props** (`TaskViewProps` dans `actions.ts`). Seul l'affichage change :

```
            Board (colonnes)                 SectionsView (sections)
groupe   →  TaskDropZone className="board-column"   TaskDropZone className="section"
en-tête  →  GroupHeader                             GroupHeader
tâche    →  TaskCard                                TaskRow
bas      →  AddTask                                 AddTask
fin      →  NewGroup                                NewGroup
```

`Workspace.renderView()` choisit la vue selon `board.list.default_view`. Comme la préférence est enregistrée sur la liste, elle est retrouvée après rechargement et sur les autres appareils.

C'est un bon exemple de **composition** : plutôt que d'écrire deux fois le menu « … », le glisser-déposer ou l'ajout de tâche, chaque vue assemble les mêmes petites pièces.

### Le glisser-déposer remonte dans Workspace

À la 3b, l'état du glisser-déposer était dans `Board`. Il est maintenant dans `Workspace` (`drag: DragState`) :

```
TaskCard / TaskRow   dragstart → actions.startTaskDrag(task)
TaskDropZone         dragover  → computeDropIndex(…) → actions.setDropTarget({ groupId, index })
TaskDropZone         drop      → actions.dropTask() → Workspace.moveTask(…)
```

Deux raisons :
1. les deux vues partagent le même code (`TaskDropZone`) ;
2. à la 3c-3, en vue scindée, on glissera une tâche **d'une liste à l'autre**. Il faut donc que l'état soit au-dessus des deux listes.

### Les overlays : position et fermeture partagées

Les deux overlays (tâche et liste) utilisent `utils/popover.ts` :
- `popoverPosition(anchor, largeur, hauteur)` : à droite de l'élément cliqué s'il y a la place, sinon à gauche, toujours dans la fenêtre ;
- `DismissListener` : une petite classe qui écoute Échap et les clics en dehors, avec `start()` dans `componentDidMount` et `stop()` dans `componentWillUnmount`. Les boutons qui ouvrent l'overlay de liste portent `data-list-edit`, pour qu'un clic dessus ne le referme pas aussitôt.

### Modifier une liste

`Workspace.updateList(id, changes)` suit le même schéma optimiste que les tâches :
1. la liste change dans la barre latérale **et** dans l'en-tête ;
2. `PATCH /lists/{id}` part vers l'API ;
3. si le groupe de listes change, la liste des listes est rechargée, parce que l'ordre de la barre latérale change.

La recherche d'icône regarde le nom Lucide (anglais) **et** un libellé français (`listIcons.ts`) : « maison », « courses », « études »…

Supprimer la liste affichée ouvre la première liste restante.

## Lancer et tester

```bash
docker compose up -d --build --wait
```

```bash
cd web
npm test
npm run build
npm run lint
```

### Ce qui a été vérifié dans le navigateur

Avec un compte de test :
- **vue sections** : rendu conforme à la maquette (colonnes tags / échéance / urgence alignées, tâche faite barrée) ;
- la bascule Colonnes ↔ Sections fonctionne, et la vue est retrouvée après rechargement ;
- glisser-déposer en vue sections, vers le haut dans un groupe et vers un autre groupe. La ligne bleue s'affiche, et l'ordre est retrouvé après rechargement ;
- un clic sur une ligne ouvre l'overlay de la tâche, et la ligne est surlignée ;
- **Modifier une liste** : couleur, icône (trouvée en tapant « maison »), nom et sortie du groupe de listes. Tout se voit tout de suite dans la barre latérale et l'en-tête, et tout est enregistré sur le serveur ;
- renommer un groupe de listes (texte sélectionné d'office), puis le supprimer : sa liste passe hors groupe ;
- supprimer une liste depuis son overlay, avec confirmation ;
- aucune erreur 500 côté serveur.

Deux petits défauts ont été corrigés pendant les tests :
- l'icône de note était poussée à droite de la ligne au lieu de suivre le titre ;
- le nom de la liste s'affichait en gris dans l'overlay.

## En cas de problème

| Symptôme | Cause probable | Solution |
|---|---|---|
| La vue revient à « Colonnes » | Le `PATCH /lists/{id}` a échoué (bandeau rouge) | Onglet Réseau du navigateur |
| L'overlay de liste se referme en cliquant sur le crayon | Le bouton n'a plus `data-list-edit` | Le remettre (c'est ce que `DismissListener` ignore) |
| Plus de ligne bleue en glissant | `drag` n'est plus transmis à la vue | Vérifier `renderView()` dans `Workspace` |
| Une icône de liste est remplacée par une liste à puces | Nom absent de `listIcons.ts` | L'y ajouter, avec son libellé français |
| Le menu « … » d'un groupe de listes n'apparaît pas | Il ne s'affiche qu'au survol, et pas en mode icônes | Élargir la barre latérale |

## Et après ?

**3c-2 : l'écran Paramètres**
- niveaux d'urgence et tags ;
- vue par défaut des nouvelles listes, ce qui demande d'ajouter `users.default_view` (migration 003) et `PATCH /me` à l'API.
