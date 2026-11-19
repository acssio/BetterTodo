package io.github.acssio.bettertodo.sync

import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

/** Texte de l'état de la synchronisation (Paramètres › Serveur). Fonction pure, testée. */
object SyncStatus {
    private val time = DateTimeFormatter.ofPattern("HH:mm", Locale.FRENCH)
    private val dayTime = DateTimeFormatter.ofPattern("d MMM 'à' HH:mm", Locale.FRENCH)

    /**
     * « Synchronisation… », « Synchronisé à 18:42 », « Synchronisé le 18 nov. à 09:05 », « Jamais synchronisé »,
     * suivi de « · 2 modifications en attente » s'il y en a.
     */
    fun describe(syncing: Boolean, lastSync: Long?, pending: Int, zone: ZoneId = ZoneId.systemDefault(), today: LocalDate = LocalDate.now(zone)): String {
        val state = when {
            syncing -> "Synchronisation…"
            lastSync == null -> "Jamais synchronisé"
            else -> {
                val at = Instant.ofEpochMilli(lastSync).atZone(zone)
                if (at.toLocalDate() == today) "Synchronisé à ${at.format(time)}" else "Synchronisé le ${at.format(dayTime)}"
            }
        }
        return when (pending) {
            0 -> state
            1 -> "$state · 1 modification en attente"
            else -> "$state · $pending modifications en attente"
        }
    }
}
