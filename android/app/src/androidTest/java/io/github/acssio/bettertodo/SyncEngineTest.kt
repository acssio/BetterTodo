package io.github.acssio.bettertodo

import androidx.room.Room
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import io.github.acssio.bettertodo.api.ApiError
import io.github.acssio.bettertodo.api.Folder
import io.github.acssio.bettertodo.api.SyncData
import io.github.acssio.bettertodo.api.SyncDeletion
import io.github.acssio.bettertodo.api.SyncGroup
import io.github.acssio.bettertodo.api.Tag
import io.github.acssio.bettertodo.api.Task
import io.github.acssio.bettertodo.api.TaskChanges
import io.github.acssio.bettertodo.api.TodoList
import io.github.acssio.bettertodo.api.UrgencyLevel
import io.github.acssio.bettertodo.api.User
import io.github.acssio.bettertodo.local.LocalApi
import io.github.acssio.bettertodo.local.LocalDatabase
import io.github.acssio.bettertodo.sync.SyncEngine
import io.github.acssio.bettertodo.sync.SyncServer
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

/**
 * SyncEngine sur une vraie base (en mémoire), avec un faux serveur qui note ce qu'il reçoit
 * et répond ce qu'on lui dit. Lancé par ./gradlew connectedDebugAndroidTest.
 */
@RunWith(AndroidJUnit4::class)
class SyncEngineTest {
    private lateinit var db: LocalDatabase
    private lateinit var local: LocalApi
    private lateinit var engine: SyncEngine
    private val user = User(id = "u1", email = "moi@example.test", displayName = "Serveur") // Avant server, qui l'utilise
    private val server = FakeServer()

    /** Faux serveur : garde les requêtes reçues ; refuse celles de refuse ; renvoie next à /sync. */
    inner class FakeServer : SyncServer {
        val received = mutableListOf<String>()           // "POST /folders", …
        val bodies = mutableListOf<String?>()
        var refuse = mutableMapOf<String, Int>()          // "PATCH /tasks/x" → code d'erreur
        var next = SyncData(cursor = "c0", user = user)   // Réponse de GET /sync
        val sinces = mutableListOf<String?>()

        override suspend fun replay(method: String, path: String, body: String?) {
            refuse["$method $path"]?.let { throw ApiError(it, "refusée") }
            received += "$method $path"
            bodies += body
        }

        override suspend fun sync(since: String?): SyncData {
            sinces += since
            return next
        }
    }

