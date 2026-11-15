# Étape 3c-3 : la vue scindée

## Objectif

Afficher **deux listes côte à côte**, comme les éditeurs de VS Code (écrans 3 et 4 de la maquette), et **glisser une tâche d'une liste à l'autre**.

## Comment s'en servir

| Action | Comment |
|---|---|
| Ouvrir une liste à côté | **Glisser** la liste depuis la barre latérale sur « Ouvrir à droite », ou **Cmd+clic** (Ctrl+clic sous Windows / Linux) |
| Remplacer une moitié | Glisser une liste sur « Remplacer à gauche / à droite » |
| Choisir la moitié active | Cliquer n'importe où dedans (trait bleu sous son en-tête). Un clic simple dans la barre latérale ouvre la liste dans la moitié active |
| Fermer une moitié | Croix dans son en-tête |
| Déplacer une tâche d'une liste à l'autre | La glisser d'une moitié à l'autre |

Dans la barre latérale, la liste de la moitié active est surlignée, et celle de l'autre moitié a un trait bleu à gauche.

Les listes ouvertes sont retrouvées après rechargement. Si la fenêtre fait **moins de 1 100 px**, seule la moitié active est affichée : l'autre reste ouverte et revient quand la fenêtre s'élargit.

## Fichiers

```
web/src/
  utils/board.ts (+ tests)       → placeTask, boardWithGroup, boardWithTask
  components/Workspace.tsx       → réécrit autour des « moitiés » (panes)
  components/Workspace.css       → .panes, .pane, zones de dépôt d'une liste
  components/Sidebar.tsx/.css    → listes glissables, Cmd+clic, liste de l'autre moitié
  components/ListHeader.tsx      → croix « Fermer cette moitié »
  components/actions.ts, NewGroup.tsx → createGroup(listId, name) : on précise dans quelle liste
```

## Comment ça marche

### Les moitiés (`Pane`)

```ts
interface Pane {
    listId: string
    board: BoardData | null // null pendant le chargement
}

panes: Pane[]      // 1 liste, ou 2 en vue scindée (gauche, droite)
activePane: number // où s'ouvre une liste cliquée
```

Avant cette étape, `Workspace` avait **un** board. Il en a maintenant un par moitié. Deux idées rendent le changement simple :

1. **Les modifications de tâches sont appliquées à tous les boards** (`updateBoards`). Une tâche n'est que dans un board : les fonctions de `utils/board.ts` ne changent rien aux autres. `toggleTask`, `renameGroup` ou `deleteTask` n'ont donc pas besoin de savoir dans quelle moitié ils agissent.
2. **Chaque moitié est un « container »** pour les `@container main` de l'étape 3b. En vue scindée, l'en-tête de chaque moitié passe en icônes selon **sa** largeur, sans une ligne de code en plus.

### Glisser une tâche d'une liste à l'autre

L'état du glisser-déposer était déjà dans `Workspace` depuis la 3c-1, au-dessus des deux moitiés. Au dépôt, `moveTask` :
1. trouve le board **source** (qui contient la tâche) et le board **cible** (qui contient le groupe) ;
2. convertit l'index si la liste cible masque ses tâches faites (`visibleToFullIndex`, étape 3c-2b) ;
3. appelle `placeTask` sur **chaque** board : la tâche est retirée de la source et insérée dans la cible ;
4. envoie `PATCH /tasks/{id}` avec `group_id` et `position`. L'API acceptait déjà un groupe d'une autre liste depuis l'étape 2b ;
5. recharge les boards (positions renumérotées) et les compteurs des deux listes.

### Glisser une liste depuis la barre latérale

Les listes de la barre latérale sont `draggable`. Pendant qu'on en glisse une, `Workspace` affiche des **zones de dépôt** par-dessus les moitiés (`renderListDropZones`) :
- une liste ouverte : « Ouvrir ici » et « Ouvrir à droite » ;
- deux listes : « Remplacer à gauche » et « Remplacer à droite » ;
- fenêtre étroite : une seule zone, « Ouvrir ici », qui vise la moitié visible.

