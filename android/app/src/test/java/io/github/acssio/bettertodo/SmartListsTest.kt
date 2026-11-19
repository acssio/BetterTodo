package io.github.acssio.bettertodo

import io.github.acssio.bettertodo.api.DueTask
import io.github.acssio.bettertodo.api.ListRef
import io.github.acssio.bettertodo.api.Task
import io.github.acssio.bettertodo.ui.SmartKind
import io.github.acssio.bettertodo.ui.SmartLists
import io.github.acssio.bettertodo.ui.SmartModel
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate

class SmartListsTest {
    private val today = LocalDate.of(2026, 11, 17) // Un mardi
    private val list = ListRef("l", "Maison", "#E8A33D", "house")

    private fun due(id: String, date: String?, done: Boolean = false, time: String? = null) =
        DueTask(Task(id = id, groupId = "g", title = id, position = 0, dueDate = date, dueTime = time, done = done), list, "À faire")

    @Test
    fun memeJourParHeureSansHeureEnDernier() {
        assertEquals(
            listOf("Aujourd'hui" to listOf("b", "c", "a")),
            shape(SmartKind.TODAY, due("a", "2026-11-17"), due("c", "2026-11-17", time = "14:30"), due("b", "2026-11-17", time = "09:00")),
        )
    }

    /** Titres des sections et ids de leurs tâches, pour comparer facilement. */
    private fun shape(kind: SmartKind, vararg tasks: DueTask) =
        SmartLists.sections(tasks.toList(), kind, today).map { section -> section.title to section.tasks.map { it.task.id } }

    @Test
    fun aujourdHui() {
        assertEquals(
            listOf("En retard" to listOf("a", "b"), "Aujourd'hui" to listOf("c")),
            shape(SmartKind.TODAY, due("c", "2026-11-17"), due("a", "2026-11-10"), due("b", "2026-11-16")),
        )
    }

    @Test
    fun planifie() {
        assertEquals(
            listOf(
                "En retard" to listOf("a"),
                "Aujourd'hui" to listOf("b"),
                "Demain" to listOf("c"),
                "mer. 25 nov." to listOf("d", "e"),
                "dim. 3 janv. 2027" to listOf("f"),
            ),
            shape(
                SmartKind.SCHEDULED,
                due("a", "2026-11-01"), due("b", "2026-11-17"), due("c", "2026-11-18"),
                due("d", "2026-11-25"), due("e", "2026-11-25"), due("f", "2027-01-03"),
            ),
        )
    }

    @Test
    fun sectionEnRetardEnRouge() {
        val sections = SmartLists.sections(listOf(due("a", "2026-11-01"), due("b", "2026-11-17")), SmartKind.TODAY, today)
        assertEquals(listOf(true, false), sections.map { it.late })
    }

    @Test
    fun aSaPlaceDansLaVue() {
        assertTrue(SmartLists.belongs(due("a", "2026-11-17").task, SmartKind.TODAY, today))
        assertTrue(SmartLists.belongs(due("a", "2026-11-01").task, SmartKind.TODAY, today))
        assertFalse(SmartLists.belongs(due("a", "2026-11-18").task, SmartKind.TODAY, today)) // Repoussée à demain
        assertTrue(SmartLists.belongs(due("a", "2026-11-18").task, SmartKind.SCHEDULED, today))
        assertFalse(SmartLists.belongs(due("a", null).task, SmartKind.SCHEDULED, today))      // Échéance enlevée
        assertFalse(SmartLists.belongs(due("a", "2026-11-17", done = true).task, SmartKind.TODAY, today))
    }

    @Test
    fun tacheCocheeEncoreVisibleUnInstant() {
        val checked = due("a", "2027-11-17", done = true) // Date lointaine : la vue « Planifié » ne dépend pas d'aujourd'hui
        assertEquals(emptyList<DueTask>(), SmartModel.visible(listOf(checked), SmartKind.SCHEDULED, emptySet()))
        assertEquals(listOf(checked), SmartModel.visible(listOf(checked), SmartKind.SCHEDULED, setOf("a")))
    }
}
