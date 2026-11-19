package io.github.acssio.bettertodo.ui

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import io.github.acssio.bettertodo.api.TodoApi
import io.github.acssio.bettertodo.api.BoardGroup
import io.github.acssio.bettertodo.api.DueTask
import io.github.acssio.bettertodo.api.Task
import kotlinx.coroutines.CoroutineScope

/**
 * « Aujourd'hui » ou « Planifié » ouvert : les tâches à faire avec une échéance, de toutes les listes.
 * Le chargement, la tâche ouverte et les modifications sont dans TaskStore.
 *
 * Une tâche modifiée reste dans `tasks` (le panneau ouvert la retrouve) ; c'est `visible` qui
 * décide si elle est encore à sa place (SmartLists.belongs) : cochée, ou repoussée à demain
 * dans « Aujourd'hui », elle quitte la vue.
 */
class SmartModel(
    api: TodoApi,
    val kind: SmartKind,
    scope: CoroutineScope,
    onUnauthorized: () -> Unit,
) : TaskStore(api, scope, onUnauthorized) {

    var tasks by mutableStateOf<List<DueTask>>(emptyList())
        private set

    override val allTasks: List<Task>
        get() = tasks.map { it.task }

    override val hidesDoneTasks: Boolean = true // Seulement des tâches à faire

    init {
        load()
    }

    /** Sections à afficher (la date du jour est celle du téléphone). */
    val sections: List<DueSection>
        get() = SmartLists.sections(visible(tasks, kind, leavingIds), kind)

    override suspend fun loadData() {
        // La date du jour est envoyée par l'app : c'est elle qui connaît le fuseau horaire
        tasks = api.dueTasks(until = if (kind == SmartKind.TODAY) DueDates.todayIso() else null)
    }

    override fun applyLocal(task: Task) {
        tasks = tasks.map { if (it.task.id == task.id) it.copy(task = task) else it }
    }

    override fun removeLocal(taskId: String) {
        tasks = tasks.filter { it.task.id != taskId }
    }

    /** On ne connaît que le groupe de la tâche, pas toute sa liste (comme le site) : lecture seule. */
    override fun groupsFor(task: Task): List<BoardGroup>? = null

    override val visibleTaskIds: List<String>
        get() = sections.flatMap { section -> section.tasks.map { it.task.id } }

    override fun listIdOf(task: Task): String = tasks.firstOrNull { it.task.id == task.id }?.list?.id ?: ""

    /** La tâche reste dans la vue (son échéance n'a pas changé) ; afterMove recharge les noms de liste et de groupe. */
    override fun applyMoved(task: Task, listId: String, groupId: String) {
        applyLocal(task.copy(groupId = groupId))
    }

    override fun afterMove() {
        fetch()
    }

    override fun placeOf(task: Task): String {
        val due = tasks.firstOrNull { it.task.id == task.id } ?: return ""
        return "${due.list.name} › ${due.groupName}"
    }

    companion object {
        /** Les tâches encore à leur place dans la vue, plus celles qui viennent d'être cochées. */
        fun visible(tasks: List<DueTask>, kind: SmartKind, leaving: Set<String>): List<DueTask> {
            return tasks.filter { SmartLists.belongs(it.task, kind) || it.task.id in leaving }
        }
    }
}
