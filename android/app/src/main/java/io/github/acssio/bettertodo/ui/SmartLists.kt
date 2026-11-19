package io.github.acssio.bettertodo.ui

import com.composables.icons.lucide.R // Icônes de la bibliothèque icons-lucide-android
import io.github.acssio.bettertodo.api.DueTask
import io.github.acssio.bettertodo.api.SmartList
import io.github.acssio.bettertodo.api.UrgencyLevel
import io.github.acssio.bettertodo.local.SmartMatch
import io.github.acssio.bettertodo.api.Task
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.Locale

/**
 * Listes intelligentes « Aujourd'hui » et « Planifié » (comme utils/smartLists.ts et smartInfo.ts du site).
 * Ce ne sont pas de vraies listes : des vues sur les tâches de TOUTES les listes.
 */
enum class SmartKind(val title: String, val icon: Int, val color: String, val empty: String) {
    TODAY("Aujourd'hui", R.drawable.lucide_ic_sun, "#E8A33D", "Rien d'urgent : aucune tâche pour aujourd'hui."),
    SCHEDULED("Planifié", R.drawable.lucide_ic_calendar_days, "#2F6FEB", "Aucune tâche avec une échéance."),
}

/** Une section de la vue : un titre (en rouge si late, ou dans la couleur du niveau d'urgence) et ses tâches. */
data class DueSection(val key: String, val title: String, val late: Boolean, val tasks: List<DueTask>, val color: String? = null)

object SmartLists {
    private val dayTitle = DateTimeFormatter.ofPattern("EEE d MMM", Locale.FRENCH)          // "mar. 25 nov."
    private val dayTitleYear = DateTimeFormatter.ofPattern("EEE d MMM yyyy", Locale.FRENCH) // "dim. 3 janv. 2027"

    /**
     * La tâche a-t-elle sa place dans la vue ? Seulement les tâches à faire avec une échéance,
     * et pour « Aujourd'hui », une échéance aujourd'hui ou en retard.
     * Sert après une modification : une tâche cochée ou repoussée à demain quitte « Aujourd'hui ».
     */
    fun belongs(task: Task, kind: SmartKind, today: LocalDate = LocalDate.now()): Boolean {
        val due = task.dueDate ?: return false
        if (task.done) return false
        return kind == SmartKind.SCHEDULED || DueDates.daysFromToday(due, today) <= 0
    }

    /**
     * Range les tâches en sections (triées par date ; les sections vides ne sont pas renvoyées) :
     *   Aujourd'hui : « En retard », puis « Aujourd'hui »
     *   Planifié    : « En retard », « Aujourd'hui », « Demain », puis une section par date
     */
    fun sections(tasks: List<DueTask>, kind: SmartKind, today: LocalDate = LocalDate.now()): List<DueSection> {
        val sections = linkedMapOf<String, DueSection>() // Garde l'ordre d'arrivée, donc l'ordre des dates
        for (due in tasks.filter { it.task.dueDate != null }.sortedWith(compareBy({ it.task.dueDate }, { it.task.dueTime ?: "99:99" }))) {
            val iso = due.task.dueDate!!
            val days = DueDates.daysFromToday(iso, today)
            val key = when {
                days < 0 -> "late"
                kind == SmartKind.TODAY -> "today"
                else -> iso
            }
            val section = sections[key] ?: DueSection(key, sectionTitle(days, iso, today), late = days < 0, tasks = emptyList())
            sections[key] = section.copy(tasks = section.tasks + due)
        }
        return sections.values.toList()
    }

    /** « En retard », « Aujourd'hui », « Demain », ou « mar. 25 nov. » (avec l'année si ce n'est pas celle-ci). */
    private fun sectionTitle(days: Long, iso: String, today: LocalDate): String {
        return when {
            days < 0 -> "En retard"
            days == 0L -> "Aujourd'hui"
            days == 1L -> "Demain"
            else -> {
                val date = LocalDate.parse(iso)
                date.format(if (date.year == today.year) dayTitle else dayTitleYear)
            }
        }
    }

    // --- Listes intelligentes personnalisées (8-6), comme groupSmartTasks du site ---

    /**
     * Par tags : une seule section « À faire ». Par urgence : une section par niveau, dans l'ordre
     * des tâches (déjà triées du plus urgent au moins urgent), « Sans urgence » en dernier.
     */
    fun customSections(tasks: List<DueTask>, list: SmartList, levels: Map<String, UrgencyLevel>): List<DueSection> {
        if (tasks.isEmpty()) return emptyList()
        if (list.kind == SmartList.KIND_TAGS) return listOf(DueSection("all", "À faire", late = false, tasks = tasks))
        val sections = linkedMapOf<String, DueSection>()
        for (due in tasks) {
            val key = due.task.urgencyId ?: "none"
            val level = levels[key]
            val section = sections[key] ?: DueSection(key, level?.name ?: "Sans urgence", late = false, tasks = emptyList(), color = level?.color)
            sections[key] = section.copy(tasks = section.tasks + due)
        }
        return sections.values.toList()
    }

    /** La tâche a-t-elle encore sa place dans la liste perso ? (à faire, et elle correspond au filtre) */
    fun belongsCustom(task: Task, list: SmartList): Boolean {
        if (task.done) return false
        val tagNames = list.labels.map { it.removePrefix("#") }.toSet() // Les tâches donnent leurs tags par nom
        return SmartMatch.matches(list.kind, list.tagMatch, tagNames, list.urgencyIds.toSet(), task.tags.toSet(), task.urgencyId)
    }
}
