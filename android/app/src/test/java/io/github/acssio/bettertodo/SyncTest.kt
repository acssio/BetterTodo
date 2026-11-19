package io.github.acssio.bettertodo

import io.github.acssio.bettertodo.local.OutboxEntity
import io.github.acssio.bettertodo.sync.SyncEngine
import io.github.acssio.bettertodo.sync.SyncStatus
import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.LocalDate
import java.time.ZoneId
import java.time.ZonedDateTime

/** Fonctions pures de la synchronisation (8-5). Le reste est testé sur l'émulateur (SyncEngineTest). */
class SyncTest {
    private val a = "11111111-1111-1111-1111-111111111111"
    private val g = "22222222-2222-2222-2222-222222222222"
    private val t = "33333333-3333-3333-3333-333333333333"

    @Test
    fun idsEnAttente() {
        val ops = listOf(
            OutboxEntity(1, "PATCH", "/tasks/$a", """{"title":"x"}"""),
            OutboxEntity(2, "POST", "/groups/$g/tasks", """{"id":"$t","title":"y"}"""),
            OutboxEntity(3, "PATCH", "/me", """{"language":"en"}"""),
        )
        assertEquals(setOf(a, g, t), SyncEngine.pendingIds(ops))
        assertEquals(emptySet<String>(), SyncEngine.pendingIds(emptyList()))
    }

    @Test
    fun etat() {
        val zone = ZoneId.of("Europe/Paris")
        val today = LocalDate.of(2026, 11, 19)
        val at = ZonedDateTime.of(2026, 11, 19, 18, 42, 0, 0, zone).toInstant().toEpochMilli()
        val yesterday = ZonedDateTime.of(2026, 11, 18, 9, 5, 0, 0, zone).toInstant().toEpochMilli()

        assertEquals("Synchronisation…", SyncStatus.describe(true, at, 0, zone, today))
        assertEquals("Jamais synchronisé", SyncStatus.describe(false, null, 0, zone, today))
        assertEquals("Synchronisé à 18:42", SyncStatus.describe(false, at, 0, zone, today))
        assertEquals("Synchronisé le 18 nov. à 09:05", SyncStatus.describe(false, yesterday, 0, zone, today))
        assertEquals("Synchronisé à 18:42 · 1 modification en attente", SyncStatus.describe(false, at, 1, zone, today))
        assertEquals("Synchronisé à 18:42 · 3 modifications en attente", SyncStatus.describe(false, at, 3, zone, today))
    }
}
