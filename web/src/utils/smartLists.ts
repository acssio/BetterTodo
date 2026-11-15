// =============================================================================
// Listes intelligentes « Aujourd'hui » et « Planifié » (étape 3d).
// Ce ne sont pas de vraies listes : des vues sur les tâches de TOUTES les listes.
// =============================================================================

import type { DueTask } from '../api/types'
import { daysFromToday, parseDate } from './dates'

export const SMART_TODAY = 'smart:today'
export const SMART_SCHEDULED = 'smart:scheduled'
export type SmartKind = typeof SMART_TODAY | typeof SMART_SCHEDULED

/** Cet identifiant de moitié désigne-t-il une liste intelligente (et non une vraie liste) ? */
export function isSmart(id: string): id is SmartKind {
    return id === SMART_TODAY || id === SMART_SCHEDULED
}

/** Une section de la vue : un titre et ses tâches. */
export interface DueSection {
    key: string
    title: string
    late: boolean // Titre en rouge
    tasks: DueTask[]
}

/**
 * Range les tâches (déjà triées par date par l'API) en sections :
 *   Aujourd'hui : « En retard », puis « Aujourd'hui »
 *   Planifié    : « En retard », « Aujourd'hui », « Demain », puis une section par date
 * Les sections vides ne sont pas renvoyées.
 */
export function groupDueTasks(tasks: DueTask[], kind: SmartKind, today: Date = new Date()): DueSection[] {
    const sections: DueSection[] = []

    for (const task of tasks) {
        if (!task.due_date) continue
        const days = daysFromToday(task.due_date, today)
        const key = days < 0 ? 'late' : kind === SMART_TODAY ? 'today' : task.due_date

        let section = sections.find(s => s.key === key)
        if (!section) {
            section = { key, title: sectionTitle(days, task.due_date, today), late: days < 0, tasks: [] }
            sections.push(section)
        }
        section.tasks.push(task)
    }
    return sections
}

/** « En retard », « Aujourd'hui », « Demain », ou « lun. 18 nov. » (avec l'année si ce n'est pas celle-ci). */
function sectionTitle(days: number, iso: string, today: Date): string {
    if (days < 0) return 'En retard'
    if (days === 0) return "Aujourd'hui"
    if (days === 1) return 'Demain'

    const date = parseDate(iso)
    const options: Intl.DateTimeFormatOptions = { weekday: 'short', day: 'numeric', month: 'short' }
    if (date.getFullYear() !== today.getFullYear()) options.year = 'numeric'
    return new Intl.DateTimeFormat('fr-FR', options).format(date)
}
