package io.github.acssio.bettertodo.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import io.github.acssio.bettertodo.api.TodoApi
import io.github.acssio.bettertodo.api.ApiError
import io.github.acssio.bettertodo.api.BoardGroup
import io.github.acssio.bettertodo.api.TodoList
import io.github.acssio.bettertodo.ui.theme.Palette
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch

/**
 * Choisir une liste puis un de ses groupes (comme ListGroupPicker du site).
 * Commun à la Quick task et à « Déplacer » : les groupes d'une liste sont chargés quand on la choisit.
 *
 * @param lists les listes proposées, dans l'ordre de l'accueil
 */
class PlacePicker(
    private val api: TodoApi,
    val lists: List<TodoList>,
    private val scope: CoroutineScope,
    private val onUnauthorized: () -> Unit,
) {
    var listId by mutableStateOf<String?>(null)
        private set
    var groups by mutableStateOf<List<BoardGroup>>(emptyList()) // Groupes de la liste choisie
        private set
    var groupId by mutableStateOf<String?>(null)
        private set
    var loadingGroups by mutableStateOf(false)
        private set
    var error by mutableStateOf<String?>(null)
        private set

    /** Groupe proposé quand ses groupes arrivent (s'il est dans la liste choisie). */
    private var preferredGroupId: String? = null

    /** Point de départ : la liste et le groupe proposés, ou les premiers s'ils n'existent plus. */
    fun start(preferredListId: String?, preferredGroupId: String?) {
        this.preferredGroupId = preferredGroupId
        chooseList(preferredListId, lists)?.let { selectList(it) }
    }

    /** La liste choisie change : on charge ses groupes, et on propose le groupe préféré ou le premier. */
    fun selectList(id: String) {
        listId = id
        groups = emptyList()
        groupId = null
        loadingGroups = true
        scope.launch {
            try {
                val loaded = api.board(id).groups
                if (listId == id) { // Pas une autre liste choisie entre-temps
                    groups = loaded
                    groupId = chooseGroup(preferredGroupId, loaded)
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

    /** « Liste › Groupe » du choix actuel, pour les messages. */
    val where: String
        get() = "${lists.firstOrNull { it.id == listId }?.name ?: ""} › ${groups.firstOrNull { it.id == groupId }?.name ?: ""}"

    companion object {
        /** La liste préférée si elle existe encore, sinon la première liste (null s'il n'y en a aucune). */
        fun chooseList(preferredListId: String?, lists: List<TodoList>): String? {
            if (preferredListId != null && lists.any { it.id == preferredListId }) return preferredListId
            return lists.firstOrNull()?.id
        }

        /** Le groupe préféré s'il est dans la liste choisie, sinon son premier groupe (null si elle n'en a pas). */
        fun chooseGroup(preferredGroupId: String?, groups: List<BoardGroup>): String? {
            if (preferredGroupId != null && groups.any { it.id == preferredGroupId }) return preferredGroupId
            return groups.firstOrNull()?.id
        }
    }
}

/** Les deux menus « Liste › Groupe », et l'erreur de chargement s'il y en a une. */
@Composable
fun PlacePickerRow(picker: PlacePicker) {
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
        Dropdown(
            options = picker.lists.map { it.id to it.name },
            selectedId = picker.listId,
            onSelect = picker::selectList,
            modifier = Modifier.weight(1f, fill = false),
        )
        Text("›", color = Palette.muted)
        Dropdown(
            options = picker.groups.map { it.id to it.name },
            selectedId = picker.groupId,
            onSelect = picker::selectGroup,
            placeholder = if (picker.loadingGroups) "…" else "Aucun groupe",
            modifier = Modifier.weight(1f, fill = false),
        )
    }
    picker.error?.let { Text(it, color = Palette.danger) }
}
