import { describe, expect, it } from 'vitest'
import type { DueTask } from '../api/types'
import { todayIso } from './dates'
import { groupDueTasks, isSmart, SMART_SCHEDULED, SMART_TODAY } from './smartLists'

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
