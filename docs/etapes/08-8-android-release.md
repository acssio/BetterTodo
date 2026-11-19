# Étape 8-8 : app Android, version release et icône

## Objectif

Installer l'app sur un vrai téléphone (Samsung) avec **son icône** et une **version release** :

- **plus légère et plus rapide** que la version debug (3,2 Mo au lieu de 15,9 Mo, code optimisé par R8) ;
- **signée avec ta propre clé**, pour que chaque nouvelle version s'installe par-dessus l'ancienne en gardant les données ;
- HTTPS obligatoire (`src/main/res/xml/network_security_config.xml`), comme prévu à l'étape 6-1.

## Ce qui a été fait

- **Icône** : la coche blanche sur carré `#1F1F1F` du logo du site (`web/public/favicon.svg`), en icône adaptative (`res/drawable/ic_launcher_background.xml` et `ic_launcher_foreground.xml`). Android la découpe selon la forme du téléphone. La coche sert aussi d'icône **monochrome** (icônes à thème de One UI / Android 13+). Les images du robot Android par défaut ont été retirées : avec `minSdk 26`, elles ne servaient jamais.
- **Signature** (`app/build.gradle.kts`, `signingConfigs`) : la clé et ses mots de passe sont lus dans **`android/keystore.properties`**, un fichier **hors de Git** (`android/.gitignore` : `keystore.properties`, `*.jks`, `*.keystore`). Modèle : `android/keystore.properties.example`.
  - Sans ce fichier, la version release n'est pas signée (Android refuse de l'installer), mais la version debug marche comme avant.
  - Autre emplacement possible : `./gradlew assembleRelease -PkeystoreProperties=/chemin/vers/fichier`.

## Créer ta clé (une seule fois)

> **À garder précieusement, avec ses mots de passe** (gestionnaire de mots de passe + une copie de sauvegarde du fichier `.jks`). Si tu la perds, aucune mise à jour ne pourra plus s'installer par-dessus : il faudra désinstaller l'app.

1. Dans le dossier `android/`, crée la clé. La commande te demande deux fois un mot de passe, puis ton nom ; le reste peut être laissé vide :

```bash
cd /Users/acssiociardo/GitHub/BetterTodo/android && keytool -genkeypair -keystore bettertodo-release.jks -alias bettertodo -keyalg RSA -keysize 2048 -validity 10000
```

2. Copie le modèle et remplace les deux mots de passe (le même deux fois si tu n'en as tapé qu'un pour la clé) :

```bash
cp /Users/acssiociardo/GitHub/BetterTodo/android/keystore.properties.example /Users/acssiociardo/GitHub/BetterTodo/android/keystore.properties
```

3. Vérifie que Git ignore bien les deux fichiers (deux lignes doivent s'afficher) :

```bash
git -C /Users/acssiociardo/GitHub/BetterTodo check-ignore android/keystore.properties android/bettertodo-release.jks
```

## Construire et installer

```bash
cd /Users/acssiociardo/GitHub/BetterTodo/android && ./gradlew assembleRelease
```

L'APK est dans `android/app/build/outputs/apk/release/app-release.apk`. Pour l'installer :

- **par câble** (Options de développement › Débogage USB, voir l'étape précédente) :

```bash
~/Library/Android/sdk/platform-tools/adb install -r /Users/acssiociardo/GitHub/BetterTodo/android/app/build/outputs/apk/release/app-release.apk
```

- **ou sans câble** : envoie-toi le fichier et ouvre-le sur le téléphone (autoriser « Installer des applis inconnues »).

**Première fois seulement** : si une version **debug** est déjà installée, Android refusera la release (« signatures différentes »). Désinstalle d'abord la version debug. Les listes du téléphone partent avec, mais celles qui étaient synchronisées reviennent à la connexion au serveur.

**Mises à jour** : reconstruire avec `assembleRelease` et réinstaller par-dessus, les données restent. Tu peux augmenter `versionCode` / `versionName` dans `app/build.gradle.kts` pour t'y retrouver (pas obligatoire pour une installation directe).

## Vérifié

- l'icône sur l'émulateur (coche blanche sur carré foncé) ;
- `assembleRelease` avec une **clé de test jetable** (créée hors du projet, pas la tienne) : `apksigner verify` montre la signature ;
- la release installée sur l'émulateur : premier lancement, mode sans serveur, liste et tâche avec heure, puis **connexion à un serveur, premier lien et synchronisation complète** (la liste du téléphone est arrivée sur le serveur avec son heure). Le code optimisé par R8 lit donc bien les réponses JSON de l'API.

Remarque : en release, Android a laissé passer le HTTP vers `localhost` (l'adresse du Mac via `adb reverse`) alors que la configuration réseau impose HTTPS. Les adresses de serveurs distants restent en HTTPS obligatoire.

## Où regarder en cas de problème

| Symptôme | Cause probable | Que faire |
|---|---|---|
| « App non installée » / « signatures différentes » | Une version signée avec une autre clé est installée (debug, autre Mac) | Désinstaller l'ancienne, puis installer |
| `assembleRelease` réussit mais l'APK ne s'installe pas | `keystore.properties` absent : APK non signé | Créer le fichier (voir plus haut) ; `apksigner verify` |
| « Keystore was tampered with, or password was incorrect » | Mot de passe faux dans `keystore.properties` | Le corriger |
| « Serveur injoignable » en release, mais pas en debug | Adresse en `http://` | Utiliser l'adresse `https://` du serveur |
| Plantage seulement en release | Code retiré par R8 | `app/build/outputs/mapping/release/`, Logcat `-b crash` |
