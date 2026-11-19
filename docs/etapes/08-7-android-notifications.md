# Étape 8-7 : app Android, notifications

## Objectif

Dernière demande de `ModifBetterTodo.md` (« Application mobile ») : **un système de notification pour les tâches avec un certain jour / heure**. Maquette v3, page Android, écrans 3 (« Rappel à 14:30 »), 6 (Paramètres › Notifications) et 7 (notifications).

- **Tâche avec une heure** : un rappel à l'heure, ou 15 min / 1 h avant, avec les boutons **Fait**, **Dans 1 h**, **Demain**.
- **Tâches avec une date sans heure** (et celles en retard) : un **résumé du matin**, à l'heure choisie (9:00 par défaut), avec « Ouvrir Aujourd'hui ».

Choix (consignés au plan de l'étape 8) :
- les notifications sont **programmées sur le téléphone** (AlarmManager) à partir de sa base, **sans le serveur** : elles marchent hors ligne et sans serveur, sans service de Google ;
- les réglages sont **par téléphone** (DataStore), puisque seul Android a des notifications.

## Ce qui change pour l'utilisateur

- **Paramètres › Notifications** :
  - interrupteur « Rappels d'échéance » ;
  - À l'heure / 15 min avant / 1 h avant ;
  - « Tâches sans heure : rappel à [09:00] » : heure tapée, comme celle d'une tâche (`1930`, `19h30`…) ;
  - si Android bloque les notifications : un message en rouge et « Autoriser les notifications » ;
  - si les alarmes exactes ne sont pas autorisées : « Les rappels peuvent arriver avec quelques minutes de retard » et « Rappels à la minute près », qui ouvre le réglage d'Android (c'est l'utilisateur qui l'active).
- **Notification d'une tâche** : son titre, « Aujourd'hui 14:30 · Projet Atlas › Tâches ».
  - **Fait** : la tâche est cochée ;
  - **Dans 1 h** : le rappel revient dans une heure, la tâche ne change pas ;
  - **Demain** : l'échéance passe à demain, **même heure**.
- **Résumé** : « 3 tâches pour aujourd'hui » et leur liste, les tâches en retard d'abord, avec « (en retard) ».
- Toucher une notification ouvre l'app sur **Aujourd'hui**.
- Fait / Demain marchent **app fermée** : la modification est écrite dans la base du téléphone, puis synchronisée avec le serveur s'il y en a un.

## Fichiers

```
android/app/src/main/
  AndroidManifest.xml                      → permissions (POST_NOTIFICATIONS, SCHEDULE_EXACT_ALARM, RECEIVE_BOOT_COMPLETED),
                                             3 récepteurs, MainActivity en singleTop
  java/io/github/acssio/bettertodo/
    notify/Reminders.kt            → NOUVEAU : calcul pur (plan, nextSummary, summaryTasks), NotificationSettings
    notify/NotificationStore.kt    → NOUVEAU : réglages, reports (« Dans 1 h »), alarmes posées (DataStore)
    notify/ReminderScheduler.kt    → NOUVEAU : pose / annule les alarmes (reschedule)
    notify/Notifier.kt             → NOUVEAU : canaux, notification d'une tâche, résumé
    notify/Receivers.kt            → NOUVEAU : ReminderReceiver, ReminderActionReceiver, RescheduleReceiver, Background
    ui/NotificationSettingsSection.kt → NOUVEAU : Paramètres › Notifications
    ui/AppLinks.kt                 → NOUVEAU : « ouvrir Aujourd'hui » demandé par une notification
    ui/App.kt                      → replanifie après chaque modification et chaque synchro ; SyncEngine.current
    ui/HomeScreen.kt               → ouvre Aujourd'hui sur demande (AppLinks)
    ui/SettingsScreen.kt           → partie Notifications
    local/LocalApi.kt              → onWrite (après chaque modification)
    sync/SyncEngine.kt             → current (celle de l'app ouverte), touched()
    MainActivity.kt                → canaux, Intent « open=today » (onCreate / onNewIntent)
  test/…/RemindersTest.kt          → le calcul des rappels
```

## Comment ça marche

```
 modification (écran, synchro, bouton de notification) ─┐
 ouverture de l'app, réglages ───────────────────────────┼─► ReminderScheduler.reschedule()
 redémarrage, mise à jour, changement d'heure / fuseau ──┘        │
                                                                   ├─ tâches à faire avec une échéance (base du téléphone)
                                                                   ├─ Reminders.plan(…) → une alarme par tâche avec une heure
                                                                   ├─ annule les alarmes qui ne servent plus
                                                                   └─ pose le prochain résumé du matin
 alarme ──► ReminderReceiver ──► Notifier (rappel ou résumé) ──► reschedule()
 bouton ──► ReminderActionReceiver ──► Fait / Demain : LocalApi (+ synchro) ; Dans 1 h : NotificationStore.snooze ──► reschedule()
```

### Le calcul (Reminders, pur et testé)

- **Rappel** = échéance (date + heure, fuseau du téléphone) moins le décalage. Pas de rappel pour une tâche faite, sans heure, déjà passée, ou à plus de 30 jours (elle sera posée plus tard, à une prochaine replanification). 100 alarmes au plus.
- **Report** (« Dans 1 h ») : remplace l'heure du rappel, même si l'échéance est passée.
- **Résumé** : prochain moment à l'heure choisie (aujourd'hui si elle n'est pas passée, sinon demain). Au déclenchement : tâches à faire du jour **sans heure**, plus toutes celles **en retard**. S'il n'y en a aucune, pas de notification.

