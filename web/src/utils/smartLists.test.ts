import { describe, expect, it } from 'vitest'
import type { DueTask, SmartList, UrgencyLevel } from '../api/types'
import { todayIso } from './dates'
import {
    canSaveSmartList, customSmartId, groupDueTasks, groupSmartTasks, isBuiltInSmart, isSmart, smartListSummary, smartPaneId,
    SMART_SCHEDULED, SMART_TODAY,
} from './smartLists'

const today = new Date(2026, 10, 15, 23, 30) // 15 novembre 2026, tard le soir

function due(title: string, date: string): DueTask {
    return {
        id: title, group_id: 'g', title, notes: '', due_date: date, due_time: null, urgency_id: null, done: false, done_at: null,
        position: 0, tags: [], created_at: '', updated_at: '',
        list: { id: 'l', name: 'L', color: '#000000', icon: 'list' }, group_name: 'G',
    }
}

// Déjà triées par date, comme les renvoie l'API
const tasks = [due('a', '2026-11-01'), due('b', '2026-11-14'), due('c', '2026-11-15'), due('d', '2026-11-16'),
    due('e', '2026-11-18'), due('f', '2026-11-18'), due('g', '2027-01-05')]

describe('groupDueTasks', () => {
    it('Aujourd\'hui : En retard puis Aujourd\'hui', () => {
        const sections = groupDueTasks(tasks.slice(0, 3), SMART_TODAY, today)
        expect(sections.map(s => [s.title, s.tasks.map(t => t.id).join('')])).toEqual([
            ['En retard', 'ab'],
            ["Aujourd'hui", 'c'],
        ])
        expect(sections[0].late).toBe(true)
    })

    it('Planifié : une section par date', () => {
        const titles = groupDueTasks(tasks, SMART_SCHEDULED, today).map(s => s.title)
        expect(titles[0]).toBe('En retard')
        expect(titles[1]).toBe("Aujourd'hui")
        expect(titles[2]).toBe('Demain')
        expect(titles[3]).toMatch(/18 nov/)   // mer. 18 nov. : e et f ensemble
        expect(titles[4]).toMatch(/2027/)     // autre année : l'année est affichée
        expect(titles).toHaveLength(5)
    })
})

describe('outils', () => {
    it('isSmart et todayIso', () => {
        expect(isSmart(SMART_TODAY)).toBe(true)
        expect(isSmart('8f0c…')).toBe(false)
        expect(todayIso(today)).toBe('2026-11-15')
        expect(todayIso(new Date(2026, 0, 5))).toBe('2026-01-05')
    })
})

const smartList = (kind: 'tags' | 'urgency', labels: string[], match: 'any' | 'all' = 'any'): SmartList => ({
    id: 's1', name: 'S', color: '#8250DF', icon: 'tag', kind, tag_match: match, tag_ids: [], urgency_ids: [],
    labels, visible: true, position: 0, open_tasks: 0,
})
const levels: UrgencyLevel[] = [
    { id: 'low', name: 'Basse', color: '#3FA66B', rank: 0 },
    { id: 'high', name: 'Haute', color: '#E5484D', rank: 2 },
]
const withUrgency = (title: string, urgency: string): DueTask => ({ ...due(title, '2026-11-20'), urgency_id: urgency })

describe('listes intelligentes perso', () => {
    it('identifiants de moitié', () => {
        expect(isSmart(smartPaneId('abc'))).toBe(true)
        expect(customSmartId(smartPaneId('abc'))).toBe('abc')
        expect(customSmartId(SMART_TODAY)).toBeNull()
        expect(customSmartId('8f0c…')).toBeNull()
        expect(isBuiltInSmart(smartPaneId('abc'))).toBe(false)
    })

    it('par tags : une seule section', () => {
        const sections = groupSmartTasks([due('a', '2026-11-20'), due('b', '2026-11-21')], smartList('tags', ['#boulot']), levels)
        expect(sections.map(s => [s.title, s.tasks.length])).toEqual([['À faire', 2]])
        expect(groupSmartTasks([], smartList('tags', ['#boulot']), levels)).toEqual([])
    })

    it('par urgence : une section par niveau, dans l\'ordre de l\'API', () => {
        const tasks = [withUrgency('a', 'high'), withUrgency('b', 'high'), withUrgency('c', 'low')]
        const sections = groupSmartTasks(tasks, smartList('urgency', ['Haute', 'Basse']), levels)
        expect(sections.map(s => [s.title, s.color, s.tasks.map(t => t.id).join('')])).toEqual([
            ['Haute', '#E5484D', 'ab'],
            ['Basse', '#3FA66B', 'c'],
        ])
    })

    it('sous-titre', () => {
        expect(smartListSummary(smartList('urgency', ['Haute', 'Moyenne']))).toBe('urgence Haute ou Moyenne')
        expect(smartListSummary(smartList('tags', ['#boulot', '#réunion']))).toBe('#boulot ou #réunion')
        expect(smartListSummary(smartList('tags', ['#boulot', '#réunion'], 'all'))).toBe('#boulot et #réunion')
    })
})

describe('canSaveSmartList', () => {
    it('demande un nom et au moins un tag ou un niveau, selon le type', () => {
        expect(canSaveSmartList('Travail', 'tags', ['t1'], [])).toBe(true)
        expect(canSaveSmartList('Urgent', 'urgency', [], ['u1'])).toBe(true)
        expect(canSaveSmartList('   ', 'tags', ['t1'], [])).toBe(false)
        expect(canSaveSmartList('Travail', 'tags', [], ['u1'])).toBe(false)
        expect(canSaveSmartList('Urgent', 'urgency', ['t1'], [])).toBe(false)
    })
})
