package io.github.acssio.bettertodo.notify

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.util.Log
import io.github.acssio.bettertodo.local.LocalDatabase
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.time.ZoneId

/**
 * Pose les alarmes des rappels sur le téléphone (AlarmManager), à partir de la base locale (8-7).
 *
 * reschedule() recalcule TOUT (Reminders.plan) et annule ce qui ne sert plus. On l'appelle à chaque
 * fois que quelque chose a pu changer : modification d'une tâche, synchronisation, ouverture de l'app,
 * réglages, redémarrage du téléphone, changement d'heure ou de fuseau.
 *
 * Alarme « exacte » si Android l'autorise (Paramètres › Rappels à la minute), sinon une alarme que
 * le système peut retarder (au plus 10 min, setWindow) pour économiser la batterie.
 */
object ReminderScheduler {
    private const val TAG = "Reminders" // Filtre Logcat : tag:Reminders
    const val ACTION_TASK = "io.github.acssio.bettertodo.REMINDER_TASK"
    const val ACTION_SUMMARY = "io.github.acssio.bettertodo.REMINDER_SUMMARY"
    const val EXTRA_TASK_ID = "taskId"
    private const val INEXACT_WINDOW_MS = 10 * 60 * 1000L // Le minimum accepté par setWindow
    private const val NEAR_MS = 13 * 60 * 1000L            // En dessous, 3/4 du délai < 10 min

    private val mutex = Mutex() // Une replanification à la fois

    suspend fun reschedule(context: Context) = mutex.withLock {
        val app = context.applicationContext
        val store = NotificationStore(app)
        val settings = store.settings.first()
        val now = System.currentTimeMillis()
        val zone = ZoneId.systemDefault()
        store.keepSnoozesAfter(now)

        val tasks = LocalDatabase.get(app).dao().dueTasks(null).map { ReminderTask(it.id, it.title, it.dueDate, it.dueTime, it.done) }
        val alarms = Reminders.plan(tasks, settings, now, zone, store.snoozes())

        // Annule les alarmes des tâches qui n'en ont plus besoin (faite, supprimée, échéance changée…)
        val planned = alarms.map { it.taskId }.toSet()
        for (id in store.scheduledIds() - planned) cancel(app, taskIntent(app, id))
        alarms.forEach { set(app, it.triggerAt, taskIntent(app, it.taskId)) }
        store.saveScheduledIds(planned)

        // Le résumé du matin : toujours posé (s'il n'y a rien ce jour-là, il ne s'affiche pas)
        val summary = Reminders.nextSummary(settings, now, zone)
        if (summary != null) set(app, summary, summaryIntent(app)) else cancel(app, summaryIntent(app))
        Log.i(TAG, "${alarms.size} rappel(s) posé(s), résumé : ${summary != null}")
    }

    /** Android permet-il les alarmes à la minute près ? (toujours avant Android 12) */
    fun canBeExact(context: Context): Boolean {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.S) return true
        return context.getSystemService(AlarmManager::class.java).canScheduleExactAlarms()
    }

    private fun set(context: Context, at: Long, intent: PendingIntent) {
        val alarms = context.getSystemService(AlarmManager::class.java)
        if (canBeExact(context)) {
            alarms.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, at, intent)
        } else if (at - System.currentTimeMillis() < NEAR_MS) {
            // Sans alarme exacte, Android choisit le moment dans une fenêtre d'environ 3/4 du délai :
            // pour une alarme proche, c'est moins de 10 min (≈ 1 min vu sur l'émulateur pour 2 min de délai)
            alarms.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, at, intent)
        } else {
            // Pour une alarme lointaine, cette fenêtre irait jusqu'à 1 h (vu pour le résumé) : on la limite à 10 min
            alarms.setWindow(AlarmManager.RTC_WAKEUP, at, INEXACT_WINDOW_MS, intent)
        }
    }

    private fun cancel(context: Context, intent: PendingIntent) {
        context.getSystemService(AlarmManager::class.java).cancel(intent)
        intent.cancel()
    }

    /** Une alarme par tâche : l'adresse (data) rend chaque PendingIntent unique. */
    private fun taskIntent(context: Context, taskId: String): PendingIntent {
        val intent = Intent(context, ReminderReceiver::class.java)
            .setAction(ACTION_TASK)
            .setData(Uri.parse("bettertodo://reminder/$taskId"))
            .putExtra(EXTRA_TASK_ID, taskId)
        return PendingIntent.getBroadcast(context, 0, intent, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
    }

    private fun summaryIntent(context: Context): PendingIntent {
        val intent = Intent(context, ReminderReceiver::class.java).setAction(ACTION_SUMMARY)
        return PendingIntent.getBroadcast(context, 0, intent, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
    }
}
