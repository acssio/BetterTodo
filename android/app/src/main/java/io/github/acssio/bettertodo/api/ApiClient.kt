package io.github.acssio.bettertodo.api

import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.io.IOException

/**
 * Erreur d'un appel à l'API.
 * @param status code HTTP (401, 404…), ou 0 si le serveur n'a pas répondu (réseau, adresse fausse)
 */
class ApiError(val status: Int, message: String) : Exception(message)

/**
 * Tous les appels à l'API passent par cette classe, comme ApiClient.ts dans la page web.
 *
 * Une méthode par route. Chaque méthode est « suspend » : elle s'exécute sans bloquer
 * l'écran, et le réseau tourne sur un fil à part (Dispatchers.IO).
 *
 * @param serverUrl adresse du serveur, déjà normalisée (voir ServerUrl.normalize)
 * @param token jeton de session, ou null avant la connexion
 */
class ApiClient(private val serverUrl: String, private val token: String? = null) {

    // --- Comptes -------------------------------------------------------------

    suspend fun login(email: String, password: String): Session {
        val body = json.encodeToString(LoginRequest(email, password))
        return json.decodeFromString(send("POST", "/auth/login", body))
    }

    /** true tant qu'aucun compte n'existe : il faut créer le compte administrateur (depuis le site). */
    suspend fun needsSetup(): Boolean {
        return json.decodeFromString<SetupStatus>(send("GET", "/auth/setup")).needsSetup
    }

    suspend fun me(): User {
        return json.decodeFromString(send("GET", "/me"))
    }

    /** Ferme la session côté serveur : le jeton ne marche plus. */
    suspend fun logout() {
        send("POST", "/auth/logout")
    }

    // --- Groupes de listes et listes -----------------------------------------

    suspend fun folders(): List<Folder> {
        return json.decodeFromString(send("GET", "/folders"))
    }

    suspend fun lists(): List<TodoList> {
        return json.decodeFromString(send("GET", "/lists"))
    }

    /** La liste, ses groupes et toutes leurs tâches (faites comprises). */
    suspend fun board(listId: String): Board {
        return json.decodeFromString(send("GET", "/lists/$listId/board"))
    }

    // --- Tâches --------------------------------------------------------------

    /** Crée une tâche à la fin du groupe. */
    suspend fun createTask(groupId: String, title: String): Task {
        val body = json.encodeToString(CreateTaskRequest(title))
        return json.decodeFromString(send("POST", "/groups/$groupId/tasks", body))
    }

    /** Modifie une tâche : seuls les champs de changes sont envoyés (cocher compris). */
    suspend fun updateTask(taskId: String, changes: TaskChanges): Task {
        return json.decodeFromString(send("PATCH", "/tasks/$taskId", changes.toJson()))
    }

    suspend fun deleteTask(taskId: String) {
        send("DELETE", "/tasks/$taskId")
    }

    // --- Paramètres ----------------------------------------------------------

    suspend fun urgencyLevels(): List<UrgencyLevel> {
        return json.decodeFromString(send("GET", "/urgency-levels"))
    }

    // --- Envoi d'une requête -------------------------------------------------

    /**
     * Envoie la requête et retourne le corps de la réponse (texte JSON).
     * @param body JSON à envoyer, ou null pour une requête sans corps
     * @throws ApiError si le serveur répond une erreur (4xx, 5xx) ou ne répond pas
     */
    private suspend fun send(method: String, path: String, body: String? = null): String = withContext(Dispatchers.IO) {
        val builder = Request.Builder().url("$serverUrl/api/v1$path")
        if (token != null) {
            builder.header("Authorization", "Bearer $token")
        }
        // OkHttp exige un corps pour POST, même vide
        val requestBody = body?.toRequestBody(jsonType) ?: if (method == "GET") null else "".toRequestBody(null)
        builder.method(method, requestBody)

        try {
            http.newCall(builder.build()).execute().use { response ->
                val text = response.body.string()
                if (!response.isSuccessful) {
                    throw ApiError(response.code, errorMessage(text, response.code))
                }
                text
            }
        } catch (e: IOException) {
            Log.w(TAG, "$method $path : pas de réponse", e) // La vraie cause, visible dans Logcat
            throw ApiError(0, "Serveur injoignable : vérifie l'adresse et ta connexion")
        } catch (e: IllegalArgumentException) {
            Log.w(TAG, "$method $path : adresse invalide", e)
            throw ApiError(0, "Adresse du serveur invalide") // URL que OkHttp ne sait pas lire
        }
    }

    /** Le message de {"error": "..."}, ou un message générique si la réponse n'est pas du JSON. */
    private fun errorMessage(text: String, status: Int): String {
        return try {
            json.decodeFromString<ErrorBody>(text).error
        } catch (e: Exception) {
            "Erreur $status"
        }
    }

    companion object {
        // Partagés par tous les ApiClient : OkHttp garde les connexions ouvertes pour les réutiliser
        private const val TAG = "ApiClient" // Filtre Logcat : tag:ApiClient
        private val http = OkHttpClient()
        private val jsonType = "application/json".toMediaType()

        /** ignoreUnknownKeys : l'API envoie plus de champs que ceux déclarés dans Models.kt */
        val json = Json { ignoreUnknownKeys = true }
    }
}
