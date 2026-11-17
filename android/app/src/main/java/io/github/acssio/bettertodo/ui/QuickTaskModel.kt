package io.github.acssio.bettertodo.ui

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import io.github.acssio.bettertodo.api.ApiClient
import io.github.acssio.bettertodo.api.ApiError
import io.github.acssio.bettertodo.api.BoardGroup
import io.github.acssio.bettertodo.api.TodoList
import io.github.acssio.bettertodo.data.QuickTaskMemory
import io.github.acssio.bettertodo.data.QuickTaskPlace
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch

/**
 * Panneau « Quick task » de l'accueil (comme QuickTaskSheet et utils/quickTask.ts du site) :
 * la liste et le groupe (préremplis avec ceux de la dernière Quick task), et l'échéance.
 * Le titre est gardé dans le panneau lui-même (rememberSaveable).
 *
 * Comme BoardModel, ce n'est pas un ViewModel : HomeViewModel en crée un à chaque ouverture.
 *
 * @param lists les listes de l'accueil, dans leur ordre
 * @param onCreated appelé après une création réussie, avec « Liste › Groupe » pour le message
 */
class QuickTaskModel(
    private val api: ApiClient,
    val lists: List<TodoList>,
    private val memory: QuickTaskMemory,
    private val scope: CoroutineScope,
    private val onUnauthorized: () -> Unit,
    private val onCreated: (where: String) -> Unit,
) {
    var listId by mutableStateOf<String?>(null)
        private set
    var groups by mutableStateOf<List<BoardGroup>>(emptyList()) // Groupes de la liste choisie
        private set
    var groupId by mutableStateOf<String?>(null)
        private set
    var loadingGroups by mutableStateOf(false)
        private set

    var dueDate by mutableStateOf<String?>(DueDates.todayIso()) // « Aujourd'hui » proposé par défaut
        private set

    var busy by mutableStateOf(false) // Création en cours : bouton désactivé
        private set
    var error by mutableStateOf<String?>(null)
        private set

    /** Mémoire lue à l'ouverture : son groupe est proposé si on choisit sa liste. */
    private var saved: QuickTaskPlace? = null

    init {
        scope.launch {
            saved = memory.read()
            chooseList(saved?.listId, lists)?.let { selectList(it) }
        }
    }

    /** La liste choisie change : on charge ses groupes, et on propose le groupe retenu ou le premier. */
    fun selectList(id: String) {
        listId = id
        groups = emptyList()
        groupId = null
        loadingGroups = true
        scope.launch {
            try {
                val loaded = api.board(id).groups
                if (listId == id) { // Pas une liste choisie entre-temps
                    groups = loaded
                    groupId = chooseGroup(saved?.groupId, loaded)
                }
            } catch (e: ApiError) {
                if (e.status == 401) onUnauthorized() else error = e.message
            }
            loadingGroups = false
        }
    }

    fun selectGroup(id: String) {
        groupId = id
    }

    /** @param date "AAAA-MM-JJ", ou null pour une tâche sans échéance */
    fun selectDueDate(date: String?) {
        dueDate = date
    }

    /** Peut-on créer ? (un titre, un groupe, et pas déjà en train de créer) */
    fun canCreate(title: String): Boolean = title.isNotBlank() && groupId != null && !busy

    /**
     * Crée la tâche. En cas de succès : la mémoire est mise à jour (seulement là, comme le site)
     * et onCreated ferme le panneau. En cas d'erreur : le panneau reste ouvert avec le message.
     */
    fun create(title: String) {
        val list = listId ?: return
        val group = groupId ?: return
        if (!canCreate(title)) return

        busy = true
        error = null
        scope.launch {
            try {
                api.createTask(group, title.trim(), dueDate)
                memory.save(QuickTaskPlace(list, group))
                val listName = lists.firstOrNull { it.id == list }?.name ?: ""
                val groupName = groups.firstOrNull { it.id == group }?.name ?: ""
                onCreated("$listName › $groupName")
            } catch (e: ApiError) {
                if (e.status == 401) onUnauthorized() else error = e.message
            }
            busy = false
        }
    }

    companion object {
        /** La liste retenue si elle existe encore, sinon la première liste (null s'il n'y en a aucune). */
        fun chooseList(savedListId: String?, lists: List<TodoList>): String? {
            if (savedListId != null && lists.any { it.id == savedListId }) return savedListId
            return lists.firstOrNull()?.id
        }

        /** Le groupe retenu s'il est dans la liste choisie, sinon son premier groupe (null si elle n'en a pas). */
        fun chooseGroup(savedGroupId: String?, groups: List<BoardGroup>): String? {
            if (savedGroupId != null && groups.any { it.id == savedGroupId }) return savedGroupId
            return groups.firstOrNull()?.id
        }
    }
}
