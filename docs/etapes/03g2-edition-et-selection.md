# Étape 3g-2 : titre modifié sur place et sélection multiple

## Objectif

- **Modifier le titre d'une tâche directement sur sa carte**, sans ouvrir l'overlay.
- **Sélectionner plusieurs tâches** avec Cmd+clic (Ctrl+clic sous Windows / Linux), pour les **supprimer** ou les **déplacer** en une fois.
- L'overlay ne s'ouvre plus au clic, mais avec un **petit bouton affiché au survol** de la tâche.

## Les gestes

| Geste | Effet |
|---|---|
| Clic sur une tâche | Modifie le titre sur place, curseur là où on a cliqué |
| Entrée / clic ailleurs | Enregistre le titre |
| Échap | Annule la modification |
| Cmd+clic / Ctrl+clic | Ajoute la tâche à la sélection ou l'en retire |
| Clic dans le vide, Échap, ✕ de la barre | Vide la sélection |
| Suppr / ⌫, bouton « Supprimer » de la barre | Supprime la sélection, après confirmation |
| Glisser une tâche sélectionnée | Déplace **toute** la sélection |
| Glisser une tâche non sélectionnée | Ne déplace qu'elle et vide la sélection |
| Bouton au survol (icône `PanelRightOpen`), ou Entrée sur une tâche | Ouvre l'overlay |
| Case à cocher | Ne change pas |

Ça marche dans les trois vues : colonnes, sections et listes intelligentes. Dans « Aujourd'hui » et « Planifié », on ne glisse pas (l'ordre vient des dates) : la sélection sert seulement à supprimer.

## Fichiers

```
web/src/
  components/TitleEditor.tsx/.css  → nouveau : le champ qui remplace le titre pendant la modification
  utils/caret.ts                   → nouveau : position du curseur à l'endroit d'un clic
  components/TaskCard.tsx/.css     → clic / Cmd+clic, état « editing », bouton « Ouvrir le détail »
  components/TaskRow.tsx/.css      → pareil pour la vue sections et les listes intelligentes
  components/actions.ts            → renameTask, toggleSelection, startEditing ; selectedTaskIds
  components/Workspace.tsx/.css    → état selectedTaskIds, clavier, barre de sélection, moveTasks
  components/Board.tsx, SectionsView.tsx, SmartView.tsx → transmettent les nouvelles props
  components/TaskDropZone.tsx      → plusieurs tâches glissées (drag.taskIds)
  utils/dragDrop.ts                → DragState.taskIds, computeDropIndex(…, dragIds[])
  utils/board.ts (+ tests)         → dropAnchor, multiMovePositions
  components/SettingsDialog.tsx    → seulement l'appel à computeDropIndex (tableau d'un élément)
```

Deux props ont été renommées pour ne pas confondre :
- `opened` : l'overlay de cette tâche est ouvert (avant : `selected`) ;
- `selected` : la tâche fait partie de la sélection.

## Modifier le titre sur place

`TaskCard` et `TaskRow` ont une petite machine d'état locale, `editing` : vrai ou faux.

```
          clic (sans Cmd/Ctrl)                Entrée, clic ailleurs → onRename
affichage ───────────────────────► editing ───────────────────────────────────► affichage
                                        └──── Échap, titre vide ou inchangé ────► affichage
```

- En entrant en édition, la carte appelle `actions.startEditing` : Workspace **vide la sélection et ferme l'overlay**. Sinon l'overlay, qui garde sa propre copie du titre, pourrait réécrire l'ancien titre en se fermant.
- **Le curseur va là où on a cliqué** : `caretOffsetAt` (`utils/caret.ts`) demande au navigateur quel caractère est sous la souris (`caretPositionFromPoint`, ou `caretRangeFromPoint` dans Safari). Si on a cliqué à côté du texte, le curseur va à la fin.
- `TitleEditor` est une `textarea` qui grandit avec le texte : dans une carte, un titre long passe sur plusieurs lignes. Entrée enregistre au lieu de passer à la ligne, et les retours à la ligne d'un collage sont remplacés par des espaces. Dans la vue sections, `wrap="off"` garde une seule ligne.
- Pendant la modification, la carte **n'est plus glissable** (`draggable={!editing}`) : sinon, sélectionner du texte à la souris lancerait un glisser.
- Le titre change à l'écran tout de suite, puis part à l'API (`Workspace.renameTask`, modification « optimiste » comme le reste).

## La sélection

L'état est dans `Workspace.selectedTaskIds`, au-dessus des deux moitiés de la vue scindée. On peut donc sélectionner des tâches dans les deux listes et les déposer ensemble.

