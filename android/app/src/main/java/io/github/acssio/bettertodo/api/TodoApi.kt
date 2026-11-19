package io.github.acssio.bettertodo.api

/**
 * Tout ce que les écrans demandent à leurs données : listes, tâches, réglages.
 * Deux classes le font :
 *  - ApiClient : en parlant au serveur (HTTP) ;
 *  - LocalApi  : dans la base du téléphone (Room), sans serveur (8-3).
 *
 * Les écrans (HomeViewModel, BoardModel, TaskStore…) ne connaissent que TodoApi :
 * ils marchent pareil avec ou sans serveur. Une erreur est toujours une ApiError
 * (400 donnée refusée, 404 introuvable, 409 déjà pris…), dans les deux cas.
 *
 * Ce qui n'a de sens qu'avec un serveur (connexion, mot de passe, déconnexion) reste dans ApiClient seul.
 */
interface TodoApi {

    // --- Compte ----------------------------------------------------------------

    suspend fun me(): User

    /** Seuls les champs non null sont changés. */
    suspend fun updateMe(
        displayName: String? = null,
        language: String? = null,
        defaultView: String? = null,
        quickTaskDue: String? = null,
        showToday: Boolean? = null,
        showScheduled: Boolean? = null,
    ): User

    // --- Groupes de listes et listes -------------------------------------------

    suspend fun folders(): List<Folder>
    suspend fun lists(): List<TodoList>
    suspend fun createFolder(name: String): Folder
    suspend fun renameFolder(folderId: String, name: String): Folder
    suspend fun setFolderCollapsed(folderId: String, collapsed: Boolean): Folder

    /** Les listes du groupe sont gardées, hors groupe. */
    suspend fun deleteFolder(folderId: String)

    /** Crée une liste avec un groupe par défaut (« Tâches » ou « Tasks »). */
    suspend fun createList(name: String, folderId: String?): TodoList
    suspend fun updateList(listId: String, changes: ListChanges): TodoList
    suspend fun deleteList(listId: String)

    /** Supprime toutes les tâches faites de la liste. */
    suspend fun clearDone(listId: String)

    suspend fun createGroup(listId: String, name: String): BoardGroup
    suspend fun renameGroup(groupId: String, name: String)
    suspend fun deleteGroup(groupId: String)

    /** La liste, ses groupes et toutes leurs tâches (faites comprises). */
    suspend fun board(listId: String): Board

    // --- Tâches ------------------------------------------------------------------

    /** À la fin du groupe. @param dueDate "AAAA-MM-JJ" ou null */
    suspend fun createTask(groupId: String, title: String, dueDate: String? = null): Task
    suspend fun updateTask(taskId: String, changes: TaskChanges): Task
    suspend fun deleteTask(taskId: String)

    /** Tâches à faire avec une échéance, jusqu'à until ("AAAA-MM-JJ") ou toutes si null. */
    suspend fun dueTasks(until: String?): List<DueTask>

    /** @param today date du jour du téléphone ("AAAA-MM-JJ") */
    suspend fun dueCounts(today: String): DueCounts

    // --- Listes intelligentes personnalisées (8-6) -----------------------------------

    /** Dans leur ordre, avec leur nombre de tâches à faire. */
    suspend fun smartLists(): List<SmartList>

    /** Les tâches à faire qui correspondent (format de dueTasks). */
    suspend fun smartTasks(smartListId: String): List<DueTask>
    suspend fun createSmartList(changes: SmartListChanges): SmartList
    suspend fun updateSmartList(smartListId: String, changes: SmartListChanges): SmartList
    suspend fun deleteSmartList(smartListId: String)

    // --- Niveaux d'urgence et tags -------------------------------------------------

    suspend fun urgencyLevels(): List<UrgencyLevel>
    suspend fun createUrgencyLevel(name: String, color: String): UrgencyLevel

    /** @param rank nouvelle place (0 = le moins urgent), les autres sont renumérotés */
    suspend fun updateUrgencyLevel(id: String, name: String? = null, color: String? = null, rank: Int? = null)
    suspend fun deleteUrgencyLevel(id: String)

    suspend fun tags(): List<Tag>
    suspend fun createTag(name: String): Tag
    suspend fun renameTag(id: String, name: String)
    suspend fun deleteTag(id: String)
}
