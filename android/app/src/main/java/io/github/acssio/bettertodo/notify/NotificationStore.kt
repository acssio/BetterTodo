package io.github.acssio.bettertodo.notify

import android.content.Context
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.core.stringSetPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map

/** Fichier de préférences "notifications", privé à l'app. */
private val Context.notificationStore by preferencesDataStore(name = "notifications")

/**
 * Ce que les notifications retiennent, par téléphone (8-7) :
 *  - les réglages (Paramètres › Notifications) ;
 *  - les rappels repoussés (« Dans 1 h ») : tâche → moment ;
 *  - les tâches dont une alarme est posée (pour annuler celles qui ne servent plus).
 */
class NotificationStore(context: Context) {
    private val store = context.applicationContext.notificationStore

    val settings: Flow<NotificationSettings> = store.data.map { prefs ->
        NotificationSettings(
            enabled = prefs[ENABLED] ?: true,
            offsetMinutes = prefs[OFFSET] ?: 0,
            summaryTime = prefs[SUMMARY] ?: "09:00",
        )
    }

    suspend fun save(settings: NotificationSettings) {
        store.edit { prefs ->
            prefs[ENABLED] = settings.enabled
            prefs[OFFSET] = settings.offsetMinutes
            prefs[SUMMARY] = settings.summaryTime
        }
    }

    /** Tâche → moment du report (millisecondes). */
    suspend fun snoozes(): Map<String, Long> {
        return (store.data.first()[SNOOZES] ?: emptySet()).mapNotNull { entry ->
            val (id, at) = entry.split('=').takeIf { it.size == 2 } ?: return@mapNotNull null
            at.toLongOrNull()?.let { id to it }
        }.toMap()
    }

    suspend fun snooze(taskId: String, until: Long) {
        store.edit { prefs ->
            val others = (prefs[SNOOZES] ?: emptySet()).filterNot { it.startsWith("$taskId=") }
            prefs[SNOOZES] = others.toSet() + "$taskId=$until"
        }
    }

    /** Garde seulement les reports encore à venir. */
    suspend fun keepSnoozesAfter(now: Long) {
        val kept = snoozes().filterValues { it > now }
        store.edit { prefs -> prefs[SNOOZES] = kept.map { "${it.key}=${it.value}" }.toSet() }
    }

    suspend fun scheduledIds(): Set<String> = store.data.first()[SCHEDULED] ?: emptySet()

    suspend fun saveScheduledIds(ids: Set<String>) {
        store.edit { prefs -> prefs[SCHEDULED] = ids }
    }

    private companion object {
        val ENABLED = booleanPreferencesKey("enabled")
        val OFFSET = intPreferencesKey("offset_minutes")
        val SUMMARY = stringPreferencesKey("summary_time")
        val SNOOZES = stringSetPreferencesKey("snoozes")
        val SCHEDULED = stringSetPreferencesKey("scheduled")
    }
}
