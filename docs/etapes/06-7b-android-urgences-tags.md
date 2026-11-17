# Étape 6-7b : app Android, niveaux d'urgence et tags (Paramètres)

## Objectif

Deux parties de plus dans l'écran Paramètres, comme `SettingsDialog` du site :

- **Niveaux d'urgence**, du moins au plus urgent :
  - toucher le **point de couleur** pour la changer (8 couleurs, celles des listes) ;
  - toucher le **nom** pour le renommer ;
  - **↑ ↓** pour réordonner (plutôt que glisser : la liste est courte, comme validé) ;
  - **corbeille** pour supprimer, après la confirmation du site ;
  - « Ajouter un niveau ».
- **Tags** : toucher pour renommer, le nombre de tâches qui portent chaque tag, corbeille pour supprimer, « Nouveau tag ».

Avec la 6-7a, l'app Android a maintenant tout ce que le site mobile propose, sauf les **jetons d'accès** et la **console admin**, qui restent sur le site (validé). Aucun changement côté serveur.

## Fichiers

```
app/src/main/java/io/github/acssio/bettertodo/
  api/Models.kt          ← + Tag (id, name, task_count)
  api/ApiClient.kt       ← + createUrgencyLevel, updateUrgencyLevel (nom / couleur / rang), deleteUrgencyLevel,
                            tags, createTag, renameTag, deleteTag
  ui/SettingsScreen.kt   ← SettingsModel : niveaux et tags, SettingsDialog (machine d'état des boîtes) ;
                            nextLevelColor, reorderLevels, deleteLevelMessage, deleteTagMessage (fonctions pures)
  ui/SettingsLists.kt    ← NOUVEAU : UrgencyLevelsSection, TagsSection, SettingsDialogs, boîte « Couleur »
  ui/NameDialog.kt       ← + capitalize (pas de majuscule automatique pour les tags)
app/src/test/…           ← + SettingsTest (70 tests)
```

## Comment c'est enregistré

Comme partout dans l'app :

- **les modifications** changent l'écran d'abord, puis passent par l'API. En cas d'erreur, la liste d'avant revient, avec un bandeau ;
- **les créations** apparaissent à la réponse du serveur, parce qu'il faut leur id.

| Action | Requête | Détail |
|---|---|---|
| Ajouter un niveau | `POST /urgency-levels` | À la fin, avec la **première couleur de la palette pas encore prise** (`nextLevelColor`, comme le site) |
| Renommer / couleur | `PATCH /urgency-levels/{id}` | Seul le champ changé est envoyé |
| ↑ / ↓ | `PATCH /urgency-levels/{id}` `{rank}` | L'écran renumérote les rangs 0, 1, 2… comme le serveur (`reorderLevels`) |
| Supprimer un niveau | `DELETE /urgency-levels/{id}` | « Les tâches de ce niveau passeront sans urgence. » |
| Nouveau tag | `POST /tags` | Rangés par ordre alphabétique |
| Renommer un tag | `PATCH /tags/{id}` | Le nouveau nom apparaît sur toutes ses tâches |
| Supprimer un tag | `DELETE /tags/{id}` | « Il sera enlevé de N tâches. » |

Les boîtes passent par `SettingsModel.dialog` (`sealed class SettingsDialog`), comme `HomeDialog` (6-6a) et `ListDialog` (6-6b), et réutilisent `NameDialog` et `ConfirmDialog`.

## Petit souci trouvé en testant

**Ce qui se passait** : les tags créés ou renommés depuis l'app prenaient une majuscule (« Domicile »). La boîte de nom demandait au clavier une majuscule en début de phrase. C'est bien pour une liste ou un groupe, mais pas pour un tag : le site les écrit en minuscules.

**La correction** : `NameDialog(capitalize = false)` pour les tags.

## Lancer et tester

```bash
cd android && ./gradlew test assembleDebug
```

### Tests automatiques (70 tests)

- `SettingsTest` :
  - couleur d'un nouveau niveau : première libre, majuscules ignorées, toutes prises → la dernière ;
  - monter / descendre avec les rangs renumérotés ;
  - textes des confirmations (0, 1 et plusieurs tâches) ;
- ceux des étapes précédentes.

### Vérifié sur l'émulateur, avec de vrais appuis, et sur le serveur

- 3 niveaux (Basse, Moyenne, Haute) et 4 tags avec « 1 tâche » ; flèches grisées aux extrémités ;
- ↑ sur « Haute » → Basse, Haute, Moyenne (rangs 0, 1, 2) ;
- renommer « Moyenne » → « Normale » ; couleur de « Basse » → bleu ;
- ajouter « Critique » → couleur verte (la première libre) ; supprimer avec la confirmation du site ;
- renommer le tag « maison » → la tâche « Appeler le plombier » porte le nouveau nom ;
- supprimer « urgent » → « Il sera enlevé de 1 tâche. » ;
- nouveau tag « voyage » → reste en minuscules ;
- API arrêtée : ↓ sur « Basse » → l'ordre change, puis revient, avec un bandeau.

## Où regarder en cas de problème

| Symptôme | Cause probable | Que faire |
|---|---|---|
| L'ordre des niveaux revient en arrière | Le serveur a refusé (bandeau) | Logcat `tag:ApiClient` |
| La pastille d'urgence d'une tâche n'a pas la nouvelle couleur | Liste ouverte avant la modification | Tirer vers le bas (les niveaux sont rechargés avec la liste) |
| Majuscule ajoutée au nom d'un tag | `capitalize` oublié | `NameDialog(capitalize = false)` |
