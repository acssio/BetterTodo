package io.github.acssio.bettertodo.ui

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import io.github.acssio.bettertodo.api.ApiClient
import io.github.acssio.bettertodo.api.ApiError
import io.github.acssio.bettertodo.api.Board
import io.github.acssio.bettertodo.api.BoardGroup
import io.github.acssio.bettertodo.api.Task
import io.github.acssio.bettertodo.api.TaskChanges
import io.github.acssio.bettertodo.api.TodoList
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch

/**
 * Une liste ouverte : ses groupes et ses tâches.
 * Le chargement, la tâche ouverte et les modifications sont dans TaskStore.
 *
 * @param list la liste touchée sur l'accueil : affichée tout de suite (nom, icône) pendant le chargement
 */
class BoardModel(
    api: ApiClient,
    val list: TodoList,
    scope: CoroutineScope,
    onUnauthorized: () -> Unit,
) : TaskStore(api, scope, onUnauthorized) {

    var board by mutableStateOf<Board?>(null)
        private set

    /** La liste à jour (après chargement), sinon celle de l'accueil. */
    val currentList: TodoList get() = board?.list ?: list

    override val allTasks: List<Task>
        get() = board?.groups?.flatMap { it.tasks } ?: emptyList()

    override val hidesDoneTasks: Boolean
        get() = !currentList.showDone

    init {
        load()
    }

    override suspend fun loadData() {
        board = api.board(list.id)
    }

    override fun applyLocal(task: Task) {
        board = board?.let { applyTask(it, task) }
    }

    override fun removeLocal(taskId: String) {
        board = board?.let { removeTask(it, taskId) }
    }

    override fun groupsFor(task: Task): List<BoardGroup> = board?.groups ?: emptyList()

    override val visibleTaskIds: List<String>
        get() = board?.groups?.flatMap { group -> visibleTasks(group).map { it.id } } ?: emptyList()

    override fun listIdOf(task: Task): String = list.id

    /** Même liste : à la fin du groupe ; autre liste : la tâche quitte l'écran. */
    override fun applyMoved(task: Task, listId: String, groupId: String) {
        if (listId == list.id) {
            applyLocal(task.copy(groupId = groupId))
        } else {
            removeLocal(task.id)
        }
    }

    /** Pour chaque groupe, les ids des tâches affichées dans l'ordre (cible d'un glisser). */
    fun visibleIdsByGroup(): Map<String, List<String>> {
        return board?.groups?.associate { group -> group.id to visibleTasks(group).map { it.id } } ?: emptyMap()
    }

    /** Les tâches à afficher dans un groupe (sans les faites si la liste les masque). */
    fun visibleTasks(group: BoardGroup): List<Task> {
        return visibleTasks(group.tasks, currentList.showDone, leavingIds)
    }

    /** Ajoute une tâche à la fin du groupe. Elle apparaît à la réponse du serveur (il faut son id). */
    fun createTask(groupId: String, title: String) {
        val clean = title.trim()
        if (clean.isEmpty()) {
            return
        }
        scope.launch {
            try {
                applyLocal(api.createTask(groupId, clean))
            } catch (e: ApiError) {
                handleFailure(e) {}
            }
        }
    }

    /**
     * Dépôt d'un glisser : les tâches movedIds (dans l'ordre de l'écran) vont dans groupId,
     * à la place visibleIndex parmi les tâches affichées (comptée sans elles).
     * L'écran d'abord, puis un PATCH {group_id, position} par tâche, l'un après l'autre (comme le site).
     */
    fun dropTasks(movedIds: List<String>, groupId: String, visibleIndex: Int) {
        val before = board ?: return
        val group = before.groups.firstOrNull { it.id == groupId } ?: return
        val beforeId = DragDrop.dropAnchor(group.tasks, movedIds, visibleIndex, currentList.showDone)
        val positions = DragDrop.multiMovePositions(group.tasks.map { it.id }, movedIds, beforeId)
        val after = reorder(before, movedIds, groupId, beforeId)

        clearSelection()
        if (after == before) {
            return // Déposée à sa place : rien à envoyer
        }
        board = after
        scope.launch {
            try {
                movedIds.zip(positions).forEach { (id, position) ->
                    api.updateTask(id, TaskChanges().groupId(groupId).position(position))
                }
            } catch (e: ApiError) {
                handleFailure(e) { board = before }
            }
        }
    }

    companion object {
        /**
         * Copie du board où les tâches movedIds (dans cet ordre) sont dans le groupe groupId,
         * juste avant beforeId (null = à la fin).
         */
        fun reorder(board: Board, movedIds: List<String>, groupId: String, beforeId: String?): Board {
            val byId = board.groups.flatMap { it.tasks }.associateBy { it.id }
            val moved = movedIds.mapNotNull { byId[it]?.copy(groupId = groupId) }
            val groups = board.groups.map { group ->
                val others = group.tasks.filter { it.id !in movedIds }
                if (group.id != groupId) {
                    group.copy(tasks = others)
                } else {
                    val index = if (beforeId == null) others.size else others.indexOfFirst { it.id == beforeId }.let { if (it < 0) others.size else it }
                    group.copy(tasks = others.take(index) + moved + others.drop(index))
                }
            }
            return board.copy(groups = groups)
        }

        /**
         * Tâches visibles : toutes si la liste montre les faites, sinon les « à faire »
         * plus celles qui viennent d'être cochées (leaving).
         */
        fun visibleTasks(tasks: List<Task>, showDone: Boolean, leaving: Set<String>): List<Task> {
            return tasks.filter { showDone || !it.done || it.id in leaving }
        }

        /**
         * Copie du board avec la tâche mise à jour :
         *  - même groupe : remplacée sur place ;
         *  - autre groupe (ou nouvelle tâche) : retirée de l'ancien, ajoutée à la fin du nouveau.
         * On ne modifie jamais le board en place : Compose ne verrait pas le changement.
         */
        fun applyTask(board: Board, task: Task): Board {
            val current = board.groups.flatMap { it.tasks }.firstOrNull { it.id == task.id }
            if (current != null && current.groupId == task.groupId) {
                val groups = board.groups.map { group ->
                    group.copy(tasks = group.tasks.map { if (it.id == task.id) task else it })
                }
                return board.copy(groups = groups)
            }

            val groups = removeTask(board, task.id).groups.map { group ->
                if (group.id == task.groupId) group.copy(tasks = group.tasks + task) else group
            }
            return board.copy(groups = groups)
        }

        /** Copie du board sans la tâche. */
        fun removeTask(board: Board, taskId: String): Board {
            return board.copy(groups = board.groups.map { group -> group.copy(tasks = group.tasks.filter { it.id != taskId }) })
        }
    }
}
