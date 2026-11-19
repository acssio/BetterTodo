# Étape 6-6a : app Android, créer et gérer listes et groupes de listes (accueil)

## Objectif

Sur l'accueil, sans passer par le site :

- **« + »** en haut : **Nouvelle liste** ou **Nouveau groupe de listes** ;
- **« … »** sur un groupe de listes : **Nouvelle liste ici**, **Renommer**, **Supprimer le groupe** (ses listes sont gardées, hors groupe) ;
- **replier / déplier** un groupe de listes est **retenu sur le serveur**, comme sur le site (jusqu'ici, seulement sur le téléphone).

Une nouvelle liste **s'ouvre tout de suite**, avec son groupe par défaut (« Tâches »), que le serveur crée. Gérer l'intérieur d'une liste (modifier, groupes, vue, tâches faites) : 6-6b. Aucun changement côté serveur.

## Fichiers

```
app/src/main/java/io/github/acssio/bettertodo/
  api/ApiClient.kt   ← + createFolder, renameFolder, setFolderCollapsed, deleteFolder, createList
  ui/NameDialog.kt   ← NOUVEAU : boîte « un nom, puis OK » (créer / renommer), réutilisée en 6-6b
  ui/HomeScreen.kt   ← HomeDialog (machine d'état des boîtes), menus « + » et « … », MenuItem,
                        confirmation de suppression, repli enregistré, withoutFolder / deleteFolderMessage
app/src/test/…       ← + HomeTest (58 tests)
```

## La machine d'état des boîtes

```
aucune boîte ──(+ › Nouvelle liste, ou … › Nouvelle liste ici)──→ NewList(groupe de listes) ──(OK)──→ liste créée et ouverte
             ──(+ › Nouveau groupe de listes)───────────────────→ NewFolder ──(OK)──→ ajouté à la fin
             ──(… › Renommer)───────────────────────────────────→ RenameFolder ──(OK)──→ renommé
             ──(… › Supprimer le groupe)──→ confirmation ──(Supprimer)──→ supprimé, ses listes hors groupe
```

`HomeViewModel.nameDialog` contient la boîte ouverte (`sealed class HomeDialog`), et `submitName` fait ce qui correspond. Une seule boîte, `NameDialog`, sert à toutes :

- le champ a le focus tout de suite ;
- ↵ valide ;
- « Créer » / « Renommer » est désactivé tant que le nom est vide ;
- pour renommer, l'ancien nom est déjà sélectionné, pour être remplacé d'un coup.

## Comment c'est enregistré

| Action | Écran | Serveur |
|---|---|---|
| Créer une liste ou un groupe de listes | à la réponse (il faut l'id) | `POST /lists` (`folder_id` ou `null`) / `POST /folders` |
| Renommer | tout de suite | `PATCH /folders/{id}` `{name}` |
| Replier / déplier | tout de suite | `PATCH /folders/{id}` `{collapsed}` |
| Supprimer un groupe de listes | tout de suite, ses listes passent hors groupe (`withoutFolder`) | `DELETE /folders/{id}` ; la base fait pareil (`ON DELETE SET NULL`) |

En cas d'erreur : bandeau « Modification non enregistrée : … » et rechargement de l'accueil.

**Le repli vient maintenant toujours du serveur** : chaque rechargement reprend `collapsed`. Avant, il n'était lu qu'au premier chargement.

## Lancer et tester

```bash
cd android && ./gradlew test assembleDebug
```

### Tests automatiques (58 tests)

- `HomeTest` : supprimer un groupe de listes garde ses listes, texte de la confirmation ;
- ceux des étapes précédentes.

### Vérifié sur l'émulateur, avec de vrais appuis, et sur le serveur

- « + › Nouveau groupe de listes » : boîte avec le champ actif, « Créer » désactivé si vide ; « Vacances » créé ;
- « + › Nouvelle liste » « Valise » : créée hors groupe et ouverte, avec son groupe « Tâches » ;
- « … › Nouvelle liste ici » « Billets » : créée dans « Vacances » et ouverte ;
- « … › Renommer » : l'ancien nom est proposé ; « Voyages » enregistré ;
- replier : `collapsed` vrai sur le serveur ; après relance, toujours replié ;
- « … › Supprimer le groupe » : « Annuler » garde le groupe ; « Supprimer » le retire, et « Billets » passe hors groupe ;
- API arrêtée : créer un groupe de listes → bandeau, pas de groupe fantôme.

## Où regarder en cas de problème

| Symptôme | Cause probable | Que faire |
|---|---|---|
| Le clavier ne s'ouvre pas dans la boîte | `FocusRequester` lu hors de la boîte (fenêtre à part) | Il doit être créé dans le contenu de l'`AlertDialog` |
| Le repli revient après un rafraîchissement | L'enregistrement a échoué | Bandeau ; Logcat `tag:ApiClient` |
| Une nouvelle liste n'a pas de groupe | Le serveur n'a pas créé le groupe par défaut | Voir l'étape 3c-2b (`POST /lists`) |
