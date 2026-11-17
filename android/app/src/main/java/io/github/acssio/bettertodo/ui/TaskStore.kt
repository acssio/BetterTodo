package io.github.acssio.bettertodo.ui

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import io.github.acssio.bettertodo.api.ApiClient
import io.github.acssio.bettertodo.api.ApiError
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
 * Ce qui est commun à un écran de tâches : une liste (BoardModel) ou Aujourd'hui / Planifié (SmartModel).
 *
 *  - chargement (état, rafraîchissement, bandeau d'erreur) et niveaux d'urgence ;
 *  - tâche ouverte dans le panneau du bas (TaskSheet ne connaît que TaskStore) ;
 *  - toutes les modifications d'une tâche, « optimistes » comme Workspace sur le site :
 *    l'écran change tout de suite, puis l'API ; en cas d'erreur, la tâche est remise
 *    comme avant, puis l'écran est rechargé avec un bandeau.
 *
 * Chaque sous-classe dit seulement comment charger ses données et comment afficher
 * une tâche modifiée ou supprimée (applyLocal / removeLocal).
 *
 * Ce n'est pas un ViewModel : HomeViewModel en crée un à chaque ouverture (données fraîches)
 * et le garde dans sa page courante, donc il survit à la rotation.
 *
 * @param onUnauthorized appelé sur un 401 (session expirée)
 */
abstract class TaskStore(
    protected val api: ApiClient,
    protected val scope: CoroutineScope,
    private val onUnauthorized: () -> Unit,
) {
    var state by mutableStateOf(LoadState.Loading)
        private set
    var refreshing by mutableStateOf(false)
        private set
    var error by mutableStateOf<String?>(null)
        private set

    var urgencies by mutableStateOf<Map<String, UrgencyLevel>>(emptyMap()) // id → niveau
        private set

    /** Tâches qui viennent d'être cochées et vont disparaître : visibles encore un instant. */
    var leavingIds by mutableStateOf<Set<String>>(emptySet())
        private set

    /** Tâche ouverte dans le panneau du bas (son id : il survit à la rotation). */
    var openTaskId by mutableStateOf<String?>(null)
        private set

    /** Tâches sélectionnées par appui long (vide = pas en mode sélection). */
    var selectedIds by mutableStateOf<Set<String>>(emptySet())
        private set

    /** Panneau « Déplacer » ouvert (null = fermé). */
    var moving by mutableStateOf<PlacePicker?>(null)
        private set

    // --- À écrire par chaque écran ------------------------------------------------

    /** Toutes les tâches de l'écran (même masquées) : pour retrouver la tâche ouverte. */
    protected abstract val allTasks: List<Task>

    /** Une tâche cochée disparaît-elle de cet écran ? */
    protected abstract val hidesDoneTasks: Boolean

    /** Charge les données de l'écran (appelé en même temps que les niveaux d'urgence). */
    protected abstract suspend fun loadData()

    /** Affiche la tâche modifiée (ou nouvelle). */
    protected abstract fun applyLocal(task: Task)

    /** Retire la tâche de l'écran. */
    protected abstract fun removeLocal(taskId: String)

    /** Groupes proposés dans le panneau pour déplacer la tâche, ou null si on ne les connaît pas. */
    abstract fun groupsFor(task: Task): List<BoardGroup>?

    /** Où est la tâche, affiché quand groupsFor renvoie null (« Liste › Groupe »). */
    open fun placeOf(task: Task): String = ""

    /** Ids des tâches affichées, dans l'ordre de l'écran (pour « Tout » et l'ordre de « Déplacer »). */
    abstract val visibleTaskIds: List<String>

    /** La liste d'une tâche (proposée par « Déplacer »). */
    protected abstract fun listIdOf(task: Task): String

    /** Affiche une tâche déplacée dans le groupe groupId de la liste listId (ou la retire si elle quitte l'écran). */
    protected abstract fun applyMoved(task: Task, listId: String, groupId: String)

    /** Après un « Déplacer » réussi (ex. recharger pour avoir les nouveaux noms de liste et de groupe). */
    protected open fun afterMove() {}

    // --- Chargement --------------------------------------------------------------

    fun load() {
        state = LoadState.Loading
        fetch()
    }

    fun refresh() {
        refreshing = true
        fetch()
    }

    /**
     * Charge les données de l'écran et les niveaux d'urgence en même temps.
     * @param message affiché après le rechargement (ex. modification ratée), ou null
     */
    protected fun fetch(message: String? = null) {
        scope.launch {
            try {
                // coroutineScope : obligatoire autour des async (voir étape 6-1)
                coroutineScope {
                    val levels = async { api.urgencyLevels() }
                    loadData()
                    urgencies = levels.await().associateBy { it.id }
                }
                error = message
                state = LoadState.Ready
            } catch (e: ApiError) {
                when {
                    e.status == 401 -> onUnauthorized()
                    state == LoadState.Ready -> error = message ?: e.message // On garde l'écran affiché
                    else -> {
                        error = e.message
                        state = LoadState.Failed
                    }
                }
            }
            refreshing = false
        }
    }

    // --- Tâche ouverte dans le panneau du bas -------------------------------------

    /** La tâche ouverte, cherchée dans les données : toujours à jour, même masquée. */
    val openedTask: Task?
        get() = allTasks.firstOrNull { it.id == openTaskId }

    fun openTask(task: Task) {
        openTaskId = task.id
    }

    fun closeTask() {
        openTaskId = null
    }

    // --- Sélection (appui long), comme le mode sélection du site mobile -----------
    //
    //   aucune ──(appui long)──→ sélection ──(toucher)──→ ajoute / retire (vide → aucune)
    //                               ├──(Annuler / retour)──→ aucune
    //                               ├──(Supprimer, confirmé)──→ aucune
    //                               └──(Déplacer)──→ panneau ──(Déplacer)──→ aucune

    val selecting: Boolean
        get() = selectedIds.isNotEmpty()

    /** Toucher une tâche : en mode sélection, l'ajoute / la retire ; sinon ouvre son panneau. */
    fun tap(task: Task) {
        if (selecting) toggleSelection(task) else openTask(task)
    }

    fun toggleSelection(task: Task) {
        toggleSelection(task.id)
    }

    fun toggleSelection(taskId: String) {
        selectedIds = toggle(selectedIds, taskId)
    }

    /** Tâches à glisser depuis taskId : toute la sélection si elle en fait partie, sinon elle seule (ordre de l'écran). */
    fun idsToDrag(taskId: String): List<String> {
        return if (taskId in selectedIds) inDisplayOrder(visibleTaskIds, selectedIds) else listOf(taskId)
    }

    fun titleOf(taskId: String): String = allTasks.firstOrNull { it.id == taskId }?.title ?: ""

    fun clearSelection() {
        selectedIds = emptySet()
    }

    /** « Tout » : toutes les tâches affichées. */
    fun selectAll() {
        selectedIds = visibleTaskIds.toSet()
    }

    /** Les tâches sélectionnées, dans l'ordre de l'écran. */
    fun selectedTasks(): List<Task> {
        val byId = allTasks.associateBy { it.id }
        return inDisplayOrder(visibleTaskIds, selectedIds).mapNotNull { byId[it] }
    }

    /** Supprime la sélection (après confirmation) : l'écran d'abord, puis un DELETE par tâche, l'un après l'autre. */
    fun deleteSelected() {
        val tasks = selectedTasks()
        clearSelection()
        tasks.forEach { removeLocal(it.id) }
        scope.launch {
            try {
                for (task in tasks) {
                    api.deleteTask(task.id)
                }
            } catch (e: ApiError) {
                handleFailure(e) {} // Le rechargement fera revenir celles qui n'ont pas été supprimées
            }
        }
    }

    /**
     * Ouvre « Déplacer » : la destination proposée est le groupe de la première tâche sélectionnée.
     * @param lists les listes proposées, dans l'ordre de l'accueil
     */
    fun startMove(lists: List<TodoList>) {
        val first = selectedTasks().firstOrNull() ?: return
        moving = PlacePicker(api, lists, scope, onUnauthorized).also { it.start(listIdOf(first), first.groupId) }
    }

    fun cancelMove() {
        moving = null
    }

    /**
     * Déplace la sélection à la fin du groupe choisi, dans l'ordre de l'écran :
     * l'écran d'abord, puis un PATCH group_id par tâche, l'un après l'autre (comme le site).
     */
    fun confirmMove() {
        val picker = moving ?: return
        val listId = picker.listId ?: return
        val groupId = picker.groupId ?: return
        val tasks = selectedTasks()
        moving = null
        clearSelection()

        tasks.forEach { applyMoved(it, listId, groupId) }
        scope.launch {
            try {
                for (task in tasks) {
                    api.updateTask(task.id, TaskChanges().groupId(groupId))
                }
                afterMove()
            } catch (e: ApiError) {
                handleFailure(e) {}
            }
        }
    }

    // --- Modifications ---------------------------------------------------------

    fun toggleDone(task: Task) {
        val done = !task.done
        if (done && hidesDoneTasks) {
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

    /** Supprime la tâche (après confirmation dans le panneau) et ferme le panneau. */
    fun deleteTask(task: Task) {
        closeTask()
        removeLocal(task.id)
        scope.launch {
            try {
                api.deleteTask(task.id)
            } catch (e: ApiError) {
                handleFailure(e) {} // Le rechargement fera revenir la tâche
            }
        }
    }

    /**
     * Affiche la tâche modifiée, puis envoie changes à l'API.
     * @param before la tâche avant modification (remise en cas d'erreur)
     */
    private fun updateTask(before: Task, after: Task, changes: TaskChanges) {
        // Tâche plus là (ex. supprimée, puis le panneau enregistre en se fermant) : rien à faire
        if (allTasks.none { it.id == before.id }) {
            return
        }
        applyLocal(after)
        scope.launch {
            try {
                api.updateTask(before.id, changes)
            } catch (e: ApiError) {
                handleFailure(e) {
                    applyLocal(before)
                    leavingIds = leavingIds - before.id
                }
            }
        }
    }

    /** Erreur d'une modification : 401 → session expirée ; sinon annuler (undo) puis recharger. */
    protected fun handleFailure(e: ApiError, undo: () -> Unit) {
        if (e.status == 401) {
            onUnauthorized()
            return
        }
        undo() // Utile si le rechargement échoue aussi
        fetch(message = "Modification non enregistrée : ${e.message}")
    }

    companion object {
        /** Durée pendant laquelle une tâche cochée reste visible avant de disparaître. */
        const val LEAVE_DELAY_MS = 700L

        /** Ajoute id à la sélection, ou l'en retire s'il y est déjà. */
        fun toggle(selected: Set<String>, id: String): Set<String> {
            return if (id in selected) selected - id else selected + id
        }

        /** Les ids sélectionnés, dans l'ordre de l'écran (ceux qui ne sont plus affichés sont ignorés). */
        fun inDisplayOrder(visibleIds: List<String>, selected: Set<String>): List<String> {
            return visibleIds.filter { it in selected }
        }

        /** "  #Courses " → "Courses". Vide → null. Comme addTag du site et cleanTagName du serveur. */
        fun cleanTag(input: String): String? {
            val tag = input.trim().removePrefix("#").trim()
            return tag.ifEmpty { null }
        }
    }
}
