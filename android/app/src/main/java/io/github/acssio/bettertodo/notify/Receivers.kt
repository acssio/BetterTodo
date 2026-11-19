package io.github.acssio.bettertodo.notify

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import io.github.acssio.bettertodo.api.ApiClient
import io.github.acssio.bettertodo.api.TaskChanges
import io.github.acssio.bettertodo.data.MODE_SERVER
import io.github.acssio.bettertodo.data.SessionStore
import io.github.acssio.bettertodo.local.LocalApi
import io.github.acssio.bettertodo.local.LocalDatabase
import io.github.acssio.bettertodo.sync.SyncEngine
import io.github.acssio.bettertodo.ui.DueDates
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import java.time.LocalDate

/*
 * Les récepteurs des notifications (8-7), déclarés dans AndroidManifest.xml.
 * Android peut les appeler app fermée : ils ouvrent eux-mêmes la base, et finissent par
 * goAsync() + une coroutine (un récepteur n'a que quelques secondes).
 */

/** Lance work en arrière-plan et prévient Android quand c'est fini. */
private fun BroadcastReceiver.runAsync(work: suspend () -> Unit) {
    val pending = goAsync()
    CoroutineScope(SupervisorJob() + Dispatchers.IO).launch {
        try {
            work()
        } finally {
            pending.finish()
        }
    }
}

/** L'heure d'une alarme est arrivée : rappel d'une tâche, ou résumé du matin. */
class ReminderReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) = runAsync {
        val dao = LocalDatabase.get(context).dao()
        when (intent.action) {
            ReminderScheduler.ACTION_TASK -> {
                val taskId = intent.getStringExtra(ReminderScheduler.EXTRA_TASK_ID) ?: return@runAsync
                val task = dao.task(taskId)
                if (task != null && !task.done && task.dueDate != null) { // Faite ou supprimée entre-temps : rien
                    val group = dao.group(task.groupId)
                    val list = group?.let { dao.list(it.listId) }
                    val place = if (list != null) " · ${list.name} › ${group.name}" else ""
                    Notifier.showTask(context, task.id, task.title, DueDates.format(task.dueDate, task.dueTime) + place)
                }
            }
            ReminderScheduler.ACTION_SUMMARY -> {
                val today = LocalDate.now()
                val tasks = dao.dueTasks(today.toString()).map { ReminderTask(it.id, it.title, it.dueDate, it.dueTime, it.done) }
                val lines = Reminders.summaryTasks(tasks, today).map { task ->
                    if (task.dueDate!! < today.toString()) "${task.title} (en retard)" else task.title
                }
                if (lines.isNotEmpty()) Notifier.showSummary(context, lines)
            }
        }
        ReminderScheduler.reschedule(context) // Alarmes suivantes (dont le résumé de demain)
    }
}

/** Boutons d'un rappel : Fait, Dans 1 h, Demain. */
class ReminderActionReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) = runAsync {
        val taskId = intent.getStringExtra(ReminderScheduler.EXTRA_TASK_ID) ?: return@runAsync
        Notifier.cancelTask(context, taskId)
        when (intent.action) {
            ACTION_SNOOZE -> NotificationStore(context).snooze(taskId, System.currentTimeMillis() + SNOOZE_MS) // Rien ne change sur la tâche
            ACTION_DONE -> Background.change(context) { it.updateTask(taskId, TaskChanges().done(true)) }
            ACTION_TOMORROW -> Background.change(context) { local ->
                // La date passe à demain, l'heure reste (PATCH due_date seul ne touche pas à due_time)
                local.updateTask(taskId, TaskChanges().dueDate(DueDates.tomorrowIso()))
            }
        }
        ReminderScheduler.reschedule(context)
    }

    companion object {
        const val ACTION_DONE = "io.github.acssio.bettertodo.REMINDER_DONE"
        const val ACTION_SNOOZE = "io.github.acssio.bettertodo.REMINDER_SNOOZE"
        const val ACTION_TOMORROW = "io.github.acssio.bettertodo.REMINDER_TOMORROW"
        private const val SNOOZE_MS = 60 * 60 * 1000L
    }
}

/** Redémarrage du téléphone, mise à jour de l'app, changement d'heure ou de fuseau : les alarmes sont à reposer. */
class RescheduleReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) = runAsync {
        ReminderScheduler.reschedule(context)
    }
}

/** Modifier une tâche depuis une notification, app ouverte ou non. */
object Background {
    /**
     * Fait la modification dans la base du téléphone (notée dans la file s'il y a un serveur), puis
     * synchronise : par la synchronisation de l'app si elle est ouverte, sinon une synchronisation à part.
     */
    suspend fun change(context: Context, write: suspend (LocalApi) -> Unit) {
        val app = context.applicationContext
        val db = LocalDatabase.get(app)
        val session = SessionStore(app).load()
        val serverMode = session.mode == MODE_SERVER

        val running = SyncEngine.current
        if (running != null) {
            try {
                write(running.local) // Celle de l'app : notée dans sa file, la synchronisation suit toute seule
            } catch (e: Exception) {
                return // Tâche supprimée entre-temps
            }
            running.touched() // L'écran ouvert se recharge
            return
        }
        val local = LocalApi(db).apply { recording = serverMode }
        try {
            write(local)
        } catch (e: Exception) {
            return // Tâche supprimée entre-temps : rien à faire
        }
        if (serverMode && session.token != null && session.serverUrl.isNotEmpty()) {
            val engine = SyncEngine(db, local, CoroutineScope(SupervisorJob() + Dispatchers.IO))
            engine.connect(ApiClient(session.serverUrl, session.token))
            engine.syncNow() // Hors ligne : l'erreur est gardée, la file partira à la prochaine ouverture
        }
    }
}
