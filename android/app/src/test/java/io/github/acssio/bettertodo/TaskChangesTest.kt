package io.github.acssio.bettertodo

import io.github.acssio.bettertodo.api.ListChanges
import io.github.acssio.bettertodo.api.TaskChanges
import org.junit.Assert.assertEquals
import org.junit.Test

/** Le corps de PATCH /tasks/{id} : seuls les champs modifiés, null pour effacer. */
class TaskChangesTest {
    @Test
    fun seulementLesChampsModifies() {
        assertEquals("""{"title":"Pain"}""", TaskChanges().title("Pain").toJson())
    }

    @Test
    fun nullPourEffacer() {
        assertEquals("""{"due_date":null,"urgency_id":null}""", TaskChanges().dueDate(null).urgencyId(null).toJson())
        assertEquals("""{"due_time":"14:30"}""", TaskChanges().dueTime("14:30").toJson())
        assertEquals("""{"due_time":null}""", TaskChanges().dueTime(null).toJson())
    }

    @Test
    fun valeurs() {
        assertEquals(
            """{"done":true,"due_date":"2026-11-18","tags":["bio","maison"],"group_id":"g2"}""",
            TaskChanges().done(true).dueDate("2026-11-18").tags(listOf("bio", "maison")).groupId("g2").toJson(),
        )
    }

    @Test
    fun texteAvecGuillemets() {
        assertEquals("""{"notes":"Dire \"bonjour\""}""", TaskChanges().notes("Dire \"bonjour\"").toJson())
    }

    @Test
    fun groupeEtPosition() {
        assertEquals("""{"group_id":"g2","position":0}""", TaskChanges().groupId("g2").position(0).toJson())
    }

    @Test
    fun modificationsDUneListe() {
        assertEquals("""{"folder_id":null}""", ListChanges().folderId(null).toJson()) // Hors groupe de listes
        assertEquals("""{"color":"#2F6FEB","icon":"house","default_view":"sections","show_done":true}""",
            ListChanges().color("#2F6FEB").icon("house").defaultView("sections").showDone(true).toJson())
    }
}
