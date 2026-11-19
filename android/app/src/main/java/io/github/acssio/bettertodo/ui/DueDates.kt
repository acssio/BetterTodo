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

    /** Texte d'une échéance avec son heure : "Aujourd'hui 14:30" (comme DueDate dans TaskCard.tsx). */
    fun format(iso: String, time: String?, today: LocalDate = LocalDate.now()): String {
        return if (time == null) format(iso, today) else "${format(iso, today)} $time"
    }

    /**
     * Heure tapée → "HH:MM", ou null si on ne la comprend pas.
     * Le clavier numérique du téléphone n'a pas toujours « : », alors on accepte aussi
     * "1430", "930", "14h30", "14h", "9" et "14.30". Une chaîne vide n'est PAS une heure : null.
     * Après « : », les minutes ont 2 chiffres ("9:1" est refusé : on ne devine pas).
     */
    fun parseTime(input: String): String? {
        val text = input.trim().lowercase().replace('h', ':').replace('.', ':')
        val (hours, minutes) = when {
            text.contains(':') -> {
                val parts = text.split(':')
                if (parts.size != 2) return null
                if (parts[1].length == 1) return null  // "9:1" : 09:01 ou 09:10 ? On ne devine pas
                parts[0] to parts[1].ifEmpty { "0" } // "14:" ou "14h" → 14:00
            }
            text.length in 1..2 -> text to "0"                            // "9" → 09:00
            text.length in 3..4 -> text.dropLast(2) to text.takeLast(2)   // "930" → 09:30, "1430" → 14:30
            else -> return null
        }
        if (hours.isEmpty() || hours.length > 2 || minutes.length > 2) return null
        val h = hours.toIntOrNull() ?: return null
        val m = minutes.toIntOrNull() ?: return null
        if (h !in 0..23 || m !in 0..59) return null
        return "%02d:%02d".format(h, m)
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
