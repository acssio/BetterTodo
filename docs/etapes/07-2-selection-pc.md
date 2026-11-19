# Étape 7-2 : sélection sur ordinateur (Maj+clic, clic droit)

## Objectif

Demande de `ModifBetterTodo.md` : « sélection de tâches sur PC ». La sélection par Cmd/Ctrl+clic existait déjà (étape 3g-2), mais elle était peu visible et ne sélectionnait qu'une tâche à la fois. Cette étape ajoute, comme l'écran 2 de la maquette v3 :

- **Maj+clic** : sélectionne toutes les tâches entre la dernière tâche cliquée et celle-ci ;
- **clic droit** : un petit menu à côté de la souris (sélection, ouvrir le détail, supprimer) ;
- une astuce dans la barre de sélection : « Maj+clic : plage · clic droit : menu ».

Choix validés :
- Maj+clic **ajoute** la plage à la sélection, sans rien retirer ;
- le clic droit **ne change pas** la sélection ;
- « Supprimer » du menu agit sur **toute la sélection** si la tâche cliquée en fait partie, sinon sur **cette tâche seulement** ;
- rien ne change en mode mobile (appui long, comme avant) ;
- **ni l'API ni la base n'ont changé**.

## Fichiers

```
web/src/
  utils/selection.ts (+ tests)  → NOUVEAU : selectRange (fonction pure)
  components/
    Workspace.tsx               → selectionAnchor, selectRange, visibleOrder, taskMenu (état),
                                  openTaskMenu / closeTaskMenu, renderTaskMenu, deleteTasks(ids)
    actions.ts                  → TaskViewActions.selectRange / openTaskMenu, TaskViewProps.menuTaskId
    TaskCard.tsx / TaskRow.tsx  → Maj+clic, clic droit, classe menu-on (+ .css)
    Board / SectionsView / SmartView .tsx → passent les nouvelles props
    Menu.tsx / .css             → raccourci à droite (kbd) et position à l'écran (at)
```

## Comment ça marche

### Maj+clic

```
clic (Cmd+clic, ou entrée du menu)  ──► toggleSelection(tâche) : selectionAnchor = tâche
Maj+clic sur une tâche              ──► selectRange(tâche)
                                          ├─ visibleOrder(tâche) : ids dans l'ordre de l'écran
                                          └─ selectRange(ordre, ancrage, tâche, sélection)  (utils/selection.ts)
```

- `selectionAnchor` est un simple champ de `Workspace` (pas dans `state`) : il ne s'affiche pas, il ne sert qu'au prochain Maj+clic. Si la sélection est vide, l'ancrage ne compte plus.
- `visibleOrder` cherche la moitié (vue scindée) qui contient la tâche :
  - **liste** : les tâches du board, groupe par groupe, sans les tâches faites si elles sont masquées. C'est le même ordre en colonnes et en sections ;
  - **Aujourd'hui / Planifié** : les sections de `groupDueTasks`, comme à l'écran.
- `selectRange` : plage de l'ancrage à la tâche (dans un sens ou dans l'autre), ajoutée sans doublon. Ancrage absent ou dans une autre liste : seulement la tâche cliquée.

### Clic droit

```
clic droit sur une tâche ──► TaskCard / TaskRow.onContextMenu
                                ├─ mode mobile : rien (comme avant)
                                ├─ titre en cours de modification : menu du navigateur (copier / coller)
                                └─ sinon : preventDefault + onOpenMenu(tâche, x, y, rectangle)
                                              └─► Workspace.taskMenu = { task, x, y, anchor }
                                                     └─► renderTaskMenu : <Menu at={{x, y}} …>
```

- `Menu` avec `at` est en `position: fixed` à la position de la souris, et décalé pour ne pas sortir de la fenêtre (taille estimée : `MENU_WIDTH`, `MENU_ITEM_HEIGHT`).
- Il se ferme par un clic à côté (clic droit compris), par Échap, ou après le choix d'une entrée. Échap ferme **seulement le menu**, la sélection reste.
- La tâche du menu a la classe `menu-on` (bordure grise sur une carte, fond gris sur une ligne).
- Le raccourci affiché pour la sélection est « ⌘ clic » sur Mac, « Ctrl+clic » ailleurs (`SELECT_KEY`).
- `deleteSelection` passe maintenant par `deleteTasks(ids, message)` : la même confirmation, mais pour n'importe quelle liste d'ids. Après la suppression, seules les tâches supprimées sortent de la sélection.

## Lancer et tester

```bash
cd web
npm test        # dont utils/selection.test.ts
npx tsc -b
```

Dans le navigateur (http://localhost:8000) :

1. Cmd+clic sur une tâche, puis Maj+clic sur une tâche d'un **autre groupe** : tout ce qui est entre les deux est sélectionné (colonnes et sections).
2. Même chose dans Planifié : la plage traverse les sections de dates.
3. Clic droit sur une tâche sélectionnée : « Retirer de la sélection », « Supprimer les N tâches ».
4. Clic droit sur une tâche non sélectionnée : « Ajouter à la sélection », « Supprimer » (cette tâche seule, avec confirmation ; la sélection reste).
5. « Ouvrir le détail » ouvre l'overlay à côté de la tâche. Échap ferme le menu.
6. En mode mobile (fenêtre étroite) : l'appui long marche comme avant, pas de menu.

## Où regarder en cas de problème

- **Maj+clic ne sélectionne que la tâche cliquée** : pas d'ancrage (sélection vide, ou ancrage dans l'autre moitié de la vue scindée), ou la tâche n'est pas trouvée par `visibleOrder`. Tester `selectRange` dans `selection.test.ts`.
- **Le menu du navigateur s'affiche à la place du nôtre** : vérifier que `onContextMenu` est bien sur la tâche (`TaskCard` / `TaskRow`) et que la tâche n'est pas en train d'être renommée.
- **Le menu sort de l'écran** : ajuster `MENU_WIDTH` / `MENU_ITEM_HEIGHT` dans `Menu.tsx`.
- **Tester avec de vrais clics** : le menu et la confirmation ne se testent pas en simulant des événements (voir CLAUDE.md).