La sélection est vidée :
- par un clic simple sur une tâche (qui passe en modification) ;
- par un clic dans le vide d'une liste (`onPaneMouseDown`) ;
- par Échap ou le ✕ de la barre ;
- en changeant de liste ou en fermant une moitié. Sans ça, on pourrait supprimer des tâches qu'on ne voit plus.

Le clavier est écouté sur `document` (`onDocumentKeyDown`). Une touche tapée dans un champ (`input`, `textarea`…) ou dans la fenêtre de confirmation est ignorée. Ainsi, ⌫ dans un titre efface une lettre, pas la sélection. Le drapeau `confirmingDelete` évite d'empiler deux fenêtres de confirmation si on appuie deux fois sur ⌫.

Les suppressions partent **en parallèle** (`Promise.all`) : leur ordre n'a pas d'importance.

## Déplacer plusieurs tâches

Choix de cette étape : **pas de nouvelle route d'API**. Les tâches sont déplacées **une par une**, avec la route existante `PATCH /tasks/{id}` (`position` et `group_id`). Les requêtes partent **l'une après l'autre** (pas en parallèle), car chaque position suppose que les précédentes sont déjà faites. Une route « déplacer en lot », en une seule transaction, reste possible plus tard.

Le calcul se fait en deux temps, avec deux fonctions pures testées dans `board.test.ts` :

1. **`dropAnchor`** : la tâche qui restera juste **après** les tâches déposées (`null` = fin du groupe). On raisonne avec une tâche voisine plutôt qu'avec un index, parce que les index changent à chaque déplacement. Si les tâches faites sont masquées, on se place juste après la dernière visible, comme le faisait déjà `visibleToFullIndex`.
2. **`multiMovePositions`** : rejoue les déplacements sur une copie de l'ordre du groupe pour trouver la position de chacune, **comptée sans elle** (c'est ce qu'attend l'API).

Exemple : groupe `[a, b, c, d]`, on dépose `d` puis `a` avant `c`.

| Étape | Groupe | Position envoyée |
|---|---|---|
| `d` avant `c` | `[a, b]` + `d` avant `c` → `[a, b, d, c]` | 2 |
| `a` avant `c` | `[b, d, c]` + `a` avant `c` → `[b, d, a, c]` | 2 |

Les tâches arrivent dans **l'ordre d'affichage** (liste de gauche puis de droite, groupe par groupe), pas dans l'ordre où on les a sélectionnées (`selectionInOrder`).

Après le dernier envoi, le board est rechargé pour récupérer les positions renumérotées par le serveur, comme avant.

## Lancer et tester

```bash
npm --prefix web test
```

Dans le navigateur :
1. cliquer au milieu d'un mot d'une tâche : le curseur y est ; taper, puis Entrée ; recommencer avec Échap ;
2. survoler une tâche : le bouton apparaît en haut à droite et ouvre l'overlay ; cliquer ensuite sur la carte ferme l'overlay et passe en modification ;
3. Cmd+clic sur trois tâches de groupes différents : la barre apparaît en bas ;
4. glisser l'une d'elles dans un autre groupe : les trois arrivent ensemble, dans l'ordre d'affichage ;
5. ⌫ : la confirmation « Supprimer les 3 tâches sélectionnées ? » s'affiche ;
6. même chose en vue sections, puis dans « Aujourd'hui » (suppression seulement).

> Comme aux étapes précédentes, la souris automatisée du navigateur de test ne déclenche pas le glisser-déposer natif. Le glisser de plusieurs tâches a donc été testé en simulant `dragstart`, `dragover` et `drop`. Tout le reste (clics, Cmd+clic, touches, fenêtre de confirmation) a été testé avec de vrais clics. Un essai du glisser à la main reste utile.

## En cas de problème

- **Le titre ne s'enregistre pas** : regarder la requête `PATCH /tasks/{id}` dans l'onglet Réseau. Un titre vide ou inchangé n'envoie rien, c'est voulu.
- **Le curseur va toujours à la fin** : `caretOffsetAt` renvoie `null` quand le point cliqué n'est pas dans le `<span>` du titre (`titleRef`).
- **Suppr ne fait rien** : vérifier que le focus n'est pas dans un champ (`document.activeElement`). Les touches tapées dans un champ sont ignorées exprès.
- **Les tâches déplacées arrivent dans le désordre** : comparer les `position` envoyées (onglet Réseau) avec le tableau de l'exemple ci-dessus. Les tests `déplacer plusieurs tâches` de `board.test.ts` couvrent ces cas.
