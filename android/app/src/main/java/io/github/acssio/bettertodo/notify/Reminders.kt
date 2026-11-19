package io.github.acssio.bettertodo.notify

import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.ZoneId

/** Réglages des notifications (Paramètres › Notifications), par téléphone (8-7). */
data class NotificationSettings(
    val enabled: Boolean = true,
    val offsetMinutes: Int = 0,        // Rappel : à l'heure (0), 15 min ou 1 h (60) avant
    val summaryTime: String = "09:00", // Résumé du matin : tâches du jour sans heure, et en retard
) {
    companion object {
        /** Les choix proposés : à l'heure, 15 min avant, 1 h avant (maquette v3). */
        val OFFSETS = listOf(0 to "À l'heure", 15 to "15 min avant", 60 to "1 h avant")
    }
}

/** Ce qu'il faut savoir d'une tâche pour ses rappels. */
data class ReminderTask(val id: String, val title: String, val dueDate: String?, val dueTime: String?, val done: Boolean)

/** Une alarme à poser : le rappel d'une tâche, à triggerAt (millisecondes). */
data class TaskAlarm(val taskId: String, val triggerAt: Long)

/**
 * Le calcul des rappels, sans Android : fonctions pures, testées par RemindersTest.
 * Les dates et heures des tâches sont celles du téléphone (son fuseau horaire).
 */
object Reminders {
    /** Au-delà, on ne pose pas encore l'alarme : elle sera posée à une prochaine replanification. */
    private const val HORIZON_DAYS = 30L

    /** Nombre maximum d'alarmes posées en même temps (Android en limite le nombre par app). */
    private const val MAX_ALARMS = 100

    /**
     * Les rappels des tâches à faire avec une date ET une heure, à l'heure moins le décalage choisi,
     * pas encore passés, dans les 30 prochains jours. Une tâche repoussée (« Dans 1 h ») sonne à l'heure
     * du report à la place.
     * @param snoozes tâche → moment du report (millisecondes)
     */
    fun plan(tasks: List<ReminderTask>, settings: NotificationSettings, now: Long, zone: ZoneId, snoozes: Map<String, Long> = emptyMap()): List<TaskAlarm> {
        if (!settings.enabled) return emptyList()
        val horizon = now + HORIZON_DAYS * 24 * 3600 * 1000
        return tasks.mapNotNull { task ->
            if (task.done) return@mapNotNull null
            val at = snoozes[task.id]?.takeIf { it > now } ?: dueAt(task, zone)?.minusMinutes(settings.offsetMinutes.toLong())?.atZone(zone)?.toInstant()?.toEpochMilli()
            if (at == null || at <= now || at > horizon) null else TaskAlarm(task.id, at)
        }.sortedBy { it.triggerAt }.take(MAX_ALARMS)
    }

    /** Date et heure d'échéance, ou null sans heure (ces tâches-là vont dans le résumé du matin). */
    fun dueAt(task: ReminderTask, zone: ZoneId): LocalDateTime? {
        val date = task.dueDate ?: return null
        val time = task.dueTime ?: return null
        return try {
            LocalDateTime.of(LocalDate.parse(date), LocalTime.parse(time))
        } catch (e: Exception) {
            null
        }
    }

    /** Prochain résumé du matin : aujourd'hui à l'heure choisie si elle n'est pas passée, sinon demain. */
    fun nextSummary(settings: NotificationSettings, now: Long, zone: ZoneId): Long? {
        if (!settings.enabled) return null
        val time = try {
            LocalTime.parse(settings.summaryTime)
        } catch (e: Exception) {
            return null
        }
        val nowAt = java.time.Instant.ofEpochMilli(now).atZone(zone)
        var at = nowAt.toLocalDate().atTime(time).atZone(zone)
        if (!at.isAfter(nowAt)) at = at.plusDays(1)
        return at.toInstant().toEpochMilli()
    }

    /**
     * Ce que montre le résumé du matin : les tâches à faire du jour **sans heure** (celles avec une heure
     * ont leur propre rappel) et toutes celles **en retard**, les plus anciennes d'abord.
     */
    fun summaryTasks(tasks: List<ReminderTask>, today: LocalDate): List<ReminderTask> {
        val todayIso = today.toString()
        return tasks.filter { task ->
            val date = task.dueDate
            !task.done && date != null && (date < todayIso || (date == todayIso && task.dueTime == null))
        }.sortedWith(compareBy({ it.dueDate }, { it.dueTime ?: "99:99" }))
    }
}
