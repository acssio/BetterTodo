package io.github.acssio.bettertodo.local

import androidx.room.withTransaction
import io.github.acssio.bettertodo.api.ApiError
import io.github.acssio.bettertodo.api.Board
import io.github.acssio.bettertodo.api.BoardGroup
import io.github.acssio.bettertodo.api.DueCounts
import io.github.acssio.bettertodo.api.DueTask
import io.github.acssio.bettertodo.api.Folder
import io.github.acssio.bettertodo.api.ListChanges
import io.github.acssio.bettertodo.api.ListRef
import io.github.acssio.bettertodo.api.SmartList
import io.github.acssio.bettertodo.api.SmartListChanges
import io.github.acssio.bettertodo.api.Tag
import io.github.acssio.bettertodo.api.Task
import io.github.acssio.bettertodo.api.TaskChanges
import io.github.acssio.bettertodo.api.TodoApi
import io.github.acssio.bettertodo.api.TodoList
import io.github.acssio.bettertodo.api.UrgencyLevel
import io.github.acssio.bettertodo.api.User
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.boolean
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import kotlinx.serialization.json.int
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import java.time.LocalDate
import java.time.LocalTime
import java.util.UUID

/**
 * Les données dans la base du téléphone, sans serveur (8-3).
 *
 * Même comportement que l'API Go, route par route (mêmes règles, mêmes messages d'erreur) :
 * les écrans ne voient pas la différence (voir TodoApi). Ce qui est recopié du serveur :
 *  - noms nettoyés (espaces autour) et limités à 100 caractères, titre à 500, notes à 10 000, tag à 50 ;
 *  - ordre : chaque nouvel élément va à la fin, un déplacement renumérote 0, 1, 2… (Ordering.move) ;
 *  - une nouvelle liste reçoit un groupe par défaut nommé selon la langue (« Tâches » / « Tasks ») ;
 *  - pas d'heure sans date ; « due_date: null » enlève aussi l'heure ;
 *  - tags d'une tâche donnés par leur nom, créés s'ils n'existent pas ;
 *  - 3 niveaux d'urgence au départ (Basse, Moyenne, Haute), comme un nouveau compte.
 *
 * Plusieurs écritures liées passent par withTransaction : tout ou rien, comme les transactions du serveur.
 *
 * Synchronisation (8-5) : quand recording est vrai (un serveur est relié), chaque modification est
 * AUSSI notée dans la table outbox, sous la forme de la requête REST que le serveur doit recevoir
 * (avec les id choisis ici). Dans la même transaction que la modification : jamais l'une sans l'autre.
 * SyncEngine envoie ensuite la file au serveur, dans l'ordre.
 */
class LocalApi(private val db: LocalDatabase) : TodoApi {
    private val dao = db.dao()

    /** Vrai quand un serveur est relié : chaque modification est aussi notée dans outbox. */
    @Volatile
    var recording = false

    /** Appelé après chaque modification notée (SyncEngine lance alors une synchronisation un peu plus tard). */
    @Volatile
    var onRecorded: (() -> Unit)? = null

    /** Note la requête que le serveur devra recevoir (seulement si un serveur est relié). */
    private suspend fun record(method: String, path: String, body: JsonObject? = null) {
        if (!recording) return
        dao.insertOutbox(OutboxEntity(method = method, path = path, body = body?.toString()))
        onRecorded?.invoke()
    }

    private suspend fun record(method: String, path: String, body: String) {
        if (!recording) return
        dao.insertOutbox(OutboxEntity(method = method, path = path, body = body))
        onRecorded?.invoke()
    }

    // --- Compte local ----------------------------------------------------------------

    override suspend fun me(): User = profile().toUser()

