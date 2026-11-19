import { describe, expect, it } from 'vitest'
import { parseThemeChoice, resolveTheme } from './theme'

describe('parseThemeChoice', () => {
    it('reprend un choix enregistré', () => {
        expect(parseThemeChoice('light')).toBe('light')
        expect(parseThemeChoice('dark')).toBe('dark')
        expect(parseThemeChoice('system')).toBe('system')
    })

    it('suit le système sans choix ou avec une valeur abîmée', () => {
        expect(parseThemeChoice(null)).toBe('system')
        expect(parseThemeChoice('violet')).toBe('system')
    })
})

describe('resolveTheme', () => {
    it('« system » suit le mode de l\'ordinateur', () => {
        expect(resolveTheme('system', true)).toBe('dark')
        expect(resolveTheme('system', false)).toBe('light')
    })

    it('un choix explicite ignore le mode de l\'ordinateur', () => {
        expect(resolveTheme('light', true)).toBe('light')
        expect(resolveTheme('dark', false)).toBe('dark')
    })
})
