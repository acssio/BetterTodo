package io.github.acssio.bettertodo.ui

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import io.github.acssio.bettertodo.api.ApiClient
import io.github.acssio.bettertodo.api.ApiError
import io.github.acssio.bettertodo.api.Board
import io.github.acssio.bettertodo.api.BoardGroup
import io.github.acssio.bettertodo.api.Task
import io.github.acssio.bettertodo.api.TodoList
import io.github.acssio.bettertodo.api.UrgencyLevel
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/**
 * Données d'une liste ouverte : ses groupes, ses tâches, et les niveaux d'urgence (pour les pastilles).
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

    /**
     * Cocher / décocher, de façon « optimiste » comme sur le site :
     * l'écran change tout de suite, puis l'API ; en cas d'erreur, on recharge la liste.
     */
    fun toggleDone(task: Task) {
        val done = !task.done
        board = board?.let { replaceTask(it, task.copy(done = done)) }

        if (done && !currentList.showDone) {
            leavingIds = leavingIds + task.id
            scope.launch {
                delay(LEAVE_DELAY_MS) // Le temps de voir la case se cocher
                leavingIds = leavingIds - task.id
            }
        }

        scope.launch {
            try {
                api.setTaskDone(task.id, done)
            } catch (e: ApiError) {
                if (e.status == 401) {
                    onUnauthorized()
                } else {
                    // On remet la tâche comme avant (utile si le rechargement échoue aussi), puis on recharge
                    board = board?.let { replaceTask(it, task) }
                    leavingIds = leavingIds - task.id
                    fetch(message = "Modification non enregistrée : ${e.message}")
                }
            }
        }
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

        /** Copie du board où la tâche (même id) est remplacée. */
        fun replaceTask(board: Board, task: Task): Board {
            val groups = board.groups.map { group ->
                group.copy(tasks = group.tasks.map { if (it.id == task.id) task else it })
            }
            return board.copy(groups = groups)
        }
    }
}
