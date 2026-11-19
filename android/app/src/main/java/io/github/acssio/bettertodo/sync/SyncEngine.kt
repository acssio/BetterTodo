package io.github.acssio.bettertodo.sync

import android.database.sqlite.SQLiteConstraintException
import android.util.Log
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.room.withTransaction
import io.github.acssio.bettertodo.api.ApiError
import io.github.acssio.bettertodo.api.SyncData
import io.github.acssio.bettertodo.local.FolderEntity
import io.github.acssio.bettertodo.local.GroupEntity
import io.github.acssio.bettertodo.local.ListEntity
import io.github.acssio.bettertodo.local.LocalApi
import io.github.acssio.bettertodo.local.LocalDatabase
import io.github.acssio.bettertodo.local.OutboxEntity
import io.github.acssio.bettertodo.local.SmartListEntity
import io.github.acssio.bettertodo.local.SmartMatch
import io.github.acssio.bettertodo.local.SyncStateEntity
import io.github.acssio.bettertodo.local.TagEntity
import io.github.acssio.bettertodo.local.TaskEntity
import io.github.acssio.bettertodo.local.TaskTagEntity
import io.github.acssio.bettertodo.local.UrgencyEntity
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put

/** Ce que la synchronisation demande au serveur. ApiClient le fait ; un faux serveur dans les tests. */
interface SyncServer {
    /** Envoie une requête de la file d'attente telle quelle. */
    suspend fun replay(method: String, path: String, body: String?)

    /** GET /sync?since= : ce qui a changé depuis le curseur (tout si null). */
    suspend fun sync(since: String?): SyncData
}

/**
 * Synchronisation de la base du téléphone avec un serveur (8-5).
 *
 * L'app lit et écrit TOUJOURS sa base (LocalApi). Quand un serveur est relié, chaque modification
 * est aussi notée dans la file d'attente (outbox). Une synchronisation :
 *
 *   1. envoie la file, dans l'ordre (push). Une requête refusée (400, 404, 409) est abandonnée :
 *      le serveur garde sa version, la récupération suivante la remettra sur le téléphone ;
 *   2. puis, seulement si la file est vide, récupère ce qui a changé sur le serveur (pull,
 *      GET /sync?since=) et l'écrit dans la base, suppressions comprises.
 *
 * Le serveur reste l'arbitre : c'est sa version qu'on récupère (positions renumérotées, dernière
 * modification gagnante champ par champ, puisque chaque PATCH ne contient que ses champs).
 *
 * Premier lien (link) : les listes déjà sur le téléphone sont envoyées au serveur (fusion), puis
 * tout le serveur est récupéré. Niveaux d'urgence et tags de même nom sont fusionnés.
 *
 * Quand : à l'ouverture, ~1,5 s après une modification (schedule), en tirant vers le bas.
 * Il n'y a qu'une synchronisation à la fois (mutex).
 *
 * Les valeurs observées par Compose (syncing, pending, error, version…) sont des mutableStateOf :
 * l'écran Paramètres et l'accueil se mettent à jour tout seuls.
 */
class SyncEngine(private val db: LocalDatabase, private val local: LocalApi, private val scope: CoroutineScope) {
    private val dao = db.dao()
    private val mutex = Mutex()
    private var scheduled: Job? = null

    /** Le serveur relié, ou null (mode sans serveur, ou session expirée). */
    private var server: SyncServer? = null

    /** Synchronisation en cours. */
    var syncing by mutableStateOf(false)
        private set

    /** Modifications pas encore envoyées. */
    var pending by mutableIntStateOf(0)
        private set

    /** Dernière synchronisation réussie (millisecondes), ou null. */
    var lastSync by mutableStateOf<Long?>(null)
        private set

    /** Erreur de la dernière tentative (serveur injoignable…), ou null. */
    var error by mutableStateOf<String?>(null)
        private set

    /** Augmente quand la récupération a changé la base : les écrans se rechargent. */
    var version by mutableIntStateOf(0)
        private set

    /** Appelé sur un 401 (session expirée) : AppViewModel affiche la connexion. */
    var onExpired: (() -> Unit)? = null