    override suspend fun updateMe(
        displayName: String?,
        language: String?,
        defaultView: String?,
        quickTaskDue: String?,
        showToday: Boolean?,
        showScheduled: Boolean?,
    ): User {
        var profile = profile()
        if (displayName != null) profile = profile.copy(displayName = cleanName(displayName, "Le nom affiché"))
        if (language != null) {
            if (language !in DEFAULT_GROUP_NAME) throw badRequest("language doit valoir fr ou en")
            profile = profile.copy(language = language)
        }
        if (defaultView != null) profile = profile.copy(defaultView = checkView(defaultView))
        if (quickTaskDue != null) {
            if (quickTaskDue !in listOf(User.QUICK_DUE_NONE, User.QUICK_DUE_TODAY, User.QUICK_DUE_TOMORROW)) {
                throw badRequest("quick_task_due doit valoir none, today ou tomorrow")
            }
            profile = profile.copy(quickTaskDue = quickTaskDue)
        }
        if (showToday != null) profile = profile.copy(showToday = showToday)
        if (showScheduled != null) profile = profile.copy(showScheduled = showScheduled)
        db.withTransaction {
            dao.updateProfile(profile)
            record("PATCH", "/me", buildJsonObject {
                displayName?.let { put("display_name", profile.displayName) }
                language?.let { put("language", it) }
                defaultView?.let { put("default_view", it) }
                quickTaskDue?.let { put("quick_task_due", it) }
                showToday?.let { put("show_today", it) }
                showScheduled?.let { put("show_scheduled", it) }
            })
        }
        return profile.toUser()
    }

    /** Le compte reçu du serveur à la synchronisation : rien à renvoyer (SyncEngine). */
    suspend fun saveProfile(user: User) {
        val profile = profile()
        dao.updateProfile(
            profile.copy(
                displayName = user.displayName,
                language = user.language,
                defaultView = user.defaultView,
                quickTaskDue = user.quickTaskDue,
                email = user.email,
                showToday = user.showToday,
                showScheduled = user.showScheduled,
            )
        )
    }

    /**
     * Le compte local. Au tout premier appel (base vide) : on le crée, avec les 3 niveaux
     * d'urgence de départ, comme le serveur à la création d'un compte.
     */
    private suspend fun profile(): ProfileEntity {
        dao.profile()?.let { return it }
        return db.withTransaction {
            dao.profile() ?: ProfileEntity().also { profile ->
                dao.insertProfile(profile)
                DEFAULT_URGENCY_LEVELS.forEachIndexed { rank, (name, color) ->
                    dao.insertUrgencyLevel(UrgencyEntity(newId(), name, color, rank, now()))
                }
            }
        }
    }

    private fun ProfileEntity.toUser() = User(
        id = LOCAL_USER_ID,
        email = email,
        displayName = displayName,
        language = language,
        defaultView = defaultView,
        quickTaskDue = quickTaskDue,
        showToday = showToday,
        showScheduled = showScheduled,
    )

    // --- Groupes de listes -----------------------------------------------------------

    override suspend fun folders(): List<Folder> = dao.folders().map { it.toFolder() }

    override suspend fun createFolder(name: String): Folder {
        val folder = FolderEntity(newId(), cleanName(name, "Le nom"), nextPosition(dao.folders().map { it.position }), createdAt = now())
        db.withTransaction {
            dao.insertFolder(folder)
            record("POST", "/folders", buildJsonObject {
                put("id", folder.id)
                put("name", folder.name)
            })
        }
        return folder.toFolder()
    }

    override suspend fun renameFolder(folderId: String, name: String): Folder {
        val folder = dao.folder(folderId) ?: throw notFound()
        val renamed = folder.copy(name = cleanName(name, "Le nom"))
        db.withTransaction {
            dao.updateFolder(renamed)
            record("PATCH", "/folders/$folderId", buildJsonObject { put("name", renamed.name) })
        }
        return renamed.toFolder()
    }

    override suspend fun setFolderCollapsed(folderId: String, collapsed: Boolean): Folder {
        val folder = dao.folder(folderId) ?: throw notFound()
        val changed = folder.copy(collapsed = collapsed)
        db.withTransaction {
            dao.updateFolder(changed)
            record("PATCH", "/folders/$folderId", buildJsonObject { put("collapsed", collapsed) })
        }
        return changed.toFolder()
    }

    override suspend fun deleteFolder(folderId: String) {
        dao.folder(folderId) ?: throw notFound()
        db.withTransaction {
            dao.deleteFolder(folderId) // Ses listes passent hors groupe (SET_NULL)
            record("DELETE", "/folders/$folderId")
        }
    }

    private fun FolderEntity.toFolder() = Folder(id, name, position, collapsed)

    // --- Listes -----------------------------------------------------------------------

    override suspend fun lists(): List<TodoList> = dao.lists().map { it.toTodoList() }

