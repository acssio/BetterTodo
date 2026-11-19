package io.github.acssio.bettertodo

import io.github.acssio.bettertodo.ui.DueDates
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate

class DueDatesTest {
    private val today = LocalDate.of(2026, 11, 17)

    @Test
    fun joursProches() {
        assertEquals("Aujourd'hui", DueDates.format("2026-11-17", today))
        assertEquals("Demain", DueDates.format("2026-11-18", today))
        assertEquals("Hier", DueDates.format("2026-11-16", today))
    }

    @Test
    fun autresDates() {
        assertEquals("25 nov.", DueDates.format("2026-11-25", today))
        assertEquals("3 janv. 2027", DueDates.format("2027-01-03", today)) // Autre année : on l'écrit
    }

    @Test
    fun changementDeMois() {
        assertEquals("Demain", DueDates.format("2026-12-01", LocalDate.of(2026, 11, 30)))
    }

    @Test
    fun enRetard() {
        assertTrue(DueDates.isDueOrLate("2026-11-17", today))  // Aujourd'hui : en rouge aussi
        assertTrue(DueDates.isDueOrLate("2026-10-01", today))
        assertFalse(DueDates.isDueOrLate("2026-11-18", today))
    }

    @Test
    fun aujourdHuiEtDemain() {
        assertEquals("2026-11-17", DueDates.todayIso(today))
        assertEquals("2026-12-01", DueDates.tomorrowIso(LocalDate.of(2026, 11, 30)))
    }

    @Test
    fun allerRetourAvecLeCalendrier() {
        assertEquals(1795564800000L, DueDates.toPickerMillis("2026-11-25")) // 25/11/2026 00:00 UTC
        assertEquals("2026-11-25", DueDates.fromPickerMillis(DueDates.toPickerMillis("2026-11-25")))
    }

    @Test
    fun dateAvecHeure() {
        assertEquals("Aujourd'hui 14:30", DueDates.format("2026-11-17", "14:30", today))
        assertEquals("Demain", DueDates.format("2026-11-18", null, today))
    }

    @Test
    fun heureTapee() {
        assertEquals("14:30", DueDates.parseTime("14:30"))
        assertEquals("14:30", DueDates.parseTime("1430"))   // Clavier numérique, sans « : »
        assertEquals("09:30", DueDates.parseTime("930"))
        assertEquals("14:30", DueDates.parseTime("14h30"))
        assertEquals("14:00", DueDates.parseTime("14h"))
        assertEquals("09:00", DueDates.parseTime("9"))
        assertEquals("09:05", DueDates.parseTime(" 9:05 "))
        assertEquals("14:30", DueDates.parseTime("14.30"))
        assertEquals("00:00", DueDates.parseTime("0"))
    }

    @Test
    fun heureIncomprise() {
        assertNull(DueDates.parseTime(""))
        assertNull(DueDates.parseTime("25:00"))
        assertNull(DueDates.parseTime("12:60"))
        assertNull(DueDates.parseTime("midi"))
        assertNull(DueDates.parseTime("12345"))
        assertNull(DueDates.parseTime("1:2:3"))
        assertNull(DueDates.parseTime(":30"))
        assertNull(DueDates.parseTime("09:1")) // Une lettre effacée de « 09:15 » : refusée, pas 09:01
    }
}
