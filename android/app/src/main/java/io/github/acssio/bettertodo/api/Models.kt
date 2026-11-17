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
)

// --- Corps de requête et petites réponses ----------------------------------------

@Serializable
data class LoginRequest(val email: String, val password: String)

@Serializable
data class SetupStatus(@SerialName("needs_setup") val needsSetup: Boolean)

/** Corps des réponses d'erreur : {"error": "message"}. */
@Serializable
data class ErrorBody(val error: String)
