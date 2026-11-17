package io.github.acssio.bettertodo

import io.github.acssio.bettertodo.api.ApiClient
import io.github.acssio.bettertodo.api.CreateTaskRequest
import io.github.acssio.bettertodo.api.Session
import io.github.acssio.bettertodo.api.TodoList
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** Lecture du JSON de l'API (exemples copiés des vraies réponses). */
class ModelsTest {

    @Test
    fun session() {
        val text = """
            {"user": {"id": "u1", "email": "a@b.fr", "display_name": "Alex", "default_view": "columns",
                      "language": "fr", "is_admin": true, "must_change_password": false,
                      "created_at": "2026-11-13T10:00:00Z"},
             "token": "abc", "expires_at": "2026-12-13T10:00:00Z"}
        """
        val session = ApiClient.json.decodeFromString<Session>(text)
        assertEquals("abc", session.token)
        assertEquals("Alex", session.user.displayName)
        assertTrue(session.user.isAdmin)
    }

    @Test
    fun listeHorsGroupe() {
        val text = """
            [{"id": "l1", "folder_id": null, "name": "Courses", "color": "#3FA66B", "icon": "shopping-cart",
              "default_view": "columns", "show_done": false, "position": 0, "open_tasks": 3}]
        """
        val list = ApiClient.json.decodeFromString<List<TodoList>>(text).first()
        assertNull(list.folderId)
        assertEquals(3, list.openTasks)
    }

    @Test
    fun creationSansEcheanceNEnvoiePasNull() {
        assertEquals("""{"title":"Pain"}""", ApiClient.json.encodeToString(CreateTaskRequest("Pain")))
        assertEquals("""{"title":"Pain","due_date":"2026-11-18"}""", ApiClient.json.encodeToString(CreateTaskRequest("Pain", "2026-11-18")))
    }
}