    override suspend fun createList(name: String, folderId: String?): TodoList {
        val cleanName = cleanName(name, "Le nom")
        if (folderId != null && dao.folder(folderId) == null) throw badRequest("Groupe de listes inconnu")
        val profile = profile()
        val list = ListEntity(
            id = newId(),
            folderId = folderId,
            name = cleanName,
            color = DEFAULT_LIST_COLOR,
            icon = DEFAULT_LIST_ICON,
            defaultView = profile.defaultView,
            showDone = false, // Tâches faites masquées par défaut (migration 008)
            position = nextPosition(dao.listsIn(folderId).map { it.position }),
            createdAt = now(),
        )
        val group = GroupEntity(newId(), list.id, DEFAULT_GROUP_NAME.getValue(profile.language), 0, now())
        db.withTransaction {
            dao.insertList(list)
            dao.insertGroup(group)
            // Le serveur crée lui aussi le groupe par défaut : on lui donne son id (default_group_id)
            record("POST", "/lists", buildJsonObject {
                put("id", list.id)
                put("default_group_id", group.id)
                put("name", list.name)
                put("folder_id", folderId)
            })
        }
        return list.toTodoList()
    }

    /** PATCH /lists/{id} : on lit les champs envoyés dans changes, comme le serveur. */
    override suspend fun updateList(listId: String, changes: ListChanges): TodoList {
        val fields = parse(changes.toJson())
        var list = dao.list(listId) ?: throw notFound()
        for (key in listOf("color", "icon", "default_view", "show_done", "position")) {
            if (fields.isNull(key)) throw badRequest("color, icon, default_view, show_done et position ne peuvent pas être null")
        }
        fields.string("name")?.let { list = list.copy(name = cleanName(it, "Le nom")) }
        fields.string("color")?.let { list = list.copy(color = checkColor(it)) }
        fields.string("icon")?.let {
            if (!ICON_PATTERN.matches(it)) throw badRequest("Nom d'icône invalide (lettres minuscules, chiffres et tirets)")
            list = list.copy(icon = it)
        }
        fields.string("default_view")?.let { list = list.copy(defaultView = checkView(it)) }
        if ("show_done" in fields) list = list.copy(showDone = fields.getValue("show_done").jsonPrimitive.boolean)
        val folderChanged = "folder_id" in fields
        if (folderChanged) {
            val folderId = fields.string("folder_id")
            if (folderId != null && dao.folder(folderId) == null) throw badRequest("Groupe de listes inconnu")
            list = list.copy(folderId = folderId)
        }

        db.withTransaction {
            dao.updateList(list)
            if (folderChanged || "position" in fields) {
                val position = fields.int("position") ?: Ordering.END
                val ids = Ordering.move(dao.listsIn(list.folderId).map { it.id }, list.id, position)
                ids.forEachIndexed { index, id -> dao.setListPosition(id, index) }
            }
            record("PATCH", "/lists/$listId", changes.toJson())
        }
        return (dao.list(listId) ?: throw notFound()).toTodoList()
    }

    override suspend fun deleteList(listId: String) {
        dao.list(listId) ?: throw notFound()
        db.withTransaction {
            dao.deleteList(listId) // Groupes et tâches supprimés en cascade
            record("DELETE", "/lists/$listId")
        }
    }

    override suspend fun clearDone(listId: String) {
        dao.list(listId) ?: throw notFound()
        db.withTransaction {
            dao.deleteDoneTasks(listId)
            record("DELETE", "/lists/$listId/done-tasks")
        }
    }

    private suspend fun ListEntity.toTodoList() =
        TodoList(id, folderId, name, color, icon, position, dao.openTasks(id), defaultView, showDone)

    // --- Groupes de tâches -------------------------------------------------------------

    override suspend fun createGroup(listId: String, name: String): BoardGroup {
        dao.list(listId) ?: throw notFound()
        val group = GroupEntity(newId(), listId, cleanName(name, "Le nom"), nextPosition(dao.groupsOf(listId).map { it.position }), now())
        db.withTransaction {
            dao.insertGroup(group)
            record("POST", "/lists/$listId/groups", buildJsonObject {
                put("id", group.id)
                put("name", group.name)
            })
        }
        return BoardGroup(group.id, group.name, group.position)
    }

    override suspend fun renameGroup(groupId: String, name: String) {
        val group = dao.group(groupId) ?: throw notFound()
        val renamed = group.copy(name = cleanName(name, "Le nom"))
        db.withTransaction {
            dao.updateGroup(renamed)
            record("PATCH", "/groups/$groupId", buildJsonObject { put("name", renamed.name) })
        }
    }

