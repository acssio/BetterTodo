package io.github.acssio.bettertodo

import androidx.room.Room
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import io.github.acssio.bettertodo.api.ApiError
import io.github.acssio.bettertodo.api.ListChanges
import io.github.acssio.bettertodo.api.TaskChanges
import io.github.acssio.bettertodo.local.LocalApi
import io.github.acssio.bettertodo.local.LocalDatabase
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

/**
 * LocalApi sur une vraie base SQLite (en mémoire), sur l'émulateur :
 *   ./gradlew connectedDebugAndroidTest
 * Chaque test vérifie une règle du serveur recopiée dans l'app (voir LocalApi).
 */
@RunWith(AndroidJUnit4::class)
class LocalApiTest {
    private lateinit var db: LocalDatabase
    private lateinit var api: LocalApi

    @Before
    fun open() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        db = Room.inMemoryDatabaseBuilder(context, LocalDatabase::class.java).build()
        api = LocalApi(db)
    }

    @After
    fun close() {
        db.close()
    }

    /** L'appel doit échouer avec ce code HTTP. */
    private suspend fun expectError(status: Int, call: suspend () -> Unit) {
        try {
            call()
            fail("Erreur $status attendue")
        } catch (e: ApiError) {
            assertEquals(e.message, status, e.status)
        }
    }

    @Test
    fun compteEtNiveauxDeDepart() = runBlocking {
        assertEquals("Moi", api.me().displayName)
        assertEquals(listOf("Basse" to 0, "Moyenne" to 1, "Haute" to 2), api.urgencyLevels().map { it.name to it.rank })
    }

    @Test
    fun nouvelleListeAvecSonGroupeParDefaut() = runBlocking {
        val list = api.createList("  Courses  ", null)
        assertEquals("Courses", list.name)
        assertEquals(false, list.showDone)
        assertEquals(listOf("Tâches"), api.board(list.id).groups.map { it.name })

        api.updateMe(language = "en", defaultView = "sections")
        val english = api.createList("Work", null)
        assertEquals("sections", english.defaultView)
        assertEquals(listOf("Tasks"), api.board(english.id).groups.map { it.name })
        assertEquals(listOf(0, 1), api.lists().map { it.position })
    }

    @Test
    fun nomsRefuses() = runBlocking {
        expectError(400) { api.createList("   ", null) }
        expectError(400) { api.createFolder("x".repeat(101)) }
        expectError(404) { api.renameFolder("inconnu", "Nom") }
        expectError(400) { api.createList("Liste", "groupe-inconnu") }
    }

    @Test
    fun deplacerDesTaches() = runBlocking {
        val list = api.createList("Liste", null)
        val first = api.board(list.id).groups.first().id
        val second = api.createGroup(list.id, "Autre").id
        val a = api.createTask(first, "A")
        val b = api.createTask(first, "B")
        val c = api.createTask(first, "C")
        assertEquals(listOf(0, 1, 2), listOf(a, b, c).map { it.position })

        api.updateTask(a.id, TaskChanges().position(2))
        assertEquals(listOf("B", "C", "A"), titles(list.id, first))

        // Vers un autre groupe, en tête
        val moved = api.updateTask(b.id, TaskChanges().groupId(second).position(0))
        assertEquals(second, moved.groupId)
        assertEquals(listOf("B"), titles(list.id, second))

        // Vers un autre groupe sans position : à la fin
        api.updateTask(c.id, TaskChanges().groupId(second))
        assertEquals(listOf("B", "C"), titles(list.id, second))
        expectError(400) { api.updateTask(a.id, TaskChanges().groupId("inconnu")) }
    }

    private suspend fun titles(listId: String, groupId: String) =
        api.board(listId).groups.first { it.id == groupId }.tasks.map { it.title }

    @Test
    fun pasDHeureSansDate() = runBlocking {
        val group = api.board(api.createList("Liste", null).id).groups.first().id
        val task = api.createTask(group, "Tâche")
        expectError(400) { api.updateTask(task.id, TaskChanges().dueTime("14:30")) }

        api.updateTask(task.id, TaskChanges().dueDate("2026-11-19").dueTime("14:30"))
        expectError(400) { api.updateTask(task.id, TaskChanges().dueTime("25:00")) }
        assertEquals("14:30", api.board(api.lists().first().id).groups.first().tasks.first().dueTime)

        val noDate = api.updateTask(task.id, TaskChanges().dueDate(null)) // Plus d'échéance : plus d'heure
        assertNull(noDate.dueDate)
        assertNull(noDate.dueTime)
    }

    @Test
    fun cocherEtVider() = runBlocking {
        val list = api.createList("Liste", null)
        val group = api.board(list.id).groups.first().id
        val task = api.createTask(group, "Faite")
        api.createTask(group, "À faire")
        assertEquals(2, api.lists().first().openTasks)

        api.updateTask(task.id, TaskChanges().done(true))
        assertEquals(1, api.lists().first().openTasks)

        api.clearDone(list.id)
        assertEquals(listOf("À faire"), titles(list.id, group))
    }

    @Test
    fun tags() = runBlocking {
        val group = api.board(api.createList("Liste", null).id).groups.first().id
        val task = api.createTask(group, "Tâche")
        val tagged = api.updateTask(task.id, TaskChanges().tags(listOf("maison", "#bio", "maison")))
        assertEquals(listOf("bio", "maison"), tagged.tags)
        assertEquals(listOf("bio" to 1, "maison" to 1), api.tags().map { it.name to it.taskCount })

        expectError(409) { api.createTag("bio") }
        val bio = api.tags().first { it.name == "bio" }
        api.renameTag(bio.id, "Bio")
        expectError(409) { api.renameTag(bio.id, "maison") }

        api.deleteTag(bio.id) // Enlevé de la tâche
        assertEquals(listOf("maison"), api.board(api.lists().first().id).groups.first().tasks.first().tags)
    }

    @Test
    fun suppressionsEnCascade() = runBlocking {
        val folder = api.createFolder("Perso")
        val list = api.createList("Liste", folder.id)
        val group = api.board(list.id).groups.first().id
        val task = api.createTask(group, "Tâche", dueDate = "2026-11-19")
        val haute = api.urgencyLevels().last()
        api.updateTask(task.id, TaskChanges().urgencyId(haute.id))

        // Niveau supprimé : la tâche passe sans urgence
        api.deleteUrgencyLevel(haute.id)
        assertNull(api.board(list.id).groups.first().tasks.first().urgencyId)

        // Groupe de listes supprimé : la liste reste, hors groupe
        api.deleteFolder(folder.id)
        assertNull(api.lists().single().folderId)

        // Liste supprimée : ses groupes et ses tâches avec
        api.deleteList(list.id)
        assertEquals(0, api.dueCounts("2026-11-19").scheduled)
        expectError(404) { api.updateTask(task.id, TaskChanges().title("x")) }
    }

    @Test
    fun aujourdHuiEtPlanifie() = runBlocking {
        val group = api.board(api.createList("Liste", null).id).groups.first().id
        api.createTask(group, "Sans heure", dueDate = "2026-11-19")
        val tardive = api.createTask(group, "14:30", dueDate = "2026-11-19")
        api.updateTask(tardive.id, TaskChanges().dueTime("14:30"))
        val matin = api.createTask(group, "09:00", dueDate = "2026-11-19")
        api.updateTask(matin.id, TaskChanges().dueTime("09:00"))
        api.createTask(group, "En retard", dueDate = "2026-11-10")
        api.createTask(group, "Demain", dueDate = "2026-11-20")
        val faite = api.createTask(group, "Faite", dueDate = "2026-11-19")
        api.updateTask(faite.id, TaskChanges().done(true))
        api.createTask(group, "Sans échéance")

        assertEquals(
            listOf("En retard", "09:00", "14:30", "Sans heure"),
            api.dueTasks(until = "2026-11-19").map { it.task.title },
        )
        assertEquals(5, api.dueTasks(until = null).size)
        assertEquals("Liste", api.dueTasks(null).first().list.name)
        val counts = api.dueCounts("2026-11-19")
        assertEquals(4, counts.today)
        assertEquals(5, counts.scheduled)
    }

    @Test
    fun niveauxDUrgence() = runBlocking {
        val basse = api.urgencyLevels().first()
        api.updateUrgencyLevel(basse.id, rank = 2)
        assertEquals(listOf("Moyenne", "Haute", "Basse"), api.urgencyLevels().map { it.name })
        val critique = api.createUrgencyLevel("Critique", "#2F6FEB")
        assertEquals(3, critique.rank)
        expectError(400) { api.createUrgencyLevel("Rouge", "rouge") }
    }

    @Test
    fun listes() = runBlocking {
        val list = api.createList("Liste", null)
        val changed = api.updateList(list.id, ListChanges().name("Courses").color("#3FA66B").icon("shopping-cart").showDone(true))
        assertEquals(listOf("Courses", "#3FA66B", "shopping-cart"), listOf(changed.name, changed.color, changed.icon))
        assertTrue(changed.showDone)
        expectError(400) { api.updateList(list.id, ListChanges().icon("Pas Valide")) }

        val folder = api.createFolder("Perso")
        assertEquals(folder.id, api.updateList(list.id, ListChanges().folderId(folder.id)).folderId)
        assertTrue(api.setFolderCollapsed(folder.id, true).collapsed)
    }
}
