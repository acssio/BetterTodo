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
    val mode: String,      // MODE_LOCAL, MODE_SERVER, ou "" au tout premier lancement
)

/** L'app garde ses données sur le téléphone, sans serveur (8-3). */
const val MODE_LOCAL = "local"

/** L'app parle à un serveur (connexion avec email et mot de passe). */
const val MODE_SERVER = "server"

/**
 * Garde le mode (sans serveur / serveur), l'adresse du serveur et le jeton de session (DataStore).
 * L'adresse reste après la déconnexion, pour pré-remplir l'écran de connexion.
 */
class SessionStore(context: Context) {
    private val dataStore = context.applicationContext.dataStore

    suspend fun load(): SavedSession {
        val prefs = dataStore.data.first()
        val token = prefs[TOKEN]
        // Installation d'avant la 8-3 (pas de mode enregistré) : connectée à un serveur si elle a un jeton
        val mode = prefs[MODE] ?: if (token != null) MODE_SERVER else ""
        return SavedSession(prefs[SERVER_URL] ?: "", token, mode)
    }

    /** Connexion réussie : mode serveur. */
    suspend fun save(serverUrl: String, token: String) {
        dataStore.edit { prefs ->
            prefs[SERVER_URL] = serverUrl
            prefs[TOKEN] = token
            prefs[MODE] = MODE_SERVER
        }
    }

    /** « Utiliser sans serveur » : le jeton éventuel est oublié. */
    suspend fun useLocal() {
        dataStore.edit { prefs ->
            prefs.remove(TOKEN)
            prefs[MODE] = MODE_LOCAL
        }
    }

    suspend fun clearToken() {
        dataStore.edit { prefs -> prefs.remove(TOKEN) }
    }

    private companion object {
        val SERVER_URL = stringPreferencesKey("server_url")
        val TOKEN = stringPreferencesKey("token")
        val MODE = stringPreferencesKey("mode")
    }
}