    /**
     * Avant de relier un compte : si le téléphone était relié à un AUTRE compte, on repart de zéro
     * (file vidée, premier lien à refaire). Les listes du téléphone, elles, sont gardées.
     */
    suspend fun useAccount(userId: String) {
        mutex.withLock {
            val state = dao.syncState()
            if (state?.userId != userId) {
                dao.clearOutbox()
                dao.saveSyncState(SyncStateEntity(userId = userId))
            }
        }
    }

    /** Relie le serveur : les modifications sont notées, et une synchronisation part tout de suite. */
    fun start(server: SyncServer) {
        connect(server)
        scope.launch {
            dao.syncState()?.let { lastSync = it.lastSync }
            syncNow()
        }
    }

    /** Relie le serveur sans synchroniser tout de suite (start, et les tests qui avancent pas à pas). */
    fun connect(server: SyncServer) {
        this.server = server
        local.recording = true
        local.onRecorded = { schedule() }
    }

    /** Plus de serveur : on ne note plus rien. La base du téléphone est gardée telle quelle. */
    suspend fun stop() {
        scheduled?.cancel()
        server = null
        local.recording = false
        local.onRecorded = null
        mutex.withLock {
            dao.clearOutbox()
            dao.saveSyncState(SyncStateEntity()) // La prochaine connexion refera un premier lien
        }
        pending = 0
        lastSync = null
        error = null
    }

    /** Une modification vient d'être notée : synchronisation dans 1,5 s (les suivantes la repoussent). */
    fun schedule() {
        scheduled?.cancel()
        scheduled = scope.launch {
            pending = dao.outboxCount()
            delay(SCHEDULE_DELAY_MS)
            syncNow()
        }
    }

    /**
     * Synchronise maintenant (et attend la fin). Les erreurs ne remontent pas : elles sont dans error.
     * Premier passage avec ce serveur (linked = false) : premier lien.
     */
    suspend fun syncNow() {
        val server = server ?: return
        mutex.withLock {
            syncing = true
            try {
                if (dao.syncState()?.linked != true) {
                    link(server)
                } else {
                    push(server)
                    pull(server)
                }
                error = null
            } catch (e: ApiError) {
                if (e.status == 401) {
                    this.server = null
                    onExpired?.invoke()
                } else {
                    error = e.message
                }
            } finally {
                pending = dao.outboxCount()
                syncing = false
            }
        }
    }

    // --- 1. Envoi de la file d'attente ---------------------------------------------------

    /** Envoie chaque requête notée, dans l'ordre. S'arrête sur une erreur réseau ou serveur (on réessaiera). */
    private suspend fun push(server: SyncServer) {
        for (op in dao.outbox()) {
            try {
                server.replay(op.method, op.path, op.body)
            } catch (e: ApiError) {
                if (e.status == 401 || e.status == 0 || e.status >= 500) throw e // Réessayé plus tard
                // 400, 404, 409 : le serveur refuse (élément supprimé ailleurs, nom déjà pris…).
                // On abandonne cette requête : la récupération remettra la version du serveur.
                Log.w(TAG, "${op.method} ${op.path} refusée (${e.status}) : ${e.message}")
            }
            dao.deleteOutbox(op.seq)
            pending = dao.outboxCount()
        }
    }

    // --- 2. Récupération -----------------------------------------------------------------

    /** Récupère ce qui a changé depuis le curseur, si tout a été envoyé (sinon on écraserait nos modifications). */
    private suspend fun pull(server: SyncServer) {
        if (dao.outboxCount() > 0) return
        val state = dao.syncState() ?: SyncStateEntity()
        val data = server.sync(state.cursor)
        val changed = db.withTransaction {
            val changed = apply(data, pendingIds(dao.outbox()))
            dao.saveSyncState(state.copy(linked = true, cursor = data.cursor, lastSync = System.currentTimeMillis()))
            changed
        }
        lastSync = System.currentTimeMillis()
        if (changed) version++
    }

