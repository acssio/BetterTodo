package io.github.acssio.bettertodo.ui

import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.time.temporal.ChronoUnit
import java.util.Locale

/**
 * Dates d'échéance, comme web/src/utils/dates.ts.
 * L'API envoie "AAAA-MM-JJ", sans heure ni fuseau : LocalDate est exactement ça (une date du calendrier).
 * Chaque fonction reçoit "today" pour pouvoir être testée avec une date fixe.
 */
object DueDates {
    private val dayMonth = DateTimeFormatter.ofPattern("d MMM", Locale.FRENCH)          // "18 nov."
    private val dayMonthYear = DateTimeFormatter.ofPattern("d MMM yyyy", Locale.FRENCH) // "18 nov. 2027"

    /** Nombre de jours entre aujourd'hui et la date (0 = aujourd'hui, -1 = hier, 1 = demain). */
    fun daysFromToday(iso: String, today: LocalDate = LocalDate.now()): Long {
        return ChronoUnit.DAYS.between(today, LocalDate.parse(iso))
    }

    /** Texte court : "Aujourd'hui", "Demain", "Hier", "18 nov." ou "18 nov. 2027". */
    fun format(iso: String, today: LocalDate = LocalDate.now()): String {
        return when (daysFromToday(iso, today)) {
            0L -> "Aujourd'hui"
            1L -> "Demain"
            -1L -> "Hier"
            else -> {
                val date = LocalDate.parse(iso)
                if (date.year == today.year) date.format(dayMonth) else date.format(dayMonthYear)
            }
        }
    }

    /** Vrai si l'échéance est aujourd'hui ou déjà passée (affichée en rouge). */
    fun isDueOrLate(iso: String, today: LocalDate = LocalDate.now()): Boolean {
        return daysFromToday(iso, today) <= 0
    }
}
