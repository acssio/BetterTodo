package io.github.acssio.bettertodo

import io.github.acssio.bettertodo.api.BoardGroup
import io.github.acssio.bettertodo.api.TodoList
import io.github.acssio.bettertodo.ui.QuickTaskModel
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/** Comme quickTask.test.ts du site. */
class QuickTaskTest {
    private val lists = listOf("l1", "l2").map { TodoList(id = it, name = it, color = "#2F6FEB", icon = "list", position = 0) }
    private val groups = listOf("g1", "g2").map { BoardGroup(id = it, name = it, position = 0) }

    @Test
    fun listeRetenue() {
        assertEquals("l2", QuickTaskModel.chooseList("l2", lists))
    }

    @Test
    fun listeDisparueOuAucuneMemoire() {
        assertEquals("l1", QuickTaskModel.chooseList("supprimée", lists))
        assertEquals("l1", QuickTaskModel.chooseList(null, lists))
        assertNull(QuickTaskModel.chooseList("l1", emptyList()))
    }

    @Test
    fun groupe() {
        assertEquals("g2", QuickTaskModel.chooseGroup("g2", groups))
        assertEquals("g1", QuickTaskModel.chooseGroup("d'une autre liste", groups))
        assertNull(QuickTaskModel.chooseGroup(null, emptyList()))
    }
}