    /**
     * Écrit dans la base ce que le serveur a envoyé. Les éléments qui ont encore une modification
     * en attente (skip) ne sont pas écrasés : elle partira au prochain envoi.
     * @return vrai si quelque chose a changé (les écrans doivent se recharger)
     */
    private suspend fun apply(data: SyncData, skip: Set<String>): Boolean {
        val profileBefore = local.me()
        local.saveProfile(data.user)
        var changed = local.me() != profileBefore // Nom, langue, email… changés sur le serveur
        fun keep(id: String) = (id !in skip).also { if (it) changed = true }

        for (level in data.urgencyLevels) {
            if (!keep(level.id)) continue
            val createdAt = dao.urgencyLevel(level.id)?.createdAt ?: now()
            dao.upsertUrgencyLevel(UrgencyEntity(level.id, level.name, level.color, level.rank, createdAt))
        }
        for (tag in data.tags) {
            if (!keep(tag.id)) continue
            upsertTagByName(tag.id, tag.name)
        }
        for (folder in data.folders) {
            if (!keep(folder.id)) continue
            val createdAt = dao.folder(folder.id)?.createdAt ?: now()
            dao.upsertFolder(FolderEntity(folder.id, folder.name, folder.position, folder.collapsed, createdAt))
        }
        for (list in data.lists) {
            if (!keep(list.id)) continue
            val createdAt = dao.list(list.id)?.createdAt ?: now()
            safely("liste ${list.id}") {
                dao.upsertList(ListEntity(list.id, list.folderId, list.name, list.color, list.icon, list.defaultView, list.showDone, list.position, createdAt))
            }
        }
        for (group in data.groups) {
            if (!keep(group.id)) continue
            val createdAt = dao.group(group.id)?.createdAt ?: now()
            safely("groupe ${group.id}") {
                dao.upsertGroup(GroupEntity(group.id, group.listId, group.name, group.position, createdAt))
            }
        }
        for (task in data.tasks) {
            if (!keep(task.id)) continue
            val existing = dao.task(task.id)
            val entity = TaskEntity(
                id = task.id,
                groupId = task.groupId,
                title = task.title,
                notes = task.notes,
                dueDate = task.dueDate,
                dueTime = task.dueTime,
                urgencyId = task.urgencyId,
                done = task.done,
                doneAt = if (task.done) existing?.doneAt ?: now() else null,
                position = task.position,
                createdAt = existing?.createdAt ?: now(),
            )
            safely("tâche ${task.id}") {
                dao.upsertTask(entity)
                dao.clearTaskTags(task.id)
                for (name in task.tags) {
                    dao.tagNamed(name)?.let { dao.insertTaskTag(TaskTagEntity(task.id, it.id)) }
                }
            }
        }
        for (smart in data.smartLists) {
            if (!keep(smart.id)) continue
            val createdAt = dao.smartList(smart.id)?.createdAt ?: now()
            dao.upsertSmartList(
                SmartListEntity(
                    smart.id, smart.name, smart.color, smart.icon, smart.kind, smart.tagMatch,
                    smart.tagIds.joinToString(","), smart.urgencyIds.joinToString(","), smart.visible, smart.position, createdAt,
                )
            )
        }
        for (deleted in data.deleted) {
            if (!keep(deleted.id)) continue
            when (deleted.type) { // Ce qui en dépend part en cascade, comme sur le serveur
                "folder" -> dao.deleteFolder(deleted.id)
                "list" -> dao.deleteList(deleted.id)
                "group" -> dao.deleteGroup(deleted.id)
                "task" -> dao.deleteTask(deleted.id)
                "urgency_level" -> dao.deleteUrgencyLevel(deleted.id)
                "tag" -> dao.deleteTag(deleted.id)
                "smart_list" -> dao.deleteSmartList(deleted.id)
            }
        }
        return changed
    }

