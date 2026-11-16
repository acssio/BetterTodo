# Étape 5-2 : Quick task

## Objectif

Créer une tâche **depuis l'accueil du téléphone**, sans ouvrir de liste : un bouton flottant « Quick task » ouvre un petit panneau où l'on tape le titre, choisit la liste, le groupe et l'échéance. Le panneau **retient le dernier endroit utilisé** (liste et groupe) pour la fois suivante. Maquette : écran 9 de la page « BetterTodo Mobile ».

Choix validés :
- liste et groupe dans deux **menus déroulants natifs** (le téléphone ouvre sa propre roue de sélection) ;
- échéance **« Aujourd'hui » proposée par défaut**, avec Demain, une date au choix ou Aucune ;
- après « Créer la tâche », le panneau **se ferme** et un message confirme « Tâche ajoutée dans « Travail › À faire » » ;
- la mémoire est dans le navigateur (`localStorage`), donc **par appareil** ;
- le bouton n'existe qu'en mode mobile, sur l'accueil.

## Fichiers

```
web/src/
  utils/quickTask.ts (+ tests)   → NOUVEAU : chooseList, chooseGroup, mémoire (lecture / écriture)
  utils/dates.ts (+ tests)       → tomorrowIso
  utils/mobileLayout.ts          → KeyboardListener (partagé avec l'overlay d'une tâche)
  api/ApiClient.ts               → createTask(groupId, title, dueDate?)
  components/
    QuickTaskSheet.tsx / .css    → NOUVEAU : le panneau
    MobileHome.tsx / .css        → bouton flottant (prop onQuickTask)
    Workspace.tsx                → quickTaskOpen, loadGroups, createQuickTask
    Workspace.css                → message de confirmation sur toute la largeur (mobile)
    TaskPopover.tsx              → utilise KeyboardListener (même comportement qu'avant)
```

**L'API n'a pas changé** : `POST /groups/{id}/tasks` acceptait déjà `due_date`. Seul `ApiClient.createTask` a reçu un paramètre facultatif.

## Comment ça marche

```
Accueil ──(bouton « Quick task »)──► QuickTaskSheet
                                        │ à l'ouverture : mémoire → chooseList → loadGroups → chooseGroup
                                        │ changer de liste → loadGroups (groupes de la nouvelle liste)
                                        └─(Entrée / « Créer la tâche »)─► Workspace.createQuickTask
                                              ├─ API : création avec l'échéance
                                              ├─ compteurs de l'accueil rechargés, message en bas
                                              └─ OK → mémoire écrite, panneau fermé
```

- `QuickTaskSheet` **ne parle pas à l'API** (règle du projet) : `Workspace` lui donne `onLoadGroups(listId)` et `onCreate(...)`. Pour avoir les groupes d'une liste, `loadGroups` passe par `GET /lists/{id}/board`, la seule route qui les donne.
- Si on change de liste pendant qu'un chargement est en cours, la réponse de l'ancienne liste est ignorée (`state.listId === listId` dans `loadGroups`).
- `createQuickTask` renvoie `true` ou `false` : en cas d'erreur (message affiché par `Workspace`), le panneau reste ouvert avec le titre, pour réessayer.

## Quelle liste et quel groupe proposer ?

Deux fonctions pures, testées dans `quickTask.test.ts` :

| Situation | Liste proposée | Groupe proposé |
|---|---|---|
| Première Quick task | la première liste | son premier groupe |
| Mémoire valide | la liste retenue | le groupe retenu |
| Liste retenue supprimée | la première liste | son premier groupe |
| Groupe retenu supprimé | la liste retenue | son premier groupe |
| Liste sans groupe | — | « Aucun groupe », bouton désactivé |

La mémoire est sous la clé `bettertodo.quickTask` : `{"listId": "…", "groupId": "…"}`. Une valeur abîmée est ignorée (`parseQuickTaskMemory`). Elle n'est écrite **qu'après une création réussie**. « Dernier endroit utilisé » s'affiche sous les menus quand la proposition vient de la mémoire.

## L'échéance

Quatre pastilles : **Aujourd'hui**, **Demain** (`tomorrowIso`, en date locale), **Date** et **Aucune**. « Date » contient un vrai `<input type="date">` invisible qui couvre la pastille : le toucher ouvre le calendrier du téléphone. Sur ordinateur, `showPicker()` l'ouvre au clic. Une fois choisie, la date s'affiche dans la pastille (« 25 nov. »).

## Le clavier

Le suivi de la hauteur du clavier, écrit en 5-1 dans `TaskPopover`, est maintenant une petite classe `KeyboardListener` (`utils/mobileLayout.ts`), sur le modèle de `DismissListener` : `start()` / `stop()`. L'overlay d'une tâche et la Quick task l'utilisent tous les deux.

## Lancer et tester

```bash
npm --prefix web test
```

```bash
npm --prefix web run dev
```

En mode appareil du navigateur (largeur 375 à 440 px), sur l'accueil :
1. le bouton « Quick task » est centré en bas, au-dessus de Paramètres ;
2. le toucher ouvre le panneau, le curseur est dans le titre ;
3. choisir une autre liste : les groupes changent ;
4. taper un titre, choisir une échéance, Entrée : le panneau se ferme, le message s'affiche, le compteur de la liste augmente ;
5. rouvrir : la même liste et le même groupe sont proposés, avec « Dernier endroit utilisé » ;
6. Échap, la croix ou toucher le fond ferment sans rien créer.

Testé dans le navigateur intégré, à 375 × 812, 412 × 1004 et 440 × 956, avec de vrais clics et de la vraie saisie. Les menus déroulants et le calendrier natifs ne se pilotent pas au clic dans ce navigateur : leur valeur a été choisie directement dans le champ, ce qui déclenche le même `change`. **Pas testé** : les roues de sélection et le clavier d'un vrai téléphone.

## Où regarder en cas de problème

| Symptôme | Où regarder |
|---|---|
| La mauvaise liste ou le mauvais groupe est proposé | `chooseList` / `chooseGroup` et leurs tests ; la valeur de `localStorage.getItem('bettertodo.quickTask')` |
| Les groupes ne changent pas avec la liste | `onListChanged` puis `loadGroups` dans `QuickTaskSheet.tsx` ; onglet Réseau : `GET /lists/{id}/board` |
| La tâche est créée sans échéance | onglet Réseau : le corps de `POST /groups/{id}/tasks` doit contenir `due_date` |
| Le panneau est caché par le clavier | `KeyboardListener` dans `utils/mobileLayout.ts` |
| Le calendrier ne s'ouvre pas | `.qt-date input` (doit couvrir la pastille) dans `QuickTaskSheet.css` |
| Rien ne se passe après « Créer la tâche » | message d'erreur en haut de l'écran ; `createQuickTask` dans `Workspace.tsx` |
