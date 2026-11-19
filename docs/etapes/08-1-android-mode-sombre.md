# Étape 8-1 : app Android, barre du haut grise et mode sombre

## Objectif

Reprendre dans l'app Android ce que le site a reçu à l'étape 7-1 (`ModifBetterTodo.md`, partie « UI Design ») :

- la barre du haut d'une liste (et d'Aujourd'hui / Planifié) est **grise**, pour se distinguer du reste de l'écran ;
- un **mode sombre**, au choix **Système / Clair / Sombre** dans Paramètres › Apparence.

Maquette : canevas « BetterTodo – Maquettes v3 », page Android (clair à gauche, sombre à droite). Les couleurs sont celles de `web/src/index.css` (clair) et de son bloc `:root[data-theme="dark"]` (sombre).

Choix (comme le site) :
- le thème est **retenu par téléphone** (DataStore), pas sur le compte : ni API ni migration ;
- « Système » (par défaut) suit le mode du téléphone, **même pendant que l'app est ouverte**.

## Fichiers

```
android/app/src/main/java/io/github/acssio/bettertodo/
  MainActivity.kt              → suit AppearanceStore, choisit clair / sombre, icônes de la barre d'état
  data/AppearanceStore.kt      → NOUVEAU : réglage « appearance » (system / light / dark)
  ui/theme/Theme.kt            → AppColors (LightColors, DarkColors), LocalAppColors, Palette, ThemeMode
  ui/SettingsScreen.kt         → partie « Apparence » (en premier)
  ui/ListScreen.kt, SmartScreen.kt → barre du haut Palette.header (grise), onglets sur Palette.surface
  ui/HomeScreen.kt, LoginScreen.kt, ChangePasswordScreen.kt → logo et bouton Quick task en primaryBg / primaryText
```

## Comment ça marche

```
AppearanceStore.mode (Flow) ──► MainActivity : ThemeMode ──isDark()──► dark (Système = isSystemInDarkTheme())
                                                                          │
                                       BetterTodoTheme(dark) ── LocalAppColors = DarkColors ou LightColors
                                                                          │
                                       Palette.text, Palette.border… lisent LocalAppColors.current
```

- **`Palette` n'a pas changé de forme** : les ~120 `Palette.xxx` de l'app sont restés tels quels. Avant, c'étaient des constantes ; maintenant chaque propriété est un *getter* `@Composable` qui lit les couleurs du thème en cours (`LocalAppColors`, une `CompositionLocal`). Conséquence : `Palette.xxx` ne marche **que dans une fonction `@Composable`** (le compilateur le signale sinon).
- **Une nouvelle couleur** = une propriété dans `AppColors`, avec sa valeur dans `LightColors` **et** dans `DarkColors`, puis un getter dans `Palette`. Comme sur le site : aucune couleur écrite en dur dans les écrans, sauf le blanc posé sur un fond coloré (accent, couleur d'une liste).
- **Les couleurs Material** (boutons, champs, panneaux du bas, dialogues) sont calculées à partir des mêmes valeurs dans `BetterTodoTheme` : `darkColorScheme()` ou `lightColorScheme()`, puis on remplace ce qu'il faut.
- **Logo et bouton « Quick task »** : noir en clair, blanc cassé en sombre (`primaryBg` / `primaryText`, comme `--primary-bg` du site). Avant, c'était `Palette.text` + blanc, ce qui aurait donné une coche blanche sur fond blanc en sombre.
- **Barre d'état et barre de navigation** : `enableEdgeToEdge` est rappelé à chaque changement de thème, avec des icônes claires sur fond sombre (et l'inverse). Sans ça, en forçant « Sombre » sur un téléphone en clair, l'heure et la batterie restaient noires sur fond noir.
- Changer le réglage redessine toute l'app tout de suite : `collectAsState` sur le `Flow` de DataStore.

## Lancer et tester

```bash
cd android && ./gradlew assembleDebug testDebugUnitTest
```

```bash
~/Library/Android/sdk/platform-tools/adb install -r app/build/outputs/apk/debug/app-debug.apk
```

Pour tester « Système » sans passer par les réglages du téléphone :

```bash
~/Library/Android/sdk/platform-tools/adb shell cmd uimode night yes
```

### Vérifié sur l'émulateur (Pixel 10), avec de vrais appuis

- Paramètres › Apparence : « Sombre » → toute l'app passe en sombre immédiatement, barre d'état en icônes claires ;
- accueil, liste (barre grise, onglets, tâches, tags, urgence, échéance en retard), panneau de tâche : lisibles en sombre ;
- « Clair » → barre du haut grise bien distincte du fond, onglets sur fond blanc ;
- « Système » + `cmd uimode night yes` → l'app passe en sombre toute seule ;
- le choix est gardé après fermeture de l'app.

## Où regarder en cas de problème

| Symptôme | Cause probable | Que faire |
|---|---|---|
| Erreur de compilation « @Composable invocations can only happen… » | `Palette.xxx` utilisé hors d'une fonction `@Composable` (dans un `remember { }`, un `drawBehind { }`, une classe) | Lire la couleur avant, dans le composable : `val c = Palette.text` |
| Un élément reste blanc en sombre | Couleur écrite en dur (`Color.White`, `Color(0x…)`) ou couleur Material par défaut | Chercher `Color.White` / `Color(0x` ; ajouter la couleur dans `AppColors` |
| Heure et batterie invisibles | `enableEdgeToEdge` non rappelé avec le bon style | `MainActivity`, `LaunchedEffect(dark)` |
| Le choix n'est pas gardé | DataStore « appearance » | `AppearanceStore` ; effacer les données de l'app le remet à « Système » |