    /**
     * Écrit le tag du serveur. Un autre tag du même nom sur le téléphone (créé hors ligne, ou d'avant
     * le premier lien) est fusionné : ses tâches portent désormais celui du serveur.
     */
    private suspend fun upsertTagByName(id: String, name: String) {
        val sameName = dao.tagNamed(name)
        if (sameName != null && sameName.id != id) {
            val taskIds = dao.taskIdsWithTag(sameName.id)
            dao.deleteTag(sameName.id) // Libère le nom (ses liens partent en cascade…)
            dao.upsertTag(TagEntity(id, name))
            taskIds.forEach { dao.insertTaskTag(TaskTagEntity(it, id)) } // …et sont refaits vers le tag du serveur
            replaceInSmartLists(sameName.id, id)
        } else {
            dao.upsertTag(TagEntity(id, name))
        }
    }

    // --- Premier lien ----------------------------------------------------------------

    /**
     * Premier passage avec ce serveur : fusionne le téléphone et le compte.
     *  1. ce qui attendait encore dans la file part d'abord ;
     *  2. niveaux d'urgence et tags du même nom que sur le serveur : on garde ceux du serveur ;
     *  3. tout le reste du téléphone est noté dans la file comme des créations (avec ses id) ;
     *     un élément que le serveur a déjà (même id) est un rejeu : rien n'est doublé ;
     *  4. envoi, puis récupération de TOUT le serveur.
     */
    private suspend fun link(server: SyncServer) {
        push(server)
        val remote = server.sync(null)

        db.withTransaction {
            // Niveaux d'urgence du même nom : les tâches passent sur celui du serveur
            for (level in dao.urgencyLevels()) {
                val twin = remote.urgencyLevels.firstOrNull { it.name == level.name } ?: continue
                if (twin.id == level.id) continue
                dao.upsertUrgencyLevel(UrgencyEntity(twin.id, twin.name, twin.color, twin.rank, level.createdAt))
                dao.replaceUrgency(level.id, twin.id)
                dao.deleteUrgencyLevel(level.id)
                replaceInSmartLists(level.id, twin.id)
            }
            // Tags du même nom : idem
            for (tag in remote.tags) upsertTagByName(tag.id, tag.name)

            val remoteUrgencies = remote.urgencyLevels.map { it.id }.toSet()
            val remoteTags = remote.tags.map { it.id }.toSet()
            snapshot(remoteUrgencies, remoteTags).forEach { dao.insertOutbox(it) }
        }

        push(server)
        val data = server.sync(null) // Tout, maintenant que nos listes y sont
        db.withTransaction {
            apply(data, pendingIds(dao.outbox()))
            val state = dao.syncState() ?: SyncStateEntity()
            dao.saveSyncState(state.copy(userId = data.user.id, linked = true, cursor = data.cursor, lastSync = System.currentTimeMillis()))
        }
        lastSync = System.currentTimeMillis()
        version++
    }

