package io.github.acssio.bettertodo

import io.github.acssio.bettertodo.ui.DueDates
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
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
}
