// =============================================================================
// Listes intelligentes : « Aujourd'hui » et « Planifié » (étape 3d), et les listes
// personnalisées par tags ou par urgence (étape 7-5).
// Ce ne sont pas de vraies listes : des vues sur les tâches de TOUTES les listes.
//
// Identifiant d'une moitié d'écran (Pane.listId) :
//   vraie liste → son id ; Aujourd'hui → 'smart:today' ; Planifié → 'smart:scheduled' ;
//   liste intelligente perso → 'smart:<id>' (smartPaneId).
// =============================================================================

import type { DueTask, SmartList, UrgencyLevel } from '../api/types'
import { daysFromToday, parseDate } from './dates'

const SMART_PREFIX = 'smart:'
export const SMART_TODAY = 'smart:today'
export const SMART_SCHEDULED = 'smart:scheduled'
export type SmartKind = typeof SMART_TODAY | typeof SMART_SCHEDULED

/** Cet identifiant de moitié désigne-t-il une liste intelligente (intégrée ou perso) ? */
export function isSmart(id: string): boolean {
    return id.startsWith(SMART_PREFIX)
}

/** « Aujourd'hui » ou « Planifié » ? */
export function isBuiltInSmart(id: string): id is SmartKind {
    return id === SMART_TODAY || id === SMART_SCHEDULED
}

/** Identifiant de moitié d'une liste intelligente perso. */
export function smartPaneId(smartListId: string): string {
    return SMART_PREFIX + smartListId
}

/** Id de la liste intelligente perso affichée dans cette moitié (null si ce n'en est pas une). */
export function customSmartId(paneId: string): string | null {
    return isSmart(paneId) && !isBuiltInSmart(paneId) ? paneId.slice(SMART_PREFIX.length) : null
}

/** Une section de la vue : un titre et ses tâches. */
export interface DueSection {
    key: string
    title: string
    late: boolean   // Titre en rouge
    color?: string  // Couleur du titre (niveau d'urgence)
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

/**
 * Range les tâches d'une liste intelligente perso (déjà triées par l'API) en sections :
 *   par tags    : une seule section ;
 *   par urgence : une section par niveau, du plus urgent au moins urgent (ordre de l'API).
 */
export function groupSmartTasks(tasks: DueTask[], list: SmartList, levels: UrgencyLevel[]): DueSection[] {
    if (list.kind === 'tags') {
        return tasks.length > 0 ? [{ key: 'all', title: 'À faire', late: false, tasks }] : []
    }

    const sections: DueSection[] = []
    for (const task of tasks) {
        const key = task.urgency_id ?? 'none'
        let section = sections.find(s => s.key === key)
        if (!section) {
            const level = levels.find(l => l.id === task.urgency_id)
            section = { key, title: level?.name ?? 'Sans urgence', late: false, color: level?.color, tasks: [] }
            sections.push(section)
        }
        section.tasks.push(task)
    }
    return sections
}

/** Sections d'une moitié « liste intelligente », intégrée ou perso (même ordre pour l'écran et le Maj+clic). */
export function smartSections(paneId: string, tasks: DueTask[], list: SmartList | undefined, levels: UrgencyLevel[]): DueSection[] {
    if (isBuiltInSmart(paneId)) return groupDueTasks(tasks, paneId)
    return list ? groupSmartTasks(tasks, list, levels) : []
}

/** Sous-titre d'une liste intelligente perso : « urgence Haute ou Moyenne », « #boulot et #réunion ». */
export function smartListSummary(list: SmartList): string {
    if (list.kind === 'urgency') return 'urgence ' + list.labels.join(' ou ')
    return list.labels.join(list.tag_match === 'all' ? ' et ' : ' ou ')
}

/** Le formulaire d'une liste intelligente peut-il être enregistré ? (mêmes règles que l'API) */
export function canSaveSmartList(name: string, kind: 'tags' | 'urgency', tagIds: string[], urgencyIds: string[]): boolean {
    if (name.trim() === '') return false
    return kind === 'tags' ? tagIds.length > 0 : urgencyIds.length > 0
}
