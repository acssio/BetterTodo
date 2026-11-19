package io.github.acssio.bettertodo.data

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.first

/** Fichier de préférences "session", privé à l'app (aucune autre app ne peut le lire). */
private val Context.dataStore by preferencesDataStore(name = "session")

/** Ce qui est gardé entre deux lancements de l'app. */
data class SavedSession(
    val serverUrl: String, // "" si jamais connecté
    val token: String?,    // null = déconnecté
)

/**
 * Garde l'adresse du serveur et le jeton de session (DataStore).
 * L'adresse reste après la déconnexion, pour pré-remplir l'écran de connexion.
 */
class SessionStore(context: Context) {
    private val dataStore = context.applicationContext.dataStore

    suspend fun load(): SavedSession {
        val prefs = dataStore.data.first()
        return SavedSession(prefs[SERVER_URL] ?: "", prefs[TOKEN])
    }

    suspend fun save(serverUrl: String, token: String) {
        dataStore.edit { prefs ->
            prefs[SERVER_URL] = serverUrl
            prefs[TOKEN] = token
        }
    }

    suspend fun clearToken() {
        dataStore.edit { prefs -> prefs.remove(TOKEN) }
    }

    private companion object {
        val SERVER_URL = stringPreferencesKey("server_url")
        val TOKEN = stringPreferencesKey("token")
    }
}