    override suspend fun deleteGroup(groupId: String) {
        dao.group(groupId) ?: throw notFound()
        db.withTransaction {
            dao.deleteGroup(groupId) // Ses tâches avec (CASCADE)
            record("DELETE", "/groups/$groupId")
        }
    }

    override suspend fun board(listId: String): Board {
        val list = dao.list(listId) ?: throw notFound()
        val tasks = toTasks(dao.tasksOfList(listId)).groupBy { it.groupId }
        val groups = dao.groupsOf(listId).map { BoardGroup(it.id, it.name, it.position, tasks[it.id] ?: emptyList()) }
        return Board(list.toTodoList(), groups)
    }

    // --- Tâches -----------------------------------------------------------------------

    override suspend fun createTask(groupId: String, title: String, dueDate: String?): Task {
        dao.group(groupId) ?: throw notFound()
        if (dueDate != null) checkDate(dueDate)
        val task = TaskEntity(
            id = newId(),
            groupId = groupId,
            title = cleanTitle(title),
            dueDate = dueDate,
            position = nextPosition(dao.tasksOf(groupId).map { it.position }),
            createdAt = now(),
        )
        db.withTransaction {
            dao.insertTask(task)
            record("POST", "/groups/$groupId/tasks", buildJsonObject {
                put("id", task.id)
                put("title", task.title)
                if (dueDate != null) put("due_date", dueDate)
            })
        }
        return toTask(task)
    }

    /** PATCH /tasks/{id} : on lit les champs envoyés dans changes (absent / null / valeur), comme le serveur. */
    override suspend fun updateTask(taskId: String, changes: TaskChanges): Task {
        val fields = parse(changes.toJson())
        var task = dao.task(taskId) ?: throw notFound()
        for (key in listOf("title", "notes", "done", "group_id", "position")) {
            if (fields.isNull(key)) throw badRequest("title, notes, done, group_id et position ne peuvent pas être null")
        }

        fields.string("title")?.let { task = task.copy(title = cleanTitle(it)) }
        fields.string("notes")?.let {
            if (it.codePointCount(0, it.length) > MAX_NOTES) throw badRequest("Les notes sont trop longues (10 000 caractères maximum)")
            task = task.copy(notes = it)
        }
        if ("due_date" in fields) {
            val date = fields.string("due_date")?.let { checkDate(it) }
            task = task.copy(dueDate = date)
            if (date == null && "due_time" !in fields) task = task.copy(dueTime = null) // Plus d'échéance : plus d'heure
        }
        if ("due_time" in fields) {
            val time = fields.string("due_time")?.let { checkTime(it) }
            if (time != null && task.dueDate == null) throw badRequest("due_time demande une due_date")
            task = task.copy(dueTime = time)
        }
        if ("urgency_id" in fields) {
            val urgencyId = fields.string("urgency_id")
            if (urgencyId != null && dao.urgencyLevel(urgencyId) == null) throw badRequest("Niveau d'urgence inconnu")
            task = task.copy(urgencyId = urgencyId)
        }
        if ("done" in fields) {
            val done = fields.getValue("done").jsonPrimitive.boolean
            task = task.copy(done = done, doneAt = if (done) task.doneAt ?: now() else null)
        }
        fields.string("group_id")?.let {
            dao.group(it) ?: throw badRequest("Groupe inconnu")
            task = task.copy(groupId = it)
        }
        val tags = if ("tags" in fields) normalizeTags(fields.getValue("tags").jsonArray.map { it.jsonPrimitive.content }) else null

        db.withTransaction {
            dao.updateTask(task)
            if (tags != null) setTaskTags(task.id, tags)
            if ("position" in fields || "group_id" in fields) {
                val position = fields.int("position") ?: Ordering.END
                val ids = Ordering.move(dao.tasksOf(task.groupId).map { it.id }, task.id, position)
                ids.forEachIndexed { index, id -> dao.setTaskPosition(id, index) }
            }
            record("PATCH", "/tasks/$taskId", changes.toJson())
        }
        return toTask(dao.task(taskId) ?: throw notFound())
    }

    override suspend fun deleteTask(taskId: String) {
        dao.task(taskId) ?: throw notFound()
        db.withTransaction {
            dao.deleteTask(taskId)
            record("DELETE", "/tasks/$taskId")
        }
    }

