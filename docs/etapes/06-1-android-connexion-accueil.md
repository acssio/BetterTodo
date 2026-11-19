# Étape 6-1 : app Android, connexion et accueil

## Objectif

Poser les fondations de l'**app Android native** : se connecter à son serveur BetterTodo et afficher l'accueil (groupes de listes et listes, avec leur couleur et leur icône Lucide). **Lecture seule** pour l'instant : ouvrir une liste viendra à l'étape suivante.

Cette étape installe tout ce dont les suivantes auront besoin : Compose, les appels à l'API, l'enregistrement du jeton, la machine d'état des écrans.

## Les choix (validés le 2026-11-17)

| Sujet | Choix | Pourquoi |
|---|---|---|
| Langage / interface | **Kotlin + Jetpack Compose** (Material 3) | Le natif Android recommandé par Google ; Compose ressemble à React (l'écran se redessine quand l'état change) |
| Identifiant de l'app | `io.github.acssio.bettertodo` | Définitif (Play Store) ; `com.example` est refusé |
| Android minimum | 8.0 (`minSdk 26`) | `java.time` disponible pour les échéances ; quasi tous les téléphones |
| Réseau | **OkHttp + kotlinx.serialization** dans une classe `ApiClient` | Même principe que `ApiClient.ts` du site : une méthode par route, pas de « magie » d'annotations |
| Navigation | **Machine d'état maison** (`sealed class Screen`) | Comme `mobileScreen` du site ; Navigation Compose plus tard si les écrans se multiplient |
| Jeton | **DataStore** (stockage privé de l'app) | Remplace SharedPreferences ; `allowBackup="false"` pour qu'il ne parte pas dans la sauvegarde Google |
| Icônes | **icons-lucide-android** (`com.composables`) | Les icônes Lucide en drawables Android : même style que le site |

## Structure d'un projet Android (pour s'y retrouver)

```
android/
  settings.gradle.kts        ← nom du projet, modules (ici un seul : app), dépôts Maven
  build.gradle.kts           ← plugins communs (déclarés, pas appliqués)
  gradle/libs.versions.toml  ← TOUTES les versions des dépendances, à un seul endroit
  gradle/wrapper/            ← le « wrapper » : ./gradlew télécharge la bonne version de Gradle
  gradlew                    ← lance Gradle (compiler, tester, installer)
  local.properties           ← chemin du SDK sur ce Mac (pas commité)
  app/
    build.gradle.kts         ← le module : identifiant, versions Android, dépendances
    src/main/AndroidManifest.xml  ← ce que l'app déclare à Android : permissions, écran de lancement…
    src/main/java/…          ← le code Kotlin (le dossier s'appelle java par tradition)
    src/main/res/            ← ressources : textes, icône de l'app, XML de configuration
    src/debug/res/           ← ressources qui remplacent celles de main dans la version debug seulement
    src/test/java/…          ← tests JUnit (tournent sur le Mac, sans téléphone)
```

**Gradle** est l'outil de build d'Android (l'équivalent de Vite + npm pour le site). **AGP** (Android Gradle Plugin) lui apprend à fabriquer une app Android : un fichier `.apk` qu'on installe sur le téléphone.

## Fichiers du code

```
app/src/main/java/io/github/acssio/bettertodo/
  MainActivity.kt          ← point d'entrée : affiche App() dans le thème
  api/Models.kt            ← User, Session, Folder, TodoList… (@Serializable, comme types.ts)
  api/ApiClient.kt         ← une méthode par route, ajoute "Authorization: Bearer <jeton>"
  api/ServerUrl.kt         ← normalise l'adresse tapée ("todo.exemple.fr" → "https://todo.exemple.fr")
  data/SessionStore.kt     ← DataStore : adresse du serveur + jeton
  ui/App.kt                ← Screen (machine d'état) + AppViewModel + composable App
  ui/LoginScreen.kt        ← formulaire de connexion
  ui/HomeScreen.kt         ← HomeViewModel + accueil
  ui/ListIcons.kt          ← nom d'icône en base → drawable Lucide, couleur "#RRGGBB" → Color
  ui/theme/Theme.kt        ← couleurs du site (index.css) dans un thème Material 3
```

## La machine d'état des écrans

```
  Loading ──(jeton valide)──────────→ Home
     │ ──(pas de jeton / 401)───────→ Login ──(connexion)──→ Home
     └──(serveur injoignable)───────→ Unreachable ──(Réessayer)──→ Loading
  Home ──(déconnexion / 401)────────→ Login (adresse pré-remplie)
```

`AppViewModel.screen` contient l'écran courant ; `App()` affiche le bon composable avec un `when`. Kotlin vérifie que tous les cas du `sealed class` sont traités.

- **Serveur injoignable au lancement** : on ne déconnecte pas (le jeton est peut-être encore bon, c'est peut-être juste le réseau). L'écran propose « Réessayer » ou « Se déconnecter ».
- **401** (jeton expiré ou révoqué, par exemple après un changement de mot de passe sur le site) : jeton oublié, retour à la connexion avec « Session expirée ».

## ViewModel et état

Un **ViewModel** est une classe qui garde l'état d'un écran et **survit à la rotation** du téléphone (Android recrée l'écran quand on le tourne). Comme sur le site, seuls deux endroits parlent à l'API :

- `AppViewModel` (≈ `App.tsx`) : lancement, connexion, déconnexion ;
- `HomeViewModel` (≈ `Workspace.tsx`) : groupes de listes et listes. Il est créé avec le jeton comme clé (`viewModel(key = sessionKey)`) : un nouveau à chaque connexion.

L'état est fait de `mutableStateOf(...)` : quand une valeur change, Compose redessine ce qui l'utilise (comme `setState`). Les champs du formulaire sont en `rememberSaveable` dans `LoginScreen` : ils survivent aussi à la rotation.

Les méthodes de `ApiClient` sont `suspend` : elles attendent la réponse **sans bloquer l'écran**. On les appelle depuis `viewModelScope.launch { … }` ; le réseau tourne sur un autre fil (`Dispatchers.IO`).

### Bug rencontré : `async` sans `coroutineScope`

L'accueil charge les groupes de listes et les listes **en même temps** (deux `async`). Première version : un 401 faisait **planter l'app** au lieu d'afficher la connexion. Raison : l'erreur d'un `async` remonte directement à la coroutine parente (`launch`), sans passer par le `try`. Correction : envelopper les deux `async` dans `coroutineScope { … }`, qui renvoie l'erreur à l'appelant, donc au `try`. À retenir pour les étapes suivantes.

## HTTPS et serveur local

- En **release**, HTTPS est obligatoire (`src/main/res/xml/network_security_config.xml`).
- En **debug**, ce fichier est remplacé par celui de `src/debug/res/xml/`, qui autorise le HTTP vers `10.0.2.2` et `localhost` seulement.

## Lancer et tester

### Depuis Android Studio

Ouvrir le dossier `android/`, choisir un émulateur, bouton ▶. Les fonctions `@Composable` peuvent aussi avoir un aperçu `@Preview` (pas encore utilisé).

### En ligne de commande

Gradle a besoin du JDK fourni avec Android Studio :

```bash
export JAVA_HOME="/Applications/Android Studio.app/Contents/jbr/Contents/Home"
```

```bash
cd android && ./gradlew test assembleDebug
```

```bash
~/Library/Android/sdk/platform-tools/adb install -r app/build/outputs/apk/debug/app-debug.apk
```

### Se connecter au Docker local

Lancer le serveur (`docker compose up -d --wait` à la racine), puis dans l'app, au choix :

- adresse **`http://10.0.2.2:8000`** : depuis l'émulateur, `10.0.2.2` est le Mac ;
- ou **`http://localhost:8000`** après `adb reverse tcp:8000 tcp:8000` (marche aussi avec un vrai téléphone branché en USB).

Pour ton serveur distant : son adresse HTTPS, par exemple `https://todo.exemple.fr` (le `https://` est ajouté s'il manque).

### Tests automatiques (9 tests)

`./gradlew test` :

- `ServerUrlTest` : normalisation de l'adresse ;
- `ModelsTest` : lecture du JSON de l'API ;
- `ListIconsTest` : `ListIcons.kt` propose **exactement** les icônes de `web/src/components/listIcons.ts` (comme `mcp/palette_test.go`), icône inconnue → `list`, couleurs.

### Ce qui a été vérifié sur l'émulateur (Pixel 10, Android 17)

Avec de vrais appuis (`adb shell input`) sur une pile Docker de test :

- champs vides → « Remplis les trois champs » ;
- serveur sans compte → « Aucun compte sur ce serveur : crée d'abord le compte administrateur depuis le site » ;
- mauvais mot de passe → « Email ou mot de passe incorrect » ;
- connexion → accueil : listes hors groupe, groupes « Maison » / « Travail », icônes et couleurs, compteurs, nom trop long coupé avec « … » ;
- toucher un groupe de listes → replié / déplié ;
- relancer l'app → toujours connecté ;
- mot de passe changé ailleurs (sessions fermées), puis tirer vers le bas → « Session expirée » ;
- serveur arrêté : tirer vers le bas → bandeau d'erreur, les listes restent ; relancer → écran « Serveur injoignable », puis « Réessayer » une fois le serveur relancé → accueil ;
- compte créé par l'admin → bandeau « change ton mot de passe depuis le site » ; compte sans liste → « Aucune liste » ;
- bouton de déconnexion → connexion, adresse pré-remplie.

**Pas testé** : la connexion à ton serveur distant en HTTPS, et un vrai téléphone.

## Où regarder en cas de problème

| Symptôme | Cause probable | Que faire |
|---|---|---|
| « Serveur injoignable » | Mauvaise adresse, serveur arrêté, ou HTTP vers une adresse autre que `10.0.2.2` / `localhost` | **Logcat** (Android Studio, filtre `tag:ApiClient`) : la vraie exception y est écrite |
| `10.0.2.2` ne répond pas depuis l'émulateur | Réseau de l'émulateur | Utiliser `adb reverse tcp:8000 tcp:8000` et `http://localhost:8000` |
| L'app se ferme toute seule | Exception non attrapée | Logcat, niveau Error, ou `adb logcat -b crash` |
| `Unresolved reference 'lucide_ic_…'` | Les icônes sont dans le `R` de la bibliothèque | `import com.composables.icons.lucide.R` (pas le `R` de l'app) |
| Une icône de liste s'affiche comme « list » | Nom absent de `ListIcons.kt` | L'ajouter (et `./gradlew test` le signale si le site en a une de plus) |
| `./gradlew` : « Unable to locate a Java Runtime » | `JAVA_HOME` pas défini | `export JAVA_HOME=…` (voir plus haut) |
| L'écran de connexion revient à chaque lancement | Jeton refusé (401) | Normal si la session a été fermée (mot de passe changé, déconnexion ailleurs) |

## Plus tard

- 6-2 : ouvrir une liste (groupes et tâches), cocher une tâche.
- Replier un groupe de listes : enregistrer sur le serveur (`PATCH /folders/{id}`), comme le site.
- Listes intelligentes « Aujourd'hui » / « Planifié » sur l'accueil.
- Mode sombre (le site n'en a pas encore).
