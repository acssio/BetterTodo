package io.github.acssio.bettertodo

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
}
