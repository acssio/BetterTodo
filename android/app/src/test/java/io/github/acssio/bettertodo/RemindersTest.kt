package io.github.acssio.bettertodo

import io.github.acssio.bettertodo.notify.NotificationSettings
import io.github.acssio.bettertodo.notify.ReminderTask
import io.github.acssio.bettertodo.notify.Reminders
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.time.LocalDate
import java.time.ZoneId
import java.time.ZonedDateTime

/** Calcul des rappels (8-7). */
class RemindersTest {
    private val zone = ZoneId.of("Europe/Paris")
    private fun at(day: Int, hour: Int, minute: Int = 0) = ZonedDateTime.of(2026, 11, day, hour, minute, 0, 0, zone).toInstant().toEpochMilli()
    private val now = at(19, 10) // 19 nov. 2026, 10:00
    private fun task(id: String, date: String?, time: String?, done: Boolean = false) = ReminderTask(id, id, date, time, done)

    @Test
    fun rappelALHeure() {
        val alarms = Reminders.plan(listOf(task("a", "2026-11-19", "14:30")), NotificationSettings(), now, zone)
        assertEquals(listOf("a" to at(19, 14, 30)), alarms.map { it.taskId to it.triggerAt })
    }

    @Test
    fun avecDecalage() {
        val alarms = Reminders.plan(listOf(task("a", "2026-11-19", "14:30")), NotificationSettings(offsetMinutes = 60), now, zone)
        assertEquals(at(19, 13, 30), alarms.single().triggerAt)
    }

    @Test
    fun pasDeRappel() {
        val tasks = listOf(
            task("passée", "2026-11-19", "09:00"),
            task("faite", "2026-11-19", "14:00", done = true),
            task("sans heure", "2026-11-19", null),
            task("sans date", null, null),
            task("trop loin", "2027-02-01", "09:00"),
        )
        assertEquals(emptyList<String>(), Reminders.plan(tasks, NotificationSettings(), now, zone).map { it.taskId })
        assertEquals(0, Reminders.plan(listOf(task("a", "2026-11-19", "14:30")), NotificationSettings(enabled = false), now, zone).size)
    }

    @Test
    fun reportDUneHeure() {
        val snooze = at(19, 11)
        val alarms = Reminders.plan(listOf(task("a", "2026-11-19", "09:00")), NotificationSettings(), now, zone, mapOf("a" to snooze))
        assertEquals(snooze, alarms.single().triggerAt) // Échéance passée, mais repoussée : sonne au report
    }

    @Test
    fun triesParHeure() {
        val alarms = Reminders.plan(listOf(task("b", "2026-11-20", "08:00"), task("a", "2026-11-19", "18:00")), NotificationSettings(), now, zone)
        assertEquals(listOf("a", "b"), alarms.map { it.taskId })
    }

    @Test
    fun prochainResume() {
        assertEquals(at(20, 9), Reminders.nextSummary(NotificationSettings(), now, zone))                     // 9:00 passée : demain
        assertEquals(at(19, 18), Reminders.nextSummary(NotificationSettings(summaryTime = "18:00"), now, zone)) // Pas encore : aujourd'hui
        assertNull(Reminders.nextSummary(NotificationSettings(enabled = false), now, zone))
    }

    @Test
    fun contenuDuResume() {
        val tasks = listOf(
            task("avec heure", "2026-11-19", "14:30"),
            task("du jour", "2026-11-19", null),
            task("en retard", "2026-11-17", "08:00"),
            task("demain", "2026-11-20", null),
            task("faite", "2026-11-19", null, done = true),
        )
        assertEquals(listOf("en retard", "du jour"), Reminders.summaryTasks(tasks, LocalDate.of(2026, 11, 19)).map { it.id })
    }
}
