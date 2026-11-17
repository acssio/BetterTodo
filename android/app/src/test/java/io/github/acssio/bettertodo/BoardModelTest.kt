package io.github.acssio.bettertodo

import io.github.acssio.bettertodo.api.Board
import io.github.acssio.bettertodo.api.BoardGroup
import io.github.acssio.bettertodo.api.Task
import io.github.acssio.bettertodo.api.TodoList
import io.github.acssio.bettertodo.ui.BoardModel
import org.junit.Assert.assertEquals
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

    @Test
    fun remplacerUneTache() {
        val list = TodoList(id = "l", name = "Liste", color = "#2F6FEB", icon = "list", position = 0)
        val board = Board(list, listOf(BoardGroup("g", "Tâches", 0, listOf(todo, done))))
        val updated = BoardModel.replaceTask(board, todo.copy(done = true))
        assertEquals(listOf(true, true), updated.groups[0].tasks.map { it.done })
        assertEquals(listOf(false, true), board.groups[0].tasks.map { it.done }) // L'original n'a pas changé
    }
}
