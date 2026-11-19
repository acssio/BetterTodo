# Étape 6-6b : app Android, gérer une liste de l'intérieur

## Objectif

Dans l'écran d'une liste, un menu **« … »** en haut à droite (comme « Options de la liste » sur le site) :

| Entrée | Effet |
|---|---|
| **Modifier la liste** | Panneau du bas : nom, groupe de listes, couleur (8), icône (136, avec recherche en français), « Supprimer la liste » |
| **Afficher en sections / en colonnes** | Change la vue, enregistrée sur la liste |
| **Afficher / Masquer les tâches faites (N)** | Enregistré sur la liste |
| **Vider les tâches faites (N)** | Après confirmation ; seulement s'il y en a |
| **Nouveau groupe** | Boîte de nom ; en vue colonnes, on va sur sa page |
| **Renommer / Supprimer « groupe affiché »** | Vue colonnes seulement |

En **vue sections**, chaque titre de groupe a son « … » (Renommer, Supprimer le groupe). Aucun changement côté serveur.

## Fichiers

```
app/src/main/java/io/github/acssio/bettertodo/
  api/ListChanges.kt   ← NOUVEAU : corps de PATCH /lists/{id} (seuls les champs modifiés ; folder_id null = hors groupe)
  api/ApiClient.kt     ← + updateList, deleteList, clearDone, createGroup, renameGroup, deleteGroup
  ui/ListIcons.kt      ← + libellés français, couleurs (LIST_COLORS), search()
  ui/BoardModel.kt     ← + modifications de la liste, groupes, tâches faites ; ListDialog (machine d'état) ;
                          withoutDone / deleteGroupMessage / clearDoneMessage (fonctions pures)
  ui/ListMenus.kt      ← NOUVEAU : ListMenu, GroupMenu, ListDialogs, ConfirmDialog, ListEditSheet
  ui/ListScreen.kt     ← menu dans l'en-tête, « … » des groupes, groupe affiché en vue colonnes
  ui/HomeScreen.kt     ← BoardModel(onDeleted = retour à l'accueil), groupes de listes passés à ListScreen
  ui/theme/Theme.kt    ← fond des menus blanc (surfaceContainer)
app/src/test/…         ← ListIconsTest, BoardModelTest, TaskChangesTest complétés (63 tests)
```

## La machine d'état de l'écran

```
menu « … » ──(Modifier la liste)──→ panneau ListEditSheet (chaque champ enregistré tout de suite)
           ──(Nouveau groupe / Renommer)──→ boîte de nom (NameDialog, 6-6a) ──(OK)──→ fait
           ──(Supprimer le groupe / Vider les faites / Supprimer la liste)──→ confirmation ──(OK)──→ fait
```

`BoardModel.dialog` contient la boîte ouverte (`sealed class ListDialog`) ; `submitName` et `confirm` font ce qui correspond.

## Comment c'est enregistré

Comme les tâches : **l'écran d'abord**, puis l'API. En cas d'erreur, l'état d'avant est remis, puis la liste est rechargée, avec un bandeau. Deux exceptions :

- **Nouveau groupe** : il apparaît à la réponse du serveur, parce qu'il faut son id.
- **Supprimer la liste** : on revient à l'accueil seulement si le serveur a accepté.

| Action | Requête |
|---|---|
| Nom, couleur, icône, groupe de listes, vue, tâches faites affichées | `PATCH /lists/{id}` (`ListChanges`) |
| Vider les tâches faites | `DELETE /lists/{id}/done-tasks` (une seule transaction côté serveur) |
| Nouveau / renommer / supprimer un groupe | `POST /lists/{id}/groups`, `PATCH` / `DELETE /groups/{id}` |
| Supprimer la liste | `DELETE /lists/{id}` |

Les confirmations reprennent les textes du site :

- « Supprimer le groupe « En cours » et ses 2 tâches ? » ;
- « Supprimer définitivement la tâche faite de « Maison » ? ».

## La palette, toujours synchronisée avec le site

`ListIcons.kt` reprend les mots-clés français (`label`) et les 8 couleurs de `listIcons.ts`. `ListIconsTest` vérifie que les icônes, les libellés et les couleurs sont **identiques** au site. La recherche fonctionne comme sur le site : le nom anglais ou un mot-clé contient le texte tapé (« chien » trouve `dog`).

## Bug trouvé en testant : le nouveau groupe ne s'affichait pas

**Ce qui se passait** : en vue colonnes, après « Nouveau groupe », la page s'arrêtait sur l'avant-dernier groupe, et le menu proposait de renommer le mauvais groupe.

**La première piste était fausse.** J'ai d'abord pensé que le nombre de pages du pager était lu dans un lambda qui gardait l'ancienne liste de groupes. Je l'ai corrigé (`rememberUpdatedState`), c'était une bonne précaution, mais ça ne suffisait pas.

**La vraie cause** : le groupe affiché était suivi avec `pager.currentPage`. Pendant l'animation vers la nouvelle page, `currentPage` passe par chaque page intermédiaire. Chaque passage réécrivait le groupe affiché, ce qui relançait l'effet « aller à la page du groupe » et coupait l'animation.

**La correction** : suivre `pager.settledPage`, la page une fois l'animation terminée. À retenir pour tout ce qui réagit à la page d'un pager.

## Lancer et tester

```bash
cd android && ./gradlew test assembleDebug
```

### Tests automatiques (63 tests)

- `ListIconsTest` :
  - libellés et couleurs identiques au site ;
  - recherche : rien tapé, mot-clé français, nom anglais, majuscules, aucun résultat ;
- `BoardModelTest` : vider les faites, messages de confirmation (0, 1 et plusieurs tâches) ;
- `TaskChangesTest` : `ListChanges` (`folder_id: null`, couleur, icône, vue, tâches faites).

### Vérifié sur l'émulateur, avec de vrais appuis, et sur le serveur

- menu : les 7 entrées attendues en vue colonnes ;
- « Modifier la liste » :
  - nom « Maison » ;
  - groupe de listes « Perso » ;
  - couleur verte ;
  - recherche « chien » → une seule icône, choisie ;
  - l'en-tête suit ;
- « Afficher en sections » ; « Afficher les tâches faites (1) » → la tâche faite apparaît ;
- « Vider les tâches faites (1) » → confirmation « … la tâche faite de « Maison » ? » → supprimée ;
- vue sections :
  - « Nouveau groupe » « Idees » ;
  - « … › Renommer » → « Projets » ;
  - « … › Supprimer le groupe » « En cours » → « … et ses 2 tâches ? » → supprimé ;
- vue colonnes :
  - « Nouveau groupe » → sa page s'ouvre ;
  - « Renommer « Garage » » → « Atelier » ;
  - glisser vers la page précédente → le menu propose « Jardin » ;
- « Supprimer la liste » → confirmation → retour à l'accueil, liste supprimée sur le serveur.

## Où regarder en cas de problème

| Symptôme | Cause probable | Que faire |
|---|---|---|
| Le menu propose le mauvais groupe (vue colonnes) | Groupe affiché mal suivi | `ColumnsView` : `snapshotFlow { pager.settledPage }` |
| Une icône du site manque dans l'app | Liste pas mise à jour | `./gradlew test` le signale (`ListIconsTest`) ; ajouter l'icône et son libellé dans `ListIcons.kt` |
| La liste ne se supprime pas | Serveur injoignable | Bandeau ; on reste sur la liste |
| Couleurs Material par défaut (lavande) | Rôle de couleur pas défini | `Theme.kt` : `surfaceContainer*`, `secondaryContainer` |