    /**
     * Les requêtes qui recréent tout le téléphone sur le serveur, dans l'ordre des dépendances
     * et dans l'ordre d'affichage (le serveur ajoute chaque élément à la fin).
     */
    private suspend fun snapshot(remoteUrgencies: Set<String>, remoteTags: Set<String>): List<OutboxEntity> {
        val ops = mutableListOf<OutboxEntity>()
        fun op(method: String, path: String, body: JsonObject? = null) {
            ops += OutboxEntity(method = method, path = path, body = body?.toString())
        }

        for (level in dao.urgencyLevels()) {
            if (level.id in remoteUrgencies) continue
            op("POST", "/urgency-levels", buildJsonObject {
                put("id", level.id)
                put("name", level.name)
                put("color", level.color)
            })
        }
        for (tag in dao.allTags()) {
            if (tag.id in remoteTags) continue
            op("POST", "/tags", buildJsonObject {
                put("id", tag.id)
                put("name", tag.name)
            })
        }
        for (folder in dao.folders()) {
            op("POST", "/folders", buildJsonObject {
                put("id", folder.id)
                put("name", folder.name)
            })
            if (folder.collapsed) op("PATCH", "/folders/${folder.id}", buildJsonObject { put("collapsed", true) })
        }
        // Hors groupe d'abord, puis groupe de listes par groupe de listes
        val lists = dao.lists().sortedWith(compareBy({ it.folderId != null }, { it.position }))
        for (list in lists) {
            val groups = dao.groupsOf(list.id)
            op("POST", "/lists", buildJsonObject {
                put("id", list.id)
                groups.firstOrNull()?.let { put("default_group_id", it.id) }
                put("name", list.name)
                put("folder_id", list.folderId)
                put("color", list.color)
                put("icon", list.icon)
                put("default_view", list.defaultView)
            })
            op("PATCH", "/lists/${list.id}", buildJsonObject { put("show_done", list.showDone) })
            groups.forEachIndexed { index, group ->
                if (index == 0) {
                    op("PATCH", "/groups/${group.id}", buildJsonObject { put("name", group.name) }) // Son nom, pas « Tâches »
                } else {
                    op("POST", "/lists/${list.id}/groups", buildJsonObject {
                        put("id", group.id)
                        put("name", group.name)
                    })
                }
                for (task in dao.tasksOf(group.id)) {
                    val tags = dao.tagNamesOf(listOf(task.id)).map { it.name }
                    op("POST", "/groups/${group.id}/tasks", buildJsonObject {
                        put("id", task.id)
                        put("title", task.title)
                        put("notes", task.notes)
                        task.dueDate?.let { put("due_date", it) }
                        task.dueTime?.let { put("due_time", it) }
                        task.urgencyId?.let { put("urgency_id", it) }
                        put("tags", buildJsonArray { tags.forEach { add(JsonPrimitive(it)) } })
                    })
                    if (task.done) op("PATCH", "/tasks/${task.id}", buildJsonObject { put("done", true) })
                }
            }
        }
        for (smart in dao.smartLists()) {
            op("POST", "/smart-lists", buildJsonObject {
                put("id", smart.id)
                put("name", smart.name)
                put("color", smart.color)
                put("icon", smart.icon)
                put("kind", smart.kind)
                put("tag_match", smart.tagMatch)
                put("tag_ids", buildJsonArray { SmartMatch.split(smart.tagIds).forEach { add(JsonPrimitive(it)) } })
                put("urgency_ids", buildJsonArray { SmartMatch.split(smart.urgencyIds).forEach { add(JsonPrimitive(it)) } })
                put("visible", smart.visible)
            })
        }
        return ops
    }

    /** Un tag ou un niveau fusionné : les listes intelligentes qui le citaient citent son remplaçant. */
    private suspend fun replaceInSmartLists(oldId: String, newId: String) {
        for (smart in dao.smartLists()) {
            val tags = SmartMatch.split(smart.tagIds).map { if (it == oldId) newId else it }.distinct()
            val levels = SmartMatch.split(smart.urgencyIds).map { if (it == oldId) newId else it }.distinct()
            dao.updateSmartList(smart.copy(tagIds = tags.joinToString(","), urgencyIds = levels.joinToString(",")))
        }
    }

    /** Une écriture refusée par la base (élément parent absent…) est ignorée : la synchro suivante corrigera. */
    private suspend fun safely(what: String, write: suspend () -> Unit) {
        try {
            write()
        } catch (e: SQLiteConstraintException) {
            Log.w(TAG, "$what ignorée : ${e.message}")
        }
    }

    companion object {
        private const val TAG = "SyncEngine" // Filtre Logcat : tag:SyncEngine
        private const val SCHEDULE_DELAY_MS = 1500L
        private val UUID_PATTERN = Regex("[0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{12}")

        private fun now() = System.currentTimeMillis()

        /**
         * Les id concernés par des requêtes encore en attente : dans le chemin, et "id" dans le corps.
         * Ex. PATCH /tasks/<a> → a ; POST /groups/<g>/tasks {"id": "<t>"} → g et t.
         */
        fun pendingIds(ops: List<OutboxEntity>): Set<String> {
            val ids = mutableSetOf<String>()
            for (op in ops) {
                UUID_PATTERN.findAll(op.path).forEach { ids += it.value }
                op.body?.let { body ->
                    (Json.parseToJsonElement(body) as? JsonObject)?.get("id")?.jsonPrimitive?.content?.let { ids += it }
                }
            }
            return ids
        }
    }
}
