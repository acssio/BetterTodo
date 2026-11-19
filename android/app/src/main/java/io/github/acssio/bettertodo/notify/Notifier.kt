package io.github.acssio.bettertodo.notify

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import com.composables.icons.lucide.R // Icônes de la bibliothèque icons-lucide-android
import io.github.acssio.bettertodo.MainActivity

/**
 * Affiche les notifications (8-7), comme sur la maquette v3 (Android, écran 7) :
 *  - rappel d'une tâche : titre, « Aujourd'hui 14:30 · Liste › Groupe », boutons Fait / Dans 1 h / Demain ;
 *  - résumé du matin : « 3 tâches pour aujourd'hui », la liste, bouton « Ouvrir Aujourd'hui ».
 * Toucher une notification ouvre l'app sur « Aujourd'hui ».
 */
object Notifier {
    private const val CHANNEL_TASKS = "reminders"
    private const val CHANNEL_SUMMARY = "summary"
    private const val SUMMARY_ID = 1 // Les rappels de tâches utilisent le hash de l'id de la tâche

    /** Les deux canaux, réglables par l'utilisateur dans les réglages Android de l'app. */
    fun createChannels(context: Context) {
        val manager = context.getSystemService(NotificationManager::class.java)
        manager.createNotificationChannel(
            NotificationChannel(CHANNEL_TASKS, "Rappels d'échéance", NotificationManager.IMPORTANCE_HIGH).apply {
                description = "À l'heure d'une tâche (ou un peu avant)"
            }
        )
        manager.createNotificationChannel(
            NotificationChannel(CHANNEL_SUMMARY, "Résumé du matin", NotificationManager.IMPORTANCE_DEFAULT).apply {
                description = "Les tâches du jour sans heure, et celles en retard"
            }
        )
    }

    /** L'app a-t-elle le droit d'afficher des notifications ? (demandé à partir d'Android 13) */
    fun allowed(context: Context): Boolean {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) return NotificationManagerCompat.from(context).areNotificationsEnabled()
        return ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED
    }

    fun notificationId(taskId: String) = taskId.hashCode()

    fun showTask(context: Context, taskId: String, title: String, detail: String) {
        if (!allowed(context)) return
        createChannels(context)
        val notification = NotificationCompat.Builder(context, CHANNEL_TASKS)
            .setSmallIcon(R.drawable.lucide_ic_circle_check)
            .setContentTitle(title)
            .setContentText(detail)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setCategory(NotificationCompat.CATEGORY_REMINDER)
            .setAutoCancel(true)
            .setContentIntent(openToday(context))
            .addAction(0, "Fait", action(context, ReminderActionReceiver.ACTION_DONE, taskId))
            .addAction(0, "Dans 1 h", action(context, ReminderActionReceiver.ACTION_SNOOZE, taskId))
            .addAction(0, "Demain", action(context, ReminderActionReceiver.ACTION_TOMORROW, taskId))
            .build()
        NotificationManagerCompat.from(context).notify(notificationId(taskId), notification)
    }

    /** @param lines une ligne par tâche (« Envoyer le devis (en retard) ») */
    fun showSummary(context: Context, lines: List<String>) {
        if (!allowed(context)) return
        createChannels(context)
        val title = if (lines.size == 1) "1 tâche pour aujourd'hui" else "${lines.size} tâches pour aujourd'hui"
        val style = NotificationCompat.InboxStyle()
        lines.take(6).forEach { style.addLine(it) }
        if (lines.size > 6) style.setSummaryText("et ${lines.size - 6} de plus")
        val notification = NotificationCompat.Builder(context, CHANNEL_SUMMARY)
            .setSmallIcon(R.drawable.lucide_ic_sun)
            .setContentTitle(title)
            .setContentText(lines.joinToString(" · "))
            .setStyle(style)
            .setAutoCancel(true)
            .setContentIntent(openToday(context))
            .addAction(0, "Ouvrir Aujourd'hui", openToday(context))
            .build()
        NotificationManagerCompat.from(context).notify(SUMMARY_ID, notification)
    }

    fun cancelTask(context: Context, taskId: String) {
        NotificationManagerCompat.from(context).cancel(notificationId(taskId))
    }

    /** Ouvre l'app sur « Aujourd'hui » (voir MainActivity.EXTRA_OPEN). */
    private fun openToday(context: Context): PendingIntent {
        val intent = Intent(context, MainActivity::class.java)
            .putExtra(MainActivity.EXTRA_OPEN, MainActivity.OPEN_TODAY)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP)
        return PendingIntent.getActivity(context, 0, intent, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
    }

    private fun action(context: Context, action: String, taskId: String): PendingIntent {
        val intent = Intent(context, ReminderActionReceiver::class.java)
            .setAction(action)
            .setData(Uri.parse("bettertodo://action/$taskId")) // Unique par tâche
            .putExtra(ReminderScheduler.EXTRA_TASK_ID, taskId)
        return PendingIntent.getBroadcast(context, 0, intent, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
    }
}
