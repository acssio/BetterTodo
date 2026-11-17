package io.github.acssio.bettertodo.data

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.first

/** Fichier de préférences "quick_task", privé à l'app. */
private val Context.quickTaskStore by preferencesDataStore(name = "quick_task")

/** Dernier endroit où une Quick task a été créée. */
data class QuickTaskPlace(val listId: String, val groupId: String)

/**
 * Mémoire de la Quick task : la liste et le groupe de la dernière création réussie.
 * Retenue par téléphone (comme bettertodo.quickTask dans le navigateur pour le site),
 * et gardée après la déconnexion : les ids d'un autre compte ne correspondent à rien,
 * on retombe alors sur la première liste (QuickTaskModel.chooseList).
 */
class QuickTaskMemory(context: Context) {
    private val store = context.applicationContext.quickTaskStore

    suspend fun read(): QuickTaskPlace? {
        val prefs = store.data.first()
        val listId = prefs[LIST_ID] ?: return null
        val groupId = prefs[GROUP_ID] ?: return null
        return QuickTaskPlace(listId, groupId)
    }

    suspend fun save(place: QuickTaskPlace) {
        store.edit { prefs ->
            prefs[LIST_ID] = place.listId
            prefs[GROUP_ID] = place.groupId
        }
    }

    private companion object {
        val LIST_ID = stringPreferencesKey("list_id")
        val GROUP_ID = stringPreferencesKey("group_id")
    }
}
