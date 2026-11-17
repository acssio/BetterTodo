package io.github.acssio.bettertodo

import io.github.acssio.bettertodo.api.Folder
import io.github.acssio.bettertodo.api.TodoList
import io.github.acssio.bettertodo.ui.deleteFolderMessage
import io.github.acssio.bettertodo.ui.withoutFolder
import org.junit.Assert.assertEquals
import org.junit.Test

class HomeTest {
    private fun list(id: String, folderId: String?) = TodoList(id = id, folderId = folderId, name = id, color = "#2F6FEB", icon = "list", position = 0)

    @Test
    fun supprimerUnGroupeDeListesGardeSesListes() {
        val lists = listOf(list("a", "f1"), list("b", "f2"), list("c", null))
        assertEquals(listOf(null, "f2", null), withoutFolder(lists, "f1").map { it.folderId })
    }

    @Test
    fun messageDeConfirmation() {
        assertEquals(
            "« Maison » sera supprimé. Ses listes sont gardées, hors groupe.",
            deleteFolderMessage(Folder(id = "f1", name = "Maison", position = 0)),
        )
    }
}
