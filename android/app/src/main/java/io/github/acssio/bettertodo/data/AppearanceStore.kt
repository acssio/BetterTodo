package io.github.acssio.bettertodo.data

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import io.github.acssio.bettertodo.ui.theme.ThemeMode
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

/** Fichier de préférences "appearance", privé à l'app. */
private val Context.appearanceStore by preferencesDataStore(name = "appearance")

/**
 * Réglage Apparence (Système / Clair / Sombre), retenu par téléphone
 * (comme bettertodo.theme dans le navigateur pour le site), gardé après la déconnexion.
 */
class AppearanceStore(context: Context) {
    private val store = context.applicationContext.appearanceStore

    /** Le mode enregistré, mis à jour dès qu'il change : MainActivity le suit pour redessiner l'app. */
    val mode: Flow<ThemeMode> = store.data.map { prefs -> ThemeMode.fromKey(prefs[THEME]) }

    suspend fun save(mode: ThemeMode) {
        store.edit { prefs -> prefs[THEME] = mode.key }
    }

    private companion object {
        val THEME = stringPreferencesKey("theme")
    }
}