    override suspend fun dueTasks(until: String?): List<DueTask> {
        if (until != null) checkDate(until)
        val tasks = toTasks(dao.dueTasks(until))
        val groups = mutableMapOf<String, GroupEntity?>()
        val lists = mutableMapOf<String, ListEntity?>()
        return tasks.mapNotNull { task ->
            val group = groups.getOrPut(task.groupId) { dao.group(task.groupId) } ?: return@mapNotNull null
            val list = lists.getOrPut(group.listId) { dao.list(group.listId) } ?: return@mapNotNull null
            DueTask(task, ListRef(list.id, list.name, list.color, list.icon), group.name)
        }
    }

    override suspend fun dueCounts(today: String): DueCounts {
        checkDate(today)
        return DueCounts(today = dao.countDueToday(today), scheduled = dao.countScheduled())
    }

    /** Les tâches de la base → celles des écrans, avec les noms de leurs tags (triés). */
    private suspend fun toTasks(entities: List<TaskEntity>): List<Task> {
        if (entities.isEmpty()) return emptyList()
        val tags = dao.tagNamesOf(entities.map { it.id }).groupBy({ it.taskId }, { it.name })
        return entities.map { it.toTask(tags[it.id] ?: emptyList()) }
    }

    private suspend fun toTask(entity: TaskEntity): Task = toTasks(listOf(entity)).first()

    private fun TaskEntity.toTask(tags: List<String>) =
        Task(id, groupId, title, notes, dueDate, dueTime, urgencyId, done, position, tags)

    /**
     * Remplace tous les tags de la tâche ; un nom inconnu crée le tag (comme setTaskTags du serveur).
     * Un tag créé ici est d'abord envoyé au serveur avec son id (POST /tags), pour garder le même des deux côtés.
     */
    private suspend fun setTaskTags(taskId: String, names: List<String>) {
        dao.clearTaskTags(taskId)
        for (name in names) {
            val tag = dao.tagNamed(name) ?: TagEntity(newId(), name).also { created ->
                dao.insertTag(created)
                record("POST", "/tags", buildJsonObject {
                    put("id", created.id)
                    put("name", created.name)
                })
            }
            dao.insertTaskTag(TaskTagEntity(taskId, tag.id))
        }
    }

    // --- Listes intelligentes personnalisées (8-6) ----------------------------------------

    override suspend fun smartLists(): List<SmartList> {
        val context = smartContext()
        return dao.smartLists().map { context.toSmartList(it) }
    }

    /** Même tri que le serveur : par urgence du plus urgent (liste « par urgence »), puis échéance, heure, liste, groupe, tâche. */
    override suspend fun smartTasks(smartListId: String): List<DueTask> {
        val entity = dao.smartList(smartListId) ?: throw notFound()
        val context = smartContext()
        val list = context.toSmartList(entity)
        val rank = context.levels.associate { it.id to it.rank }
        val matching = context.openTasks.filter { context.matches(list, it.task) }.sortedWith(
            compareBy<OpenTask>(
                { if (list.kind == SmartList.KIND_URGENCY) -(rank[it.task.urgencyId] ?: -1) else 0 },
                { it.task.dueDate ?: "9999-99-99" },
                { it.task.dueTime ?: "99:99" },
                { it.listPosition },
                { it.groupPosition },
                { it.task.position },
            )
        )
        val tasks = toTasks(matching.map { it.task })
        return tasks.mapNotNull { task ->
            val group = dao.group(task.groupId) ?: return@mapNotNull null
            val owner = dao.list(group.listId) ?: return@mapNotNull null
            DueTask(task, ListRef(owner.id, owner.name, owner.color, owner.icon), group.name)
        }
    }

    override suspend fun createSmartList(changes: SmartListChanges): SmartList {
        val fields = parse(changes.toJson())
        val kind = fields.string("kind") ?: throw badRequest("kind doit valoir tags ou urgency")
        var entity = SmartListEntity(
            id = newId(), name = "", color = DEFAULT_LIST_COLOR, icon = DEFAULT_LIST_ICON, kind = kind,
            tagMatch = SmartList.MATCH_ANY, tagIds = "", urgencyIds = "", visible = true,
            position = nextPosition(dao.smartLists().map { it.position }), createdAt = now(),
        )
        entity = applySmartChanges(entity, fields, creating = true)
        db.withTransaction {
            dao.insertSmartList(entity)
            record("POST", "/smart-lists", changes.toJson(id = entity.id))
        }
        return smartContext().toSmartList(entity)
    }

