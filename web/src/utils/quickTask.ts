// =============================================================================
// Quick task : quelle liste et quel groupe proposer, et la mémoire du dernier choix
// (testé dans quickTask.test.ts)
// =============================================================================

import type { Group, QuickTaskDue, TodoList } from '../api/types'
import { todayIso, tomorrowIso } from './dates'

const MEMORY_KEY = 'bettertodo.quickTask' // Dans le navigateur : retenu par appareil

/** Dernier endroit où une Quick task a été créée. */
export interface QuickTaskMemory {
    listId: string
    groupId: string
}

/** La liste retenue si elle existe encore, sinon la première liste (null s'il n'y en a aucune). */
export function chooseList(savedListId: string | null, lists: TodoList[]): string | null {
    if (savedListId && lists.some(list => list.id === savedListId)) return savedListId
    return lists[0]?.id ?? null
}

/** Le groupe retenu s'il est dans la liste choisie, sinon son premier groupe (null si elle n'en a pas). */
export function chooseGroup(savedGroupId: string | null, groups: Group[]): string | null {
    if (savedGroupId && groups.some(group => group.id === savedGroupId)) return savedGroupId
    return groups[0]?.id ?? null
}

/** Échéance proposée à l'ouverture de la Quick task, selon le réglage du compte (null = aucune). */
export function quickTaskDueDate(due: QuickTaskDue, today: Date = new Date()): string | null {
    if (due === 'today') return todayIso(today)
    if (due === 'tomorrow') return tomorrowIso(today)
    return null
}

/** Lit la mémoire enregistrée. Une valeur abîmée ou absente donne null. */
export function parseQuickTaskMemory(raw: string | null): QuickTaskMemory | null {
    if (!raw) return null
    try {
        const value = JSON.parse(raw)
        if (typeof value?.listId === 'string' && typeof value?.groupId === 'string') {
            return { listId: value.listId, groupId: value.groupId }
        }
    } catch {
        // Valeur abîmée : on l'ignore
    }
    return null
}

// localStorage peut être indisponible (navigation privée, stockage bloqué) : on fait alors sans mémoire.
export function readQuickTaskMemory(): QuickTaskMemory | null {
    try {
        return parseQuickTaskMemory(localStorage.getItem(MEMORY_KEY))
    } catch {
        return null
    }
}

export function writeQuickTaskMemory(memory: QuickTaskMemory): void {
    try {
        localStorage.setItem(MEMORY_KEY, JSON.stringify(memory))
    } catch {
        // Pas grave : la prochaine fois, on proposera la première liste
    }
}
