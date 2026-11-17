package io.github.acssio.bettertodo.api

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

// Données échangées avec l'API, comme web/src/api/types.ts.
// Seuls les champs utilisés par l'app sont déclarés : les autres sont ignorés (Json.ignoreUnknownKeys).
// @SerialName donne le nom JSON quand il diffère du nom Kotlin (display_name → displayName).

/** Le compte connecté (GET /me). */
@Serializable
data class User(
    val id: String,
    val email: String,
    @SerialName("display_name") val displayName: String,
    val language: String = "fr",                                                  // "fr" ou "en"
    @SerialName("is_admin") val isAdmin: Boolean = false,
    @SerialName("must_change_password") val mustChangePassword: Boolean = false, // Mot de passe donné par l'admin
)

/** Réponse de POST /auth/login : le jeton est à envoyer ensuite en "Authorization: Bearer". */
@Serializable
data class Session(
    val user: User,
    val token: String,
    @SerialName("expires_at") val expiresAt: String,
)

/** Un groupe de listes (dans la barre latérale du web). */
@Serializable
data class Folder(
    val id: String,
    val name: String,
    val position: Int,
    val collapsed: Boolean = false,
)

/** Une liste. Nommée TodoList pour ne pas se confondre avec kotlin.collections.List. */
@Serializable
data class TodoList(
    val id: String,
    @SerialName("folder_id") val folderId: String? = null, // null = hors groupe de listes
    val name: String,
    val color: String,                                     // "#RRGGBB"
    val icon: String,                                      // Nom d'icône Lucide, ex. "briefcase"
    val position: Int,
    @SerialName("open_tasks") val openTasks: Int = 0,      // Tâches pas encore faites
    @SerialName("default_view") val defaultView: String = VIEW_COLUMNS, // "columns" ou "sections"
    @SerialName("show_done") val showDone: Boolean = false, // false = tâches faites masquées
) {
    companion object {
        const val VIEW_COLUMNS = "columns"
        const val VIEW_SECTIONS = "sections"
    }
}

/** Une tâche. */
@Serializable
data class Task(
    val id: String,
    @SerialName("group_id") val groupId: String,
    val title: String,
    val notes: String = "",
    @SerialName("due_date") val dueDate: String? = null,     // "2026-11-18", ou null
    @SerialName("urgency_id") val urgencyId: String? = null, // null = pas d'urgence
    val done: Boolean = false,
    val position: Int,
    val tags: List<String> = emptyList(),                    // Noms des tags, triés
)

/** Un groupe d'une liste, avec ses tâches (dans GET /lists/{id}/board). */
@Serializable
data class BoardGroup(
    val id: String,
    val name: String,
    val position: Int,
    val tasks: List<Task> = emptyList(),
)

/** Réponse de GET /lists/{id}/board : tout ce qu'il faut pour afficher une liste. */
@Serializable
data class Board(
    val list: TodoList,
    val groups: List<BoardGroup>,
)

/** Niveau d'urgence (Paramètres du site). */
@Serializable
data class UrgencyLevel(
    val id: String,
    val name: String,
    val color: String,
    val rank: Int, // 0 = le moins urgent
)

/** La liste d'origine d'une tâche d'Aujourd'hui / Planifié. */
@Serializable
data class ListRef(
    val id: String,
    val name: String,
    val color: String,
    val icon: String,
)

/**
 * Ce que GET /tasks/due ajoute à chaque tâche : sa liste et le nom de son groupe.
 * Lu séparément du même JSON que Task (voir ApiClient.dueTasks), pour ne pas recopier tous les champs.
 */
@Serializable
data class DueInfo(
    val list: ListRef,
    @SerialName("group_name") val groupName: String,
)

/** Une tâche d'Aujourd'hui / Planifié, avec sa liste et son groupe d'origine. */
data class DueTask(val task: Task, val list: ListRef, val groupName: String)

/** GET /tasks/due/counts : compteurs des tuiles de l'accueil. */
@Serializable
data class DueCounts(
    val today: Int = 0,     // Échéance aujourd'hui ou en retard
    val scheduled: Int = 0, // Toutes les tâches avec une échéance
)

// --- Corps de requête et petites réponses ----------------------------------------

@Serializable
data class LoginRequest(val email: String, val password: String)

/**
 * POST /groups/{id}/tasks : le titre, et l'échéance pour la Quick task.
 * dueDate à null n'est pas envoyé (Json n'écrit pas les valeurs par défaut).
 */
@Serializable
data class CreateTaskRequest(
    val title: String,
    @SerialName("due_date") val dueDate: String? = null,
)

@Serializable
data class SetupStatus(@SerialName("needs_setup") val needsSetup: Boolean)

/** Corps des réponses d'erreur : {"error": "message"}. */
@Serializable
data class ErrorBody(val error: String)