    override suspend fun updateSmartList(smartListId: String, changes: SmartListChanges): SmartList {
        val fields = parse(changes.toJson())
        val entity = applySmartChanges(dao.smartList(smartListId) ?: throw notFound(), fields, creating = false)
        db.withTransaction {
            dao.updateSmartList(entity)
            record("PATCH", "/smart-lists/$smartListId", changes.toJson())
        }
        return smartContext().toSmartList(entity)
    }

    override suspend fun deleteSmartList(smartListId: String) {
        dao.smartList(smartListId) ?: throw notFound()
        db.withTransaction {
            dao.deleteSmartList(smartListId)
            record("DELETE", "/smart-lists/$smartListId")
        }
    }

    /** Les champs envoyés, vérifiés comme le serveur (Create / Update de smartlists.go). */
    private suspend fun applySmartChanges(start: SmartListEntity, fields: JsonObject, creating: Boolean): SmartListEntity {
        var entity = start
        if (creating && "name" !in fields) throw badRequest("Le nom doit faire entre 1 et $MAX_NAME caractères")
        fields.string("name")?.let { entity = entity.copy(name = cleanName(it, "Le nom")) }
        fields.string("color")?.let { entity = entity.copy(color = checkColor(it)) }
        fields.string("icon")?.let {
            if (!ICON_PATTERN.matches(it)) throw badRequest("Nom d'icône invalide (lettres minuscules, chiffres et tirets)")
            entity = entity.copy(icon = it)
        }
        fields.string("kind")?.let {
            if (it != SmartList.KIND_TAGS && it != SmartList.KIND_URGENCY) throw badRequest("kind doit valoir tags ou urgency")
            entity = entity.copy(kind = it)
        }
        fields.string("tag_match")?.let {
            if (it != SmartList.MATCH_ANY && it != SmartList.MATCH_ALL) throw badRequest("tag_match doit valoir any ou all")
            entity = entity.copy(tagMatch = it)
        }
        if ("tag_ids" in fields) {
            val ids = fields.getValue("tag_ids").jsonArray.map { it.jsonPrimitive.content }.distinct()
            ids.forEach { dao.tag(it) ?: throw badRequest("Tag inconnu") }
            entity = entity.copy(tagIds = ids.joinToString(","))
        }
        if ("urgency_ids" in fields) {
            val ids = fields.getValue("urgency_ids").jsonArray.map { it.jsonPrimitive.content }.distinct()
            ids.forEach { dao.urgencyLevel(it) ?: throw badRequest("Niveau d'urgence inconnu") }
            entity = entity.copy(urgencyIds = ids.joinToString(","))
        }
        if ("visible" in fields) entity = entity.copy(visible = fields.getValue("visible").jsonPrimitive.boolean)
        // Le filtre du type choisi doit avoir au moins un élément
        if (entity.kind == SmartList.KIND_TAGS && SmartMatch.split(entity.tagIds).isEmpty()) throw badRequest("Choisir au moins un tag")
        if (entity.kind == SmartList.KIND_URGENCY && SmartMatch.split(entity.urgencyIds).isEmpty()) {
            throw badRequest("Choisir au moins un niveau d'urgence")
        }
        return entity
    }

    /** Ce qu'il faut pour afficher et filtrer les listes intelligentes : tags, niveaux, tâches à faire et leurs tags. */
    private inner class SmartContext(
        val tags: Map<String, String>, // id → nom
        val levels: List<UrgencyEntity>,
        val openTasks: List<OpenTask>,
        val taskTags: Map<String, Set<String>>, // tâche → ids de ses tags
    ) {
        /** L'entité → le modèle des écrans (tags et niveaux qui existent encore, libellés, compteur). */
        fun toSmartList(entity: SmartListEntity): SmartList {
            val tagIds = SmartMatch.split(entity.tagIds).filter { it in tags }.sortedBy { tags.getValue(it) }
            val levelIds = SmartMatch.split(entity.urgencyIds).toSet()
            val chosenLevels = levels.filter { it.id in levelIds }.sortedByDescending { it.rank }
            val labels = if (entity.kind == SmartList.KIND_TAGS) tagIds.map { "#" + tags.getValue(it) } else chosenLevels.map { it.name }
            val list = SmartList(
                entity.id, entity.name, entity.color, entity.icon, entity.kind, entity.tagMatch,
                tagIds, chosenLevels.map { it.id }, labels, entity.visible, entity.position,
            )
            return list.copy(openTasks = openTasks.count { matches(list, it.task) })
        }

        fun matches(list: SmartList, task: TaskEntity): Boolean = SmartMatch.matches(
            list.kind, list.tagMatch, list.tagIds.toSet(), list.urgencyIds.toSet(), taskTags[task.id] ?: emptySet(), task.urgencyId,
        )
    }

