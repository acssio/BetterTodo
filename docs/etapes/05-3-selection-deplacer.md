# Étape 5-3 : sélection par appui long et « Déplacer »

## Objectif

Sur téléphone, il n'y a ni Cmd+clic ni glisser-déposer. Cette étape donne l'équivalent au doigt :

- **appui long** sur une tâche (environ 0,5 s) → elle est sélectionnée, on passe en **mode sélection** ;
- en mode sélection, **toucher** une tâche l'ajoute ou la retire ;
- l'en-tête devient **« Annuler · N sélectionnées · Tout »** et une barre **Déplacer / Supprimer** apparaît en bas (écran 7 de la maquette) ;
- **Déplacer** ouvre un panneau du bas pour choisir la **liste et le groupe** : les tâches arrivent à la fin du groupe, dans leur ordre d'affichage.

Ça marche dans les listes (colonnes et sections) et dans « Aujourd'hui » / « Planifié ». Sur ordinateur, rien ne change (Cmd+clic et glisser-déposer).

Choix validés :
- les menus « Liste / Groupe » de la Quick task sont devenus un composant commun, `ListGroupPicker` ;
- destination proposée : **la liste et le groupe de la première tâche sélectionnée** ;
- rien de nouveau sur ordinateur.

## Fichiers

```
web/src/
  utils/longPress.ts (+ tests)    → NOUVEAU : machine d'état de l'appui long
  components/
    ListGroupPicker.tsx / .css    → NOUVEAU : menus Liste / Groupe (Quick task et Déplacer)
    MobileSelection.tsx / .css    → NOUVEAU : SelectionHeader (en haut) et SelectionActions (en bas)
    MoveTasksSheet.tsx            → NOUVEAU : panneau « Déplacer N tâches vers… »
    QuickTaskSheet.tsx / .css     → utilise ListGroupPicker (même comportement)
    TaskCard.tsx, TaskRow.tsx     → appui long, prop selecting
    Board.tsx, SectionsView.tsx, SmartView.tsx → transmettent selecting
    Workspace.tsx                 → selectAllVisible, moveSelection, moveOpen, en-tête et barre de sélection
    Workspace.css                 → .Workspace.mobile.selecting, pas de menu iOS à l'appui long
```

**Pas de changement d'API** : `PATCH /tasks/{id}` avec seulement `group_id` met déjà la tâche à la fin du nouveau groupe, même dans une autre liste.

## La machine d'état de l'appui long

`LongPress` (`utils/longPress.ts`), une instance par carte ou ligne :

```
idle ──down──► pressing ──(500 ms sans bouger)──► fired ──click──► idle
                  │                                  (ce clic est ignoré)
                  ├──move > 10 px──► idle   (le doigt fait défiler)
                  └──up avant 500 ms──► idle (simple toucher : le clic passe)
```

- Les événements viennent des **pointer events** de la carte : `pointerdown` → `down`, `pointermove` → `move`, `pointerup` et `pointercancel` → `up`. Le navigateur envoie `pointercancel` quand il commence à faire défiler.
- Quand l'appui long se déclenche : petite vibration (`navigator.vibrate`, Android seulement) et `onToggleSelection`.
- Le relâchement du doigt produit ensuite un `click` : `consumeClick()` répond `true` une fois, et le clic est ignoré (sinon l'overlay s'ouvrirait).
- `down()` repart toujours de zéro : si un navigateur n'envoie pas ce clic, le toucher suivant n'est pas bloqué.
- `onContextMenu` est annulé (menu d'Android), et le CSS met `-webkit-touch-callout: none` (menu « Copier / Partager » d'iOS).

## Le mode sélection

Il n'y a pas d'état à part : **mode sélection = mode mobile + au moins une tâche dans `selectedTaskIds`**. C'est le même tableau que la sélection au Cmd+clic de l'ordinateur, donc « Supprimer » réutilise `deleteSelection` (avec sa fenêtre de confirmation).

- `TaskCard` / `TaskRow` reçoivent `selecting` : toucher la tâche (ou sa case) sélectionne au lieu d'ouvrir l'overlay ou de cocher.
- `renderMobile` ajoute la classe `selecting` : le CSS cache `.ListHeader` et affiche `SelectionHeader` à sa place. Les onglets des groupes restent visibles pour sélectionner dans plusieurs groupes.
- **Tout** (`selectAllVisible`) prend les tâches affichées de la liste ouverte, sans les tâches faites masquées.
- **Annuler** vide la sélection. Le geste « retour » du téléphone revient à l'accueil et la vide aussi (`onPopState`).