    @Before
    fun open() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        db = Room.inMemoryDatabaseBuilder(context, LocalDatabase::class.java).build()
        local = LocalApi(db)
        engine = SyncEngine(db, local, CoroutineScope(Job()))
    }

    @After
    fun close() {
        db.close()
    }

    /** Relie le faux serveur et fait le premier lien (base locale déjà remplie par le test). */
    private suspend fun linked() {
        engine.useAccount(user.id)
        engine.connect(server)
        engine.syncNow()
        server.received.clear()
        server.bodies.clear()
    }

    @Test
    fun sansServeurRienNEstNote() = runBlocking {
        val list = local.createList("Liste", null)
        local.renameGroup(local.board(list.id).groups.first().id, "Autre")
        assertEquals(0, db.dao().outboxCount())
    }

    @Test
    fun chaqueModificationEstEnvoyeeDansLOrdre() = runBlocking {
        linked()
        val list = local.createList("Courses", null)
        val group = local.board(list.id).groups.first()
        val task = local.createTask(group.id, "Pain")
        local.updateTask(task.id, TaskChanges().tags(listOf("bio")).done(true))
        local.deleteTask(task.id)
        engine.syncNow()

        assertEquals(
            listOf(
                "POST /lists",
                "POST /groups/${group.id}/tasks",
                "POST /tags",              // Le tag créé par la tâche part avec son id, avant la tâche
                "PATCH /tasks/${task.id}",
                "DELETE /tasks/${task.id}",
            ),
            server.received,
        )
        // La liste part avec son id et celui de son groupe par défaut
        assertTrue(server.bodies[0]!!.contains(list.id))
        assertTrue(server.bodies[0]!!.contains("\"default_group_id\":\"${group.id}\""))
        assertEquals(0, engine.pending)
    }

    @Test
    fun requeteRefuseeAbandonneeMaisReseauCoupeOnReessaie() = runBlocking {
        linked()
        val list = local.createList("Liste", null)
        local.updateList(list.id, io.github.acssio.bettertodo.api.ListChanges().name("Nouveau nom"))

        // Réseau coupé : rien n'est perdu
        server.refuse["POST /lists"] = 0
        engine.syncNow()
        assertEquals(2, engine.pending)
        assertTrue(engine.error != null)

        // Le serveur refuse le PATCH (404) : il est abandonné, la suite part
        server.refuse.clear()
        server.refuse["PATCH /lists/${list.id}"] = 404
        local.createFolder("Après")
        engine.syncNow()
        assertEquals(listOf("POST /lists", "POST /folders"), server.received)
        assertEquals(0, engine.pending)
        assertNull(engine.error)
    }

    @Test
    fun recuperationEcritLaBase() = runBlocking {
        linked()
        server.next = SyncData(
            cursor = "c1",
            user = user.copy(displayName = "Nouveau nom", language = "en"),
            folders = listOf(Folder("f0000000-0000-0000-0000-000000000001", "Perso", 0)),
            lists = listOf(TodoList("l0000000-0000-0000-0000-000000000001", "f0000000-0000-0000-0000-000000000001", "Du site", "#2F6FEB", "list", 0)),
            groups = listOf(SyncGroup("g0000000-0000-0000-0000-000000000001", "l0000000-0000-0000-0000-000000000001", "À faire", 0)),
            tasks = listOf(
                Task("t0000000-0000-0000-0000-000000000001", "g0000000-0000-0000-0000-000000000001", "Écrite sur le site", dueDate = "2026-11-19", position = 0, tags = listOf("web")),
            ),
            tags = listOf(Tag("a0000000-0000-0000-0000-000000000001", "web")),
        )
        engine.syncNow()

        assertEquals("Nouveau nom", local.me().displayName)
        assertEquals(listOf("Du site"), local.lists().map { it.name })
        val board = local.board("l0000000-0000-0000-0000-000000000001")
        assertEquals(listOf("Écrite sur le site"), board.groups.single().tasks.map { it.title })
        assertEquals(listOf("web"), board.groups.single().tasks.single().tags)
        assertEquals("c1", db.dao().syncState()?.cursor)

        // Puis la liste est supprimée sur le site : partie aussi sur le téléphone, avec ses tâches
        server.next = SyncData(cursor = "c2", user = user, deleted = listOf(SyncDeletion("list", "l0000000-0000-0000-0000-000000000001")))
        engine.syncNow()
        assertEquals(emptyList<String>(), local.lists().map { it.name })
        assertEquals(0, local.dueCounts("2026-11-19").scheduled)
        assertEquals("c1", server.sinces.last()) // On a bien demandé « depuis c1 »
    }

    @Test
    fun tagDuMemeNomFusionne() = runBlocking {
        linked()
        val group = local.board(local.createList("Liste", null).id).groups.first().id
        val task = local.createTask(group, "Tâche")
        local.updateTask(task.id, TaskChanges().tags(listOf("maison")))
        engine.syncNow()

        // Le serveur avait déjà un tag « maison », avec un autre id
        server.next = SyncData(cursor = "c1", user = user, tags = listOf(Tag("a0000000-0000-0000-0000-000000000009", "maison")))
        engine.syncNow()
        assertEquals(listOf("a0000000-0000-0000-0000-000000000009"), local.tags().map { it.id })
        assertEquals(listOf("maison"), local.board(local.lists().single().id).groups.single().tasks.single().tags)
    }

    @Test
    fun premierLienFusionneEtEnvoieLeTelephone() = runBlocking {
        // Sur le téléphone, sans serveur : une liste, une tâche « Haute » taguée
        val list = local.createList("Courses", null)
        val group = local.board(list.id).groups.first().id
        val task = local.createTask(group, "Pain", dueDate = "2026-11-19")
        val haute = local.urgencyLevels().last()
        local.updateTask(task.id, TaskChanges().urgencyId(haute.id).tags(listOf("bio")))
        local.updateTask(task.id, TaskChanges().done(true))
        assertEquals(0, db.dao().outboxCount())

        // Le compte du serveur a déjà ses 3 niveaux (autres id) et une liste
        val serverLevels = listOf(
            UrgencyLevel("e0000000-0000-0000-0000-000000000001", "Basse", "#3FA66B", 0),
            UrgencyLevel("e0000000-0000-0000-0000-000000000002", "Moyenne", "#E8A33D", 1),
            UrgencyLevel("e0000000-0000-0000-0000-000000000003", "Haute", "#E5484D", 2),
        )
        server.next = SyncData(cursor = "c0", user = user, urgencyLevels = serverLevels)
        engine.useAccount(user.id)
        engine.connect(server)
        engine.syncNow()

        // Les niveaux du même nom sont ceux du serveur : rien à créer, la tâche pointe sur « Haute » du serveur
        assertTrue(server.received.none { it == "POST /urgency-levels" })
        assertEquals(serverLevels.map { it.id }, local.urgencyLevels().map { it.id })
        val sent = server.received
        assertEquals("POST /tags", sent[0])
        assertEquals("POST /lists", sent[1])
        assertTrue("POST /groups/$group/tasks" in sent)
        assertTrue("PATCH /tasks/${task.id}" in sent) // done
        val taskBody = server.bodies[sent.indexOf("POST /groups/$group/tasks")]!!
        assertTrue(taskBody.contains("e0000000-0000-0000-0000-000000000003"))
        assertTrue(taskBody.contains("\"tags\":[\"bio\"]"))
        assertEquals(true, db.dao().syncState()?.linked)
        assertEquals(listOf(null, null), server.sinces) // Deux synchros complètes : avant et après l'envoi
    }

    @Test
    fun autreCompteOnRepartDeZero() = runBlocking {
        linked()
        local.createFolder("Pour u1")
        engine.useAccount("u2")
        assertEquals(0, db.dao().outboxCount())
        assertEquals(false, db.dao().syncState()?.linked)
    }
}
