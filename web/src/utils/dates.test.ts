import { describe, expect, it } from 'vitest'
import { daysFromToday, formatDueDate, isDueOrLate } from './dates'

// « Aujourd'hui » fixé au 13 novembre 2026 à 23h30 (heure locale) :
// vérifie qu'on ne bascule pas au lendemain à cause de l'heure.
const today = new Date(2026, 10, 13, 23, 30)

describe('dates', () => {
    it('compte les jours depuis aujourd\'hui', () => {
        expect(daysFromToday('2026-11-13', today)).toBe(0)
        expect(daysFromToday('2026-11-14', today)).toBe(1)
        expect(daysFromToday('2026-11-12', today)).toBe(-1)
    })

    it('formate les échéances', () => {
        expect(formatDueDate('2026-11-13', today)).toBe("Aujourd'hui")
        expect(formatDueDate('2026-11-14', today)).toBe('Demain')
        expect(formatDueDate('2026-11-12', today)).toBe('Hier')
        expect(formatDueDate('2026-11-18', today)).toBe('18 nov.')
        expect(formatDueDate('2027-01-05', today)).toContain('2027')
    })

    it('signale le retard', () => {
        expect(isDueOrLate('2026-11-13', today)).toBe(true)
        expect(isDueOrLate('2026-11-01', today)).toBe(true)
        expect(isDueOrLate('2026-11-14', today)).toBe(false)
    })
})