### Les alarmes (ReminderScheduler)

- **Une alarme par tâche**, reconnue par son adresse (`bettertodo://reminder/<id>`). Les id posés sont gardés pour annuler ceux qui ne servent plus.
- **Exacte** si Android l'autorise (`canScheduleExactAlarms`). Sinon :
  - alarme **proche** (< 13 min) : `setAndAllowWhileIdle`, Android la déclenche dans environ 3/4 du délai (≈ 40 s vu pour 1 min) ;
  - alarme **lointaine** : `setWindow` de **10 min**. Avec `setAndAllowWhileIdle`, la fenêtre allait jusqu'à **1 h** (vu sur l'émulateur pour le résumé de 9:00) : problème trouvé et corrigé pendant les tests.
- `reschedule` est protégé par un `Mutex`, et l'app le lance 1 s après une modification : plusieurs modifications d'affilée = une seule replanification.

### Les boutons, app ouverte ou fermée (Background)

- **App ouverte** : on passe par son `LocalApi` (`SyncEngine.current`). La modification est notée dans sa file et part au serveur 1,5 s après ; l'écran se recharge (`touched()`).
- **App fermée** : un `LocalApi` à part, qui note dans la file si le mode est « serveur », puis une synchronisation complète, dans les quelques secondes qu'Android laisse à un récepteur (`goAsync`). Hors ligne : la file partira à la prochaine ouverture.

## Lancer et tester

```bash
cd android && ./gradlew assembleDebug testDebugUnitTest
```

Voir les alarmes posées par l'app (heure prévue et fenêtre) :

```bash
~/Library/Android/sdk/platform-tools/adb shell dumpsys alarm | grep -A3 bettertodo
```

Voir les notifications affichées :

```bash
~/Library/Android/sdk/platform-tools/adb shell dumpsys notification --noredact | grep -A2 bettertodo
```

### Vérifié

- **98 tests JVM** (dont `RemindersTest` : à l'heure, 1 h avant, faite / sans heure / passée / trop loin / désactivé, report, ordre, prochain résumé, contenu du résumé) et **20 tests sur l'émulateur** ; `go vet` et `go test` ;
- **sur l'émulateur (Android 17), avec le vrai serveur, alarmes non exactes** (le cas par défaut) :
  - Paramètres › Notifications : bloquées → « Autoriser les notifications » → fenêtre d'Android → Allow → le message disparaît ;
  - tâche créée **sur le site** pour dans 2 min → après « tirer vers le bas », alarme posée → notification « Appeler le garage », « Aujourd'hui 19:20 · Projet Atlas › Tâches », Fait / Dans 1 h / Demain ;
  - **Fait** → tâche cochée **sur le serveur**, notification retirée ;
  - **Demain** → échéance au lendemain, **heure gardée**, sur le serveur ;
  - **Dans 1 h** → alarme reposée une heure plus tard, tâche inchangée ;
  - heure du résumé tapée `1927` → « 19:27 », alarme reposée → « 3 tâches pour aujourd'hui », la tâche en retard en premier ;
  - toucher le résumé ou un rappel (app en arrière-plan) → l'app s'ouvre sur Aujourd'hui.
- **Vu une fois, pas reproduit** : un « BetterTodo ne répond pas » en ouvrant Aujourd'hui depuis le résumé, avec l'émulateur très chargé (CPU à 46 %, pendant mes captures d'écran). Trois ouvertures par le même chemin, puis un vrai toucher de notification, n'ont rien donné. À surveiller sur un vrai téléphone.

## Limites connues

- **Sans « Rappels à la minute près »**, un rappel lointain peut arriver jusqu'à 10 min après l'heure, et Android peut le retarder davantage quand le téléphone est en veille profonde. C'est le réglage d'Android qui décide.
- Une tâche modifiée **sur le site** n'a son rappel à jour qu'après une synchronisation du téléphone (ouverture de l'app, tirer vers le bas…). Il n'y a pas encore de synchronisation périodique en arrière-plan (WorkManager), ni de notification envoyée par le serveur.
- Les tâches à plus de 30 jours n'ont leur alarme qu'à une replanification ultérieure : chaque ouverture de l'app et chaque modification replanifient tout.

## Où regarder en cas de problème

| Symptôme | Cause probable | Que faire |
|---|---|---|
| Aucune notification | Notifications bloquées, ou rappels désactivés | Paramètres › Notifications ; réglages Android de l'app |
| Pas d'alarme pour une tâche | Pas d'heure, faite, passée, à plus de 30 jours | `dumpsys alarm` ; Logcat `tag:Reminders` (« N rappel(s) posé(s) ») |
| Rappel en retard de plusieurs minutes | Alarmes exactes non autorisées, ou veille profonde | Paramètres › Notifications › Rappels à la minute près |
| « Fait » depuis la notification n'arrive pas sur le site | Hors ligne, ou session expirée | Ouvrir l'app (la file part) ; Paramètres › Serveur |
| Les rappels disparaissent après un redémarrage | `RescheduleReceiver` | Manifeste (BOOT_COMPLETED), Logcat `tag:Reminders` |
