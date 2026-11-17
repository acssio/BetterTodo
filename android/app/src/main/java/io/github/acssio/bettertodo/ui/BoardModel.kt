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
import io.github.acssio.bettertodo.api.UrgencyLevel
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/**
 * Données d'une liste ouverte : ses groupes, ses tâches, les niveaux d'urgence (pour les pastilles),
 * la tâche ouverte dans le panneau du bas, et toutes les modifications des tâches.
 *
 * Ce n'est pas un ViewModel : HomeViewModel en crée un à chaque ouverture d'une liste
 * (donc toujours des données fraîches) et le garde dans sa page courante. Il survit ainsi
 * à la rotation comme HomeViewModel, et ses requêtes tournent dans le scope de HomeViewModel.
 *
 * @param list la liste touchée sur l'accueil : affichée tout de suite (nom, icône) pendant le chargement
 * @param onUnauthorized appelé sur un 401 (session expirée)
 */
class BoardModel(
    private val api: ApiClient,
    val list: TodoList,
    private val scope: CoroutineScope,
    private val onUnauthorized: () -> Unit,
) {
    var state by mutableStateOf(LoadState.Loading)
        private set
    var refreshing by mutableStateOf(false)
        private set
    var error by mutableStateOf<String?>(null)
        private set

    var board by mutableStateOf<Board?>(null)
        private set
    var urgencies by mutableStateOf<Map<String, UrgencyLevel>>(emptyMap()) // id → niveau
        private set

    /** Tâches qui viennent d'être cochées dans une liste qui masque les faites : visibles encore un instant. */
    var leavingIds by mutableStateOf<Set<String>>(emptySet())
        private set

    /** Tâche ouverte dans le panneau du bas (son id : il survit à la rotation avec BoardModel). */
    var openTaskId by mutableStateOf<String?>(null)
        private set

    /** La liste à jour (après chargement), sinon celle de l'accueil. */
    val currentList: TodoList get() = board?.list ?: list

    init {
        load()
    }

    fun load() {
        state = LoadState.Loading
        fetch()
    }

    fun refresh() {
        refreshing = true
        fetch()
    }

    /** Les tâches à afficher dans un groupe (sans les faites si la liste les masque). */
    fun visibleTasks(group: BoardGroup): List<Task> {
        return visibleTasks(group.tasks, currentList.showDone, leavingIds)
    }

    // --- Tâche ouverte dans le panneau du bas -------------------------------------

    /** La tâche ouverte, cherchée dans le board : toujours à jour, même masquée (cochée). */
    val openedTask: Task?
        get() = board?.groups?.flatMap { it.tasks }?.firstOrNull { it.id == openTaskId }

    fun openTask(task: Task) {
        openTaskId = task.id
    }

    fun closeTask() {
        openTaskId = null
    }

    // --- Modifications (toutes « optimistes », comme Workspace sur le site) ------
    // L'écran change tout de suite, puis l'API. En cas d'erreur : la tâche est remise
    // comme avant, puis la liste est rechargée avec un bandeau.

    fun toggleDone(task: Task) {
        val done = !task.done
        if (done && !currentList.showDone) {
            leavingIds = leavingIds + task.id
            scope.launch {
                delay(LEAVE_DELAY_MS) // Le temps de voir la case se cocher
                leavingIds = leavingIds - task.id
            }
        }
        updateTask(task, task.copy(done = done), TaskChanges().done(done))
    }

    /** Titre vide ou inchangé : rien n'est envoyé. */
    fun setTitle(task: Task, title: String) {
        val clean = title.trim()
        if (clean.isNotEmpty() && clean != task.title) {
            updateTask(task, task.copy(title = clean), TaskChanges().title(clean))
        }
    }

    fun setNotes(task: Task, notes: String) {
        if (notes != task.notes) {
            updateTask(task, task.copy(notes = notes), TaskChanges().notes(notes))
        }
    }

    /** @param dueDate "2026-11-18", ou null pour enlever l'échéance */
    fun setDueDate(task: Task, dueDate: String?) {
        if (dueDate != task.dueDate) {
            updateTask(task, task.copy(dueDate = dueDate), TaskChanges().dueDate(dueDate))
        }
    }

    fun setUrgency(task: Task, urgencyId: String?) {
        if (urgencyId != task.urgencyId) {
            updateTask(task, task.copy(urgencyId = urgencyId), TaskChanges().urgencyId(urgencyId))
        }
    }

    /** Ajoute un tag tapé (nettoyé par cleanTag). Tag vide ou déjà présent : rien. */
    fun addTag(task: Task, input: String) {
        val tag = cleanTag(input) ?: return
        if (tag !in task.tags) {
            val tags = (task.tags + tag).sorted() // Triés, comme les renvoie le serveur
            updateTask(task, task.copy(tags = tags), TaskChanges().tags(tags))
        }
    }

    fun removeTag(task: Task, tag: String) {
        val tags = task.tags - tag
        updateTask(task, task.copy(tags = tags), TaskChanges().tags(tags))
    }

    /** Change de groupe : la tâche va à la fin du groupe choisi (comme le fait le serveur). */
    fun moveToGroup(task: Task, groupId: String) {
        if (groupId != task.groupId) {
            updateTask(task, task.copy(groupId = groupId), TaskChanges().groupId(groupId))
        }
    }

    /**
     * Applique la tâche modifiée à l'écran, puis envoie changes à l'API.
     * @param before la tâche avant modification (remise en cas d'erreur)
     */
    private fun updateTask(before: Task, after: Task, changes: TaskChanges) {
        // Tâche plus dans la liste (ex. supprimée, puis le panneau enregistre en se fermant) : rien à faire
        val current = board ?: return
        if (current.groups.none { group -> group.tasks.any { it.id == before.id } }) {
            return
        }
        board = applyTask(current, after)
        scope.launch {
            try {
                api.updateTask(before.id, changes)
            } catch (e: ApiError) {
                handleFailure(e) {
                    board = board?.let { applyTask(it, before) }
                    leavingIds = leavingIds - before.id
                }
            }
        }
    }

    /**
     * Ajoute une tâche à la fin du groupe. Elle apparaît à la réponse du serveur (il faut son id).
     */
    fun createTask(groupId: String, title: String) {
        val clean = title.trim()
        if (clean.isEmpty()) {
            return
        }
        scope.launch {
            try {
                val created = api.createTask(groupId, clean)
                board = board?.let { applyTask(it, created) }
            } catch (e: ApiError) {
                handleFailure(e) {}
            }
        }
    }

    /** Supprime la tâche (après confirmation dans le panneau) et ferme le panneau. */
    fun deleteTask(task: Task) {
        closeTask()
        board = board?.let { removeTask(it, task.id) }
        scope.launch {
            try {
                api.deleteTask(task.id)
            } catch (e: ApiError) {
                handleFailure(e) {} // Le rechargement fera revenir la tâche
            }
        }
    }

    /**
     * Erreur d'une modification : 401 → session expirée ; sinon annuler (undo) puis recharger.
     */
    private fun handleFailure(e: ApiError, undo: () -> Unit) {
        if (e.status == 401) {
            onUnauthorized()
            return
        }
        undo() // Utile si le rechargement échoue aussi
        fetch(message = "Modification non enregistrée : ${e.message}")
    }

    /**
     * Charge la liste et les niveaux d'urgence en même temps.
     * @param message affiché après le rechargement (ex. modification ratée), ou null
     */
    private fun fetch(message: String? = null) {
        scope.launch {
            try {
                // coroutineScope : obligatoire autour des async (voir étape 6-1)
                val (newBoard, levels) = coroutineScope {
                    val boardCall = async { api.board(list.id) }
                    val levelsCall = async { api.urgencyLevels() }
                    boardCall.await() to levelsCall.await()
                }
                board = newBoard
                urgencies = levels.associateBy { it.id }
                error = message
                state = LoadState.Ready
            } catch (e: ApiError) {
                when {
                    e.status == 401 -> onUnauthorized()
                    state == LoadState.Ready -> error = message ?: e.message // On garde la liste affichée
                    else -> {
                        error = e.message
                        state = LoadState.Failed
                    }
                }
            }
            refreshing = false
        }
    }

    companion object {
        /** Durée pendant laquelle une tâche cochée reste visible avant de disparaître. */
        const val LEAVE_DELAY_MS = 700L

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

        /** "  #Courses " → "Courses". Vide → null. Comme addTag du site et cleanTagName du serveur. */
        fun cleanTag(input: String): String? {
            val tag = input.trim().removePrefix("#").trim()
            return tag.ifEmpty { null }
        }
    }
}
