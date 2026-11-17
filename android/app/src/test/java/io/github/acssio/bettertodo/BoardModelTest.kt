package io.github.acssio.bettertodo

import io.github.acssio.bettertodo.api.Board
import io.github.acssio.bettertodo.api.BoardGroup
import io.github.acssio.bettertodo.api.Task
import io.github.acssio.bettertodo.api.TodoList
import io.github.acssio.bettertodo.ui.BoardModel
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class BoardModelTest {
    private val todo = Task(id = "a", groupId = "g", title = "À faire", position = 0)
    private val done = Task(id = "b", groupId = "g", title = "Faite", position = 1, done = true)

    @Test
    fun tachesFaitesMasquees() {
        assertEquals(listOf(todo), BoardModel.visibleTasks(listOf(todo, done), showDone = false, leaving = emptySet()))
    }

    @Test
    fun tachesFaitesAffichees() {
        assertEquals(listOf(todo, done), BoardModel.visibleTasks(listOf(todo, done), showDone = true, leaving = emptySet()))
    }

    @Test
    fun tacheQuiVientDEtreCocheeResteVisible() {
        assertEquals(listOf(todo, done), BoardModel.visibleTasks(listOf(todo, done), showDone = false, leaving = setOf("b")))
    }

    private val list = TodoList(id = "l", name = "Liste", color = "#2F6FEB", icon = "list", position = 0)
    private val other = Task(id = "c", groupId = "g2", title = "Autre groupe", position = 0)
    private val board = Board(list, listOf(BoardGroup("g", "Tâches", 0, listOf(todo, done)), BoardGroup("g2", "Plus tard", 1, listOf(other))))

    private fun titles(board: Board) = board.groups.map { group -> group.tasks.map { it.title } }

    @Test
    fun remplacerUneTache() {
        val updated = BoardModel.applyTask(board, todo.copy(done = true))
        assertEquals(listOf(true, true), updated.groups[0].tasks.map { it.done })
        assertEquals(listOf(false, true), board.groups[0].tasks.map { it.done }) // L'original n'a pas changé
    }

    @Test
    fun changerDeGroupeMetALaFin() {
        val updated = BoardModel.applyTask(board, todo.copy(groupId = "g2"))
        assertEquals(listOf(listOf("Faite"), listOf("Autre groupe", "À faire")), titles(updated))
    }

    @Test
    fun nouvelleTacheALaFin() {
        val created = Task(id = "n", groupId = "g", title = "Nouvelle", position = 2)
        assertEquals(listOf(listOf("À faire", "Faite", "Nouvelle"), listOf("Autre groupe")), titles(BoardModel.applyTask(board, created)))
    }

    @Test
    fun supprimerUneTache() {
        assertEquals(listOf(listOf("Faite"), listOf("Autre groupe")), titles(BoardModel.removeTask(board, "a")))
    }

    @Test
    fun nettoyerUnTag() {
        assertEquals("Courses", BoardModel.cleanTag("  #Courses "))
        assertEquals("bio", BoardModel.cleanTag("bio"))
        assertNull(BoardModel.cleanTag(" # "))
    }
}
