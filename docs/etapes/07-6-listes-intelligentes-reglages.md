# Étape 7-6 : créer et régler les listes intelligentes

## Objectif

Suite de 7-5 : créer, modifier, masquer et supprimer les listes intelligentes **depuis la page**, plus par l'API à la main. Maquette v3, écrans 5 (par tags), 6 (par urgence) et 7 (Paramètres).

**Ni la base ni l'API n'ont changé** (tout existait en 7-5).

Choix validés :
- tout se règle dans **Paramètres › Listes intelligentes** : une ligne par liste avec un interrupteur afficher / masquer (Aujourd'hui et Planifié compris), un crayon pour les listes perso, et « Nouvelle liste intelligente » ;
- une **fenêtre** (`SmartListDialog`) pour créer et modifier, avec « Supprimer » en modification ;
- la palette couleur + icônes est **commune** avec « Modifier une liste » (`ListLookPicker`, sorti de `ListEditPopover`) ;
- **simplifications** par rapport à la maquette : pas de compteur en direct pendant la création (il faudrait une route de prévisualisation), pas de nombre de tâches par niveau, pas de réordonnancement (l'API le permet : plus tard).

## Fichiers

```
web/src/
  components/ListLookPicker.tsx      → NOUVEAU : couleur + icône + recherche (sorti de ListEditPopover)
  components/ListEditPopover.tsx     → utilise ListLookPicker (rien ne change à l'écran)
  components/SmartListDialog.tsx/.css → NOUVEAU : fenêtre créer / modifier / supprimer
  components/SettingsDialog.tsx/.css → partie « Listes intelligentes », interrupteur .settings-switch
  components/Workspace.tsx           → onSmartListsChanged
  api/types.ts, api/ApiClient.ts     → SmartListChanges, createSmartList, updateSmartList, deleteSmartList
  utils/smartLists.ts (+ tests)      → canSaveSmartList
```

## Comment ça marche

```
Paramètres › Listes intelligentes
  ├─ interrupteur Aujourd'hui / Planifié → PATCH /me { show_today | show_scheduled } → onUserChanged
  ├─ interrupteur d'une liste perso      → PATCH /smart-lists/{id} { visible }
  ├─ crayon / « Nouvelle liste… »        → SmartListDialog (formulaire, ne parle pas à l'API)
  │                                          └─ onSave(changes) → SettingsDialog.saveSmartList → POST ou PATCH
  │                                          └─ onDelete()      → SettingsDialog.deleteSmartList → DELETE
  └─ à chaque changement : setSmartLists → props.onSmartListsChanged → Workspace
                                              ├─ barre latérale / accueil mobile à jour
                                              ├─ moitiés qui affichent une liste perso rechargées (filtre changé)
                                              └─ liste supprimée affichée → remplacée par la première vraie liste
```

- **Règle du projet** : seuls `App`, `Workspace` et `SettingsDialog` parlent à l'API. La fenêtre est un simple formulaire ; `SettingsDialog` enregistre, comme pour les tags et les jetons.
- **Fenêtre par-dessus les Paramètres** :
  - `data-keep-popovers` sur son fond : un clic dans la fenêtre ne ferme pas les Paramètres (voir `DismissListener`) ;
  - Échap : `preventDefault()` dans `onKeyDown`, donc seule la fenêtre se ferme ;
  - en mode mobile, elle prend tout l'écran comme les Paramètres (`.SettingsDialog`).
- **Ce qui est envoyé** : nom, couleur, icône, `kind`, `tag_match`, et **seulement** les ids du type choisi (`tag_ids` ou `urgency_ids`) : une liste vide serait refusée par l'API, et changer de type demande ses ids (règles de 7-5).
- **Bouton grisé** tant que `canSaveSmartList` est faux : nom vide, ou aucun tag / niveau pour le type choisi (mêmes règles que l'API, testées).
- Une erreur de l'API s'affiche **dans la fenêtre**, qui reste ouverte.
- Niveaux proposés du plus urgent au moins urgent (`rank` décroissant), comme les sections de la vue.

## Lancer et tester

```bash
cd web
npm test        # dont canSaveSmartList
npx tsc -b
```

Dans le navigateur (http://localhost:8000), avec de vrais clics :
1. Paramètres › Listes intelligentes : décocher Planifié → il disparaît de la barre latérale ; recocher.
2. « Nouvelle liste intelligente » : nom, clic sur l'icône (couleur, icône avec recherche), Par tags, cocher deux tags → « Créer la liste » : elle apparaît dans la barre latérale avec son compteur.
3. Crayon sur une liste par urgence affichée à l'écran : décocher un niveau → « Enregistrer » : la vue se recharge.
4. Crayon → « Supprimer » → confirmer : la liste disparaît ; si elle était affichée, la moitié montre la première liste.
5. Échap dans la fenêtre : seule la fenêtre se ferme.
6. Même chose en mode mobile (fenêtre étroite) : Paramètres et fenêtre en plein écran.

## Où regarder en cas de problème

- **400 en enregistrant** (message dans la fenêtre) : voir les règles de 7-5 (`missingFilter`, ids d'un autre compte, changement de type sans ses ids).
- **Les Paramètres se ferment en cliquant dans la fenêtre** : `data-keep-popovers` manque sur `.SmartListDialog-backdrop`.
- **La barre latérale ne suit pas** : `SettingsDialog.setSmartLists` doit appeler `onSmartListsChanged`, branché sur `Workspace.onSmartListsChanged`.
- **Entrée dans la recherche d'icônes valide la fenêtre** : le `onKeyDown` de la recherche (`ListLookPicker`) doit faire `preventDefault()`.
