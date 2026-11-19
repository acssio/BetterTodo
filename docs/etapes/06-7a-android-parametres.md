# Étape 6-7a : app Android, mot de passe initial et Paramètres (compte)

## Objectif

- **Changer son mot de passe initial depuis l'app** (demande du 2026-11-17).
  - Quand l'admin a créé le compte ou changé son mot de passe (`must_change_password`), l'app affiche **avant l'accueil** l'écran « Choisis ton mot de passe », comme `ChangePasswordPage` du site.
  - Il remplace le bandeau qui renvoyait vers le site.
- **Écran Paramètres**, ouvert par l'**engrenage** de l'accueil, avec trois parties :
  - **Compte** : nom affiché, langue ;
  - **Mot de passe** ;
  - **Affichage par défaut** : colonnes / sections, pour les nouvelles listes.

Niveaux d'urgence et tags : 6-7b. Jetons d'accès et console admin : sur le site. Aucun changement côté serveur.

## Fichiers

```
app/src/main/java/io/github/acssio/bettertodo/
  api/Models.kt              ← User.defaultView
  api/ApiClient.kt           ← + updateMe (PATCH /me), changePassword (POST /me/password)
  ui/PasswordForm.kt         ← NOUVEAU : vérification avant envoi (vide, < 8 caractères, confirmation différente)
  ui/ChangePasswordScreen.kt ← NOUVEAU : « Choisis ton mot de passe » + PasswordField (avec l'œil)
  ui/SettingsScreen.kt       ← NOUVEAU : SettingsModel + écran Paramètres
  ui/App.kt                  ← Screen.MustChangePassword, changeInitialPassword, updateUser
  ui/HomeScreen.kt           ← engrenage, HomePage.Settings ; bandeau « mot de passe donné par l'admin » retiré
app/src/test/…               ← + PasswordFormTest (67 tests)
```

## La machine d'état de l'app

```
Loading / Login ──(connecté)──→ must_change_password ? ──oui──→ MustChangePassword ──(enregistré)──→ Home
                                                      └─non──→ Home
Home ──(engrenage)──→ page Settings ──(‹ ou retour)──→ Home
```

- **`AppViewModel.connected()`** choisit l'écran après une connexion ou au lancement.
- **Sur l'écran obligatoire**, « Se déconnecter » reste possible.
- **`POST /me/password` demande toujours l'actuel**, ici le mot de passe provisoire. Le serveur ferme les **autres** sessions du compte et garde celle du téléphone : on arrive donc directement à l'accueil.

## Paramètres

- **Les choix sont enregistrés tout de suite** (`PATCH /me`). Le nom l'est à ↵, et en quittant l'écran.
  - Le compte à jour remonte à `AppViewModel` (`onUserChanged` → `updateUser`) : le nom affiché change aussi ailleurs dans l'app (bouton de déconnexion).
  - En cas d'erreur, l'ancienne valeur revient, avec un bandeau.
- **La langue** (Français / English) ne sert encore qu'au nom du groupe créé avec chaque nouvelle liste (« Tâches » / « Tasks »). L'écran le dit.
- **Mot de passe** : actuel, nouveau, confirmation. Après le changement :
  - les champs sont vidés et un message s'affiche ;
  - les autres appareils devront se reconnecter.

## Bug trouvé en testant : « Suivant » s'arrêtait sur l'œil

**Ce qui se passait** : la touche « Suivant » du clavier ne passait pas au champ suivant. Elle donnait le focus au bouton « œil » du champ (afficher le mot de passe), et tout ce qu'on tapait ensuite restait dans le premier champ.

**La correction** : le bouton œil ne peut plus prendre le focus (`Modifier.focusProperties { canFocus = false }`). Il reste utilisable au toucher.

**À retenir** : un bouton dans `trailingIcon` peut voler le focus de « Suivant ».

## Lancer et tester

```bash
cd android && ./gradlew test assembleDebug
```

### Tests automatiques (67 tests)

- `PasswordFormTest` : champ vide, moins de 8 caractères, confirmation différente, formulaire valide ;
- ceux des étapes précédentes.

### Vérifié sur l'émulateur, avec de vrais appuis, et sur le serveur

Avec un compte créé par l'admin (`POST /admin/users`) :

- connexion → « Choisis ton mot de passe » au lieu de l'accueil ;
- confirmation différente → « Les deux nouveaux mots de passe ne sont pas identiques. » ;
- mauvais mot de passe provisoire → « Mot de passe actuel incorrect » ;
- bon → accueil ; l'ancien mot de passe est refusé (401) ; `must_change_password` vaut faux ;
- Paramètres :
  - nom « Alex » (aussi sur le bouton de déconnexion de l'accueil) ;
  - langue English → une nouvelle liste a le groupe « Tasks » ;
  - vue par défaut Sections → une nouvelle liste est en sections ;
- changer le mot de passe (en passant d'un champ à l'autre avec « Suivant ») :
  - une autre session ouverte avec `curl` est fermée (401) ;
  - le nouveau mot de passe marche ;
  - l'app reste connectée.

**Pas vu à l'écran** : le message « Mot de passe changé ». Le changement a bien eu lieu (vérifié sur le serveur), mais le message, sous les champs, n'a pas été capté par l'outil de lecture d'écran. À regarder sur ton téléphone.

## Où regarder en cas de problème

| Symptôme | Cause probable | Que faire |
|---|---|---|
| L'écran du mot de passe revient à chaque lancement | Changement refusé, `must_change_password` toujours vrai | Message sous le formulaire ; Logcat `tag:ApiClient` |
| « Suivant » ne passe pas au champ suivant | Un bouton focusable dans le champ | `focusProperties { canFocus = false }` sur ce bouton |
| L'ancien nom s'affiche encore | `onUserChanged` pas appelé | Voir `SettingsModel.updateMe` |
| Déconnecté sur un autre appareil | Normal après un changement de mot de passe | Se reconnecter avec le nouveau |
