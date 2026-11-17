package io.github.acssio.bettertodo.ui

import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset
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

    /** Date du jour au format de l'API ("2026-11-17"), dans le fuseau du téléphone. */
    fun todayIso(today: LocalDate = LocalDate.now()): String = today.toString()

    fun tomorrowIso(today: LocalDate = LocalDate.now()): String = today.plusDays(1).toString()

    // Le calendrier Material (DatePicker) compte en millisecondes depuis 1970, à minuit **UTC**.
    // Passer par UTC des deux côtés évite le décalage d'un jour selon le fuseau horaire.

    /** "2026-11-18" → millisecondes pour DatePicker. */
    fun toPickerMillis(iso: String): Long {
        return LocalDate.parse(iso).atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli()
    }

    /** Millisecondes choisies dans DatePicker → "2026-11-18". */
    fun fromPickerMillis(millis: Long): String {
        return Instant.ofEpochMilli(millis).atZone(ZoneOffset.UTC).toLocalDate().toString()
    }
}