    private suspend fun smartContext(): SmartContext = SmartContext(
        tags = dao.allTags().associate { it.id to it.name },
        levels = dao.urgencyLevels(),
        openTasks = dao.openTasksWithPlace(),
        taskTags = dao.allTaskTags().groupBy({ it.taskId }, { it.tagId }).mapValues { it.value.toSet() },
    )

    // --- Niveaux d'urgence -------------------------------------------------------------

    override suspend fun urgencyLevels(): List<UrgencyLevel> {
        profile() // Base neuve : crée les 3 niveaux de départ
        return dao.urgencyLevels().map { UrgencyLevel(it.id, it.name, it.color, it.rank) }
    }

    override suspend fun createUrgencyLevel(name: String, color: String): UrgencyLevel {
        val level = UrgencyEntity(newId(), cleanName(name, "Le nom"), checkColor(color), nextPosition(dao.urgencyLevels().map { it.rank }), now())
        db.withTransaction {
            dao.insertUrgencyLevel(level)
            record("POST", "/urgency-levels", buildJsonObject {
                put("id", level.id)
                put("name", level.name)
                put("color", level.color)
            })
        }
        return UrgencyLevel(level.id, level.name, level.color, level.rank)
    }

    override suspend fun updateUrgencyLevel(id: String, name: String?, color: String?, rank: Int?) {
        var level = dao.urgencyLevel(id) ?: throw notFound()
        if (name != null) level = level.copy(name = cleanName(name, "Le nom"))
        if (color != null) level = level.copy(color = checkColor(color))
        db.withTransaction {
            dao.updateUrgencyLevel(level)
            if (rank != null) {
                val ids = Ordering.move(dao.urgencyLevels().map { it.id }, id, rank)
                ids.forEachIndexed { index, levelId -> dao.setUrgencyRank(levelId, index) }
            }
            record("PATCH", "/urgency-levels/$id", buildJsonObject {
                if (name != null) put("name", level.name)
                if (color != null) put("color", color)
                if (rank != null) put("rank", rank)
            })
        }
    }

    override suspend fun deleteUrgencyLevel(id: String) {
        dao.urgencyLevel(id) ?: throw notFound()
        db.withTransaction {
            dao.deleteUrgencyLevel(id) // Ses tâches passent sans urgence (SET_NULL)
            record("DELETE", "/urgency-levels/$id")
        }
    }

    // --- Tags -------------------------------------------------------------------------

    override suspend fun tags(): List<Tag> = dao.tagsWithCount().map { Tag(it.id, it.name, it.taskCount) }

    override suspend fun createTag(name: String): Tag {
        val clean = cleanTag(name)
        if (dao.tagNamed(clean) != null) throw ApiError(409, "Ce tag existe déjà")
        val tag = TagEntity(newId(), clean)
        db.withTransaction {
            dao.insertTag(tag)
            record("POST", "/tags", buildJsonObject {
                put("id", tag.id)
                put("name", tag.name)
            })
        }
        return Tag(tag.id, tag.name, 0)
    }

    override suspend fun renameTag(id: String, name: String) {
        val tag = dao.tag(id) ?: throw notFound()
        val clean = cleanTag(name)
        val other = dao.tagNamed(clean)
        if (other != null && other.id != id) throw ApiError(409, "Ce tag existe déjà")
        db.withTransaction {
            dao.updateTag(tag.copy(name = clean))
            record("PATCH", "/tags/$id", buildJsonObject { put("name", clean) })
        }
    }

    override suspend fun deleteTag(id: String) {
        dao.tag(id) ?: throw notFound()
        db.withTransaction {
            dao.deleteTag(id) // Enlevé de ses tâches (CASCADE sur task_tags)
            record("DELETE", "/tags/$id")
        }
    }