## Déplacer

1. `firstSelectedPlace()` trouve la liste et le groupe de la première tâche sélectionnée (dans un board, ou dans une liste intelligente grâce à `DueTask.list`) : c'est la destination proposée.
2. `MoveTasksSheet` affiche `ListGroupPicker` et le bouton « Déplacer ».
3. `moveSelection(place)` :
   - garde l'**ordre d'affichage** (`orderedSelection` : boards d'abord, puis listes intelligentes) et ignore les tâches déjà dans ce groupe ;
   - met l'écran à jour tout de suite (`placeTask` à la fin du groupe, sur chaque board affiché) ;
   - envoie **un PATCH par tâche, l'un après l'autre** (même principe que le glisser de plusieurs tâches en 3g-2) ;
   - recharge les boards (positions renumérotées) et les listes (compteurs, liste d'origine dans « Aujourd'hui ») ;
   - affiche « 2 tâches déplacées vers « Travail › À faire » ».

## `ListGroupPicker`

Composant « non contrôlé » : il garde lui-même la liste, le groupe et les groupes chargés, et prévient le parent avec `onChange(place | null)` (`null` pendant un chargement ou sans groupe). Il reçoit `initialListId` et `preferredGroupId` (choisi s'il est dans la liste, sinon le premier groupe, avec `chooseGroup`). Une réponse qui arrive pour une liste qu'on a déjà quittée est ignorée (`currentListId`).

## Lancer et tester

```bash
npm --prefix web test
```

```bash
npm --prefix web run dev
```

En mode appareil du navigateur (375 à 440 px) :
1. appui long sur une tâche : elle devient bleutée, l'en-tête affiche « 1 sélectionnée », la barre du bas apparaît ;
2. toucher d'autres tâches les ajoute ou les retire ; « Tout » prend toute la liste ; « Annuler » sort du mode ;
3. « Déplacer » : la destination proposée est le groupe de la première tâche ; en choisir un autre, « Déplacer » ;
4. les tâches arrivent à la fin du groupe, dans le même ordre, et le message s'affiche ;
5. pareil depuis « Aujourd'hui », vers une autre liste : la liste d'origine affichée change ;
6. sur ordinateur, Cmd+clic et la barre noire du bas sont inchangés.

Testé dans le navigateur intégré, à 375 × 812, avec de vrais clics, sauf **l'appui long lui-même** : ce navigateur de test ne sait pas garder le bouton enfoncé. L'appui long a donc été envoyé depuis la page (`pointerdown`, 0,6 s, `pointerup`, `click`), comme le glisser-déposer en 3g-2. Les menus déroulants ont été réglés directement dans le champ (même événement `change`). La machine d'état est couverte par `longPress.test.ts`. **Un essai sur un vrai téléphone reste nécessaire**, surtout pour vérifier que l'appui long ne gêne pas le défilement.

## Où regarder en cas de problème

| Symptôme | Où regarder |
|---|---|
| L'appui long ne sélectionne pas | `onPointerDown` (seulement si `tapToOpen`) dans `TaskCard` / `TaskRow` ; `LONG_PRESS.delay` |
| Faire défiler sélectionne une tâche | `LONG_PRESS.tolerance` ; le navigateur doit envoyer `pointercancel` (→ `up`) |
| L'overlay s'ouvre après un appui long | `consumeClick()` au début de `onClick` |
| Le menu « Copier » d'iOS apparaît | `-webkit-touch-callout: none` dans `Workspace.css` |
| L'en-tête de la liste reste affiché | classe `selecting` sur `.Workspace` (`renderMobile`) |
| Mauvaise destination proposée | `firstSelectedPlace` / `orderedSelection` dans `Workspace.tsx` |
| Les tâches arrivent dans le désordre | `orderedSelection` ; requêtes envoyées l'une après l'autre (onglet Réseau) |

## Limites connues

- Pas de **réordonnancement** au doigt à l'intérieur d'un groupe : « Déplacer » met toujours à la fin. Pourra faire l'objet d'une étape à part.
- Pas de « Déplacer » sur ordinateur (le glisser-déposer suffit).
