package io.github.acssio.bettertodo.ui

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import io.github.acssio.bettertodo.api.ApiClient
import io.github.acssio.bettertodo.api.ApiError
import io.github.acssio.bettertodo.api.TodoList
import io.github.acssio.bettertodo.api.User
import io.github.acssio.bettertodo.data.QuickTaskMemory
import io.github.acssio.bettertodo.data.QuickTaskPlace
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import java.time.LocalDate

/**
 * Panneau « Quick task » de l'accueil (comme QuickTaskSheet et utils/quickTask.ts du site) :
 * la liste et le groupe (PlacePicker, préremplis avec ceux de la dernière Quick task), et l'échéance.
 * Le titre est gardé dans le panneau lui-même (rememberSaveable).
 *
 * Comme BoardModel, ce n'est pas un ViewModel : HomeViewModel en crée un à chaque ouverture.
 *
 * @param lists les listes de l'accueil, dans leur ordre
 * @param defaultDue réglage du compte (User.quickTaskDue) : "none", "today" ou "tomorrow"
 * @param onCreated appelé après une création réussie, avec « Liste › Groupe » pour le message
 */
class QuickTaskModel(
    private val api: ApiClient,
    lists: List<TodoList>,
    defaultDue: String,
    private val memory: QuickTaskMemory,
    private val scope: CoroutineScope,
    private val onUnauthorized: () -> Unit,
    private val onCreated: (where: String) -> Unit,
) {
    val place = PlacePicker(api, lists, scope, onUnauthorized)

    var dueDate by mutableStateOf(dueDateFor(defaultDue)) // Calculée à l'ouverture (Paramètres › Quick task)
        private set

    var busy by mutableStateOf(false) // Création en cours : bouton désactivé
        private set
    var error by mutableStateOf<String?>(null)
        private set

    init {
        scope.launch {
            val saved = memory.read()
            place.start(saved?.listId, saved?.groupId)
        }
    }

    /** @param date "AAAA-MM-JJ", ou null pour une tâche sans échéance */
    fun selectDueDate(date: String?) {
        dueDate = date
    }

    /** Peut-on créer ? (un titre, un groupe, et pas déjà en train de créer) */
    fun canCreate(title: String): Boolean = title.isNotBlank() && place.groupId != null && !busy

    /**
     * Crée la tâche. En cas de succès : la mémoire est mise à jour (seulement là, comme le site)
     * et onCreated ferme le panneau. En cas d'erreur : le panneau reste ouvert avec le message.
     */
    fun create(title: String) {
        val list = place.listId ?: return
        val group = place.groupId ?: return
        if (!canCreate(title)) return

        busy = true
        error = null
        scope.launch {
            try {
                api.createTask(group, title.trim(), dueDate)
                memory.save(QuickTaskPlace(list, group))
                onCreated(place.where)
            } catch (e: ApiError) {
                if (e.status == 401) onUnauthorized() else error = e.message
            }
            busy = false
        }
    }

    companion object {
        /** Réglage "none" / "today" / "tomorrow" → date proposée (comme quickTaskDueDate du site). */
        fun dueDateFor(setting: String, today: LocalDate = LocalDate.now()): String? = when (setting) {
            User.QUICK_DUE_NONE -> null
            User.QUICK_DUE_TOMORROW -> DueDates.tomorrowIso(today)
            else -> DueDates.todayIso(today) // "today", et toute valeur inconnue
        }
    }
}