    // --- Validation (mêmes règles et messages que le serveur) --------------------------

    private fun cleanName(name: String, field: String): String {
        val clean = name.trim()
        if (clean.codePointCount(0, clean.length) !in 1..MAX_NAME) {
            throw badRequest("$field doit faire entre 1 et $MAX_NAME caractères")
        }
        return clean
    }

    private fun cleanTitle(title: String): String {
        val clean = title.trim()
        if (clean.codePointCount(0, clean.length) !in 1..MAX_TITLE) throw badRequest("Le titre doit faire entre 1 et 500 caractères")
        return clean
    }

    /** "#urgent" → "urgent" (comme cleanTagName du serveur). */
    private fun cleanTag(name: String): String {
        val clean = name.trim().removePrefix("#").trim()
        if (clean.codePointCount(0, clean.length) !in 1..MAX_TAG) throw badRequest("Un tag doit faire entre 1 et 50 caractères")
        return clean
    }

    /** Noms nettoyés, sans doublon, dans l'ordre reçu. */
    private fun normalizeTags(names: List<String>): List<String> = names.map { cleanTag(it) }.distinct()

    private fun checkColor(color: String): String {
        if (!COLOR_PATTERN.matches(color)) throw badRequest("La couleur doit être au format #RRGGBB")
        return color
    }

    private fun checkView(view: String): String {
        if (view != TodoList.VIEW_COLUMNS && view != TodoList.VIEW_SECTIONS) throw badRequest("default_view doit valoir columns ou sections")
        return view
    }

    private fun checkDate(date: String): String {
        try {
            LocalDate.parse(date)
        } catch (e: Exception) {
            throw badRequest("due_date doit être une date au format AAAA-MM-JJ")
        }
        return date
    }

    private fun checkTime(time: String): String {
        if (!TIME_PATTERN.matches(time)) throw badRequest("due_time doit être une heure au format HH:MM")
        try {
            LocalTime.parse(time)
        } catch (e: Exception) {
            throw badRequest("due_time doit être une heure au format HH:MM")
        }
        return time
    }

    private fun badRequest(message: String) = ApiError(400, message)
    private fun notFound() = ApiError(404, "Introuvable")

    companion object {
        /** L'id du compte local (le serveur en donne un vrai à la synchronisation). */
        const val LOCAL_USER_ID = "local"

        private const val MAX_NAME = 100
        private const val MAX_TITLE = 500
        private const val MAX_NOTES = 10000
        private const val MAX_TAG = 50
        private const val DEFAULT_LIST_COLOR = "#6B6B6B"
        private const val DEFAULT_LIST_ICON = "list"

        private val COLOR_PATTERN = Regex("^#[0-9A-Fa-f]{6}$")
        private val ICON_PATTERN = Regex("^[a-z0-9-]{1,50}$")
        private val TIME_PATTERN = Regex("^\\d{2}:\\d{2}$")

        /** Nom du groupe créé avec chaque nouvelle liste, selon la langue du compte. */
        private val DEFAULT_GROUP_NAME = mapOf("fr" to "Tâches", "en" to "Tasks")

        /** Niveaux d'urgence d'un nouveau compte (defaultUrgencyLevels du serveur). */
        private val DEFAULT_URGENCY_LEVELS = listOf("Basse" to "#3FA66B", "Moyenne" to "#E8A33D", "Haute" to "#E5484D")

        private fun newId(): String = UUID.randomUUID().toString()
        private fun now(): Long = System.currentTimeMillis()

        /** Position d'un nouvel élément : à la fin (MAX + 1, ou 0 s'il n'y en a pas). */
        fun nextPosition(positions: List<Int>): Int = (positions.maxOrNull() ?: -1) + 1

        // Lecture du JSON d'un PATCH (ListChanges / TaskChanges)
        private fun parse(json: String): JsonObject = Json.parseToJsonElement(json).jsonObject
        private fun JsonObject.isNull(key: String) = this[key] is JsonNull
        private fun JsonObject.string(key: String): String? = this[key]?.takeIf { it !is JsonNull }?.jsonPrimitive?.content
        private fun JsonObject.int(key: String): Int? = this[key]?.takeIf { it !is JsonNull }?.jsonPrimitive?.int
    }
}
