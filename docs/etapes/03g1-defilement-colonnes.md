# Étape 3g-1 : signaler les colonnes cachées

## Objectif

Dans la vue colonnes, quand une liste a trop de groupes pour l'écran, l'utilisateur doit **voir qu'il peut encore défiler** vers la droite (ou la gauche). Sur macOS, les barres de défilement sont masquées par défaut : rien ne le montrait.

Deux idées, maquettées puis gardées **ensemble** :

1. **une barre de défilement fine mais toujours visible**, sous les colonnes ;
2. **un fondu sur le bord** où des colonnes sont cachées, avec un **bouton chevron** qui avance d'une colonne.

La barre sert au geste (on l'attrape), le fondu sert d'indice visuel.

## Fichiers

```
web/src/
  utils/scrollEdges.ts         → fonction pure : quels bords signaler ?
  utils/scrollEdges.test.ts    → ses tests (Vitest)
  components/Board.tsx         → état « edges », écoute du défilement et de la taille, chevrons
  components/Board.css         → .board-scroll (zone qui défile), barre de défilement, .board-edge (fondu)
```

## Organisation de `Board`

Avant, `.Board` était à la fois le cadre et la zone qui défile. Le fondu doit rester **fixe** pendant que les colonnes défilent : on a donc séparé les deux.

```
.Board            position: relative, ne défile pas
├── .board-scroll  overflow: auto, les colonnes (c'est elle qui défile)
├── .board-edge.left   position: absolute, fondu + bouton ‹
└── .board-edge.right  position: absolute, fondu + bouton ›
```

Le fondu a `pointer-events: none` : il ne bloque ni les clics sur les cartes en dessous ni le glisser-déposer. Seul le bouton redevient cliquable, et seulement quand il est affiché (`.visible`).

## La machine d'état

`BoardState.edges` vaut l'un de ces quatre états, calculé par `scrollEdges(scrollLeft, clientWidth, scrollWidth)` :

| État | Ce qui est caché | Affichage |
|---|---|---|
| `none` | rien | aucun fondu |
| `right` | des colonnes à droite | fondu + › à droite |
| `left` | des colonnes à gauche | fondu + ‹ à gauche |
| `both` | des deux côtés | les deux |

L'état est recalculé quand :
- on **défile** (`onScroll`) ;
- la zone **change de taille** : fenêtre, barre latérale, vue scindée (`ResizeObserver`) ;
- le board **change** : un groupe ajouté ou supprimé (`componentDidUpdate`).

`updateEdges` utilise `setState(state => state.edges === edges ? null : { edges })`. Renvoyer `null` annule la mise à jour : sans ça, `componentDidUpdate` relancerait `updateEdges` à chaque rendu, en boucle.

Le bouton fait défiler de **la largeur d'une colonne plus l'écart** (lu dans le CSS avec `getComputedStyle`), en douceur, sauf si le système demande moins d'animations (`prefers-reduced-motion`).

## La barre de défilement

- **Chrome et Safari** : les pseudo-éléments `::-webkit-scrollbar` dessinent une barre de 8 px, arrondie, qui reste affichée.
- **Firefox** ne connaît pas ces pseudo-éléments. Le bloc `@supports not selector(::-webkit-scrollbar)` lui donne `scrollbar-width: thin` et la couleur. Sur macOS, Firefox peut encore la masquer : le fondu reste là pour le signaler.

Attention : dans Chrome, dès qu'une règle `scrollbar-color` ou `scrollbar-width` s'applique, les `::-webkit-scrollbar` sont ignorés. C'est pour ça que ces deux propriétés sont **seulement** dans le bloc `@supports` pour Firefox.

## Lancer et tester

```bash
npm --prefix web test
```

Puis dans le navigateur, sur une liste avec plus de colonnes que l'écran :
1. le bord droit s'estompe et un bouton › apparaît ;
2. un clic avance d'une colonne, et le bouton ‹ apparaît à gauche ;
3. au bout, le › disparaît ;
4. ajouter un groupe, rétrécir la fenêtre ou ouvrir la vue scindée remet les bons bords.

## En cas de problème

- **Le fondu ne s'affiche jamais** : regarder dans la console `document.querySelector('.board-scroll')` et comparer `scrollLeft + clientWidth` à `scrollWidth`.
- **On ne peut plus cliquer sur une carte au bord** : vérifier que `.board-edge` a toujours `pointer-events: none`.
- **La barre n'a pas le bon style dans Chrome** : chercher un `scrollbar-color` ou un `scrollbar-width` appliqué à `.board-scroll` hors du bloc `@supports`.