Les zones n'existent que pendant ce glisser-là : elles ne gênent pas le glisser des tâches.

Une liste déjà ouverte n'est jamais affichée deux fois : l'ouvrir active simplement sa moitié.

### Bug trouvé pendant les tests : encore `setState`

Au rechargement avec deux listes, seule la 2ᵉ s'affichait. Au démarrage, `componentDidMount` ouvre les deux listes l'une après l'autre :

```ts
await this.openList(gauche, 0) // range le board de gauche… avec setState
await this.openList(droite, 1) // relisait this.state.panes : le board de gauche n'y était pas encore !
```

Le 2ᵉ appel repartait d'un état où le board de gauche valait encore `null`, et l'écrasait. **La correction** : `openList` et `closePane` utilisent maintenant `setState(state => …)`. React passe alors l'état **le plus récent**, y compris les mises à jour pas encore affichées.

C'est la même famille de bug qu'à l'étape 3b. La règle du CLAUDE.md devient : **pour calculer un nouvel état à partir de l'ancien, toujours `setState(state => …)`**.

L'enregistrement des listes ouvertes est maintenant dans `componentDidUpdate` : dès que la liste des moitiés change, elle est retenue (`bettertodo.panes` dans le navigateur).

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

23 tests Vitest, dont 2 nouveaux pour `placeTask` entre deux boards.

### Ce qui a été vérifié dans le navigateur

Avec le compte de test, à 1 440 px puis à 900 px :
- Cmd+clic sur « Travail » l'ouvre à droite, et la moitié droite devient active (trait bleu). Le rendu correspond à l'écran 4 de la maquette ;
- glisser « Fruits » de « Perso maison » entre les deux tâches de « Travail » : la ligne bleue s'affiche, l'ordre est le bon côté serveur, et les compteurs passent à 4 et 3. Le glisser dans l'autre sens fonctionne aussi ;
- fermer une moitié (plus de croix quand il n'en reste qu'une), puis glisser une liste depuis la barre latérale sur « Ouvrir à droite » ;
- **après deux rechargements, les deux listes reviennent**. C'était le bug corrigé ci-dessus ;
- un clic sur une liste déjà ouverte active sa moitié ;
- à 900 px, seule la moitié active s'affiche, cliquer sur l'autre liste bascule vers elle, et la seule zone de dépôt est « Ouvrir ici » ;
- aucune erreur 500 côté serveur.

> Comme pour les étapes précédentes, les glisser-déposer ont été testés en simulant les événements `dragstart`, `dragover` et `drop` : un essai à la main reste utile.

## En cas de problème

| Symptôme | Cause probable | Solution |
|---|---|---|
| Une moitié reste vide après rechargement | Mise à jour calculée depuis `this.state` | Utiliser `setState(state => …)` (voir plus haut) |
| Pas de vue scindée alors que deux listes sont ouvertes | Fenêtre de moins de 1 100 px | Élargir la fenêtre ou réduire la barre latérale (`SPLIT_MIN_WINDOW_WIDTH`) |
| Le Cmd+clic ouvre la liste au même endroit | La liste est déjà ouverte dans l'autre moitié | Normal : elle est activée, pas dupliquée |
| La tâche revient à sa place après un glisser entre listes | Le `PATCH` a échoué (bandeau rouge) | Onglet Réseau : réponse de `PATCH /tasks/…` |
| Les listes ouvertes ne sont pas retenues | `localStorage` bloqué (navigation privée) | Normal : on rouvre la première liste |

## Et après ?

L'**étape 3d** : les listes intelligentes « **Aujourd'hui** » (échéance aujourd'hui ou en retard) et « **Planifié** » (toutes les tâches avec une échéance), en haut de la barre latérale.
