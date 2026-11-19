package io.github.acssio.bettertodo

import io.github.acssio.bettertodo.api.BoardGroup
import io.github.acssio.bettertodo.api.TodoList
import io.github.acssio.bettertodo.ui.PlacePicker
import io.github.acssio.bettertodo.ui.QuickTaskModel
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.time.LocalDate

/** Comme quickTask.test.ts du site. */
class QuickTaskTest {
    private val lists = listOf("l1", "l2").map { TodoList(id = it, name = it, color = "#2F6FEB", icon = "list", position = 0) }
    private val groups = listOf("g1", "g2").map { BoardGroup(id = it, name = it, position = 0) }

    @Test
    fun listeRetenue() {
        assertEquals("l2", PlacePicker.chooseList("l2", lists))
    }

    @Test
    fun listeDisparueOuAucuneMemoire() {
        assertEquals("l1", PlacePicker.chooseList("supprimée", lists))
        assertEquals("l1", PlacePicker.chooseList(null, lists))
        assertNull(PlacePicker.chooseList("l1", emptyList()))
    }

    @Test
    fun groupe() {
        assertEquals("g2", PlacePicker.chooseGroup("g2", groups))
        assertEquals("g1", PlacePicker.chooseGroup("d'une autre liste", groups))
        assertNull(PlacePicker.chooseGroup(null, emptyList()))
    }

    @Test
    fun echeanceProposee() {
        val today = LocalDate.of(2026, 11, 19)
        assertNull(QuickTaskModel.dueDateFor("none", today))
        assertEquals("2026-11-19", QuickTaskModel.dueDateFor("today", today))
        assertEquals("2026-11-20", QuickTaskModel.dueDateFor("tomorrow", today))
        assertEquals("2026-11-19", QuickTaskModel.dueDateFor("inconnue", today)) // Valeur d'un serveur plus récent
    }
}
