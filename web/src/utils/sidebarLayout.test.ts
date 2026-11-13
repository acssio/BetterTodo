import { describe, expect, it } from 'vitest'
import { clampSidebarWidth, SIDEBAR, sidebarMode, snapSidebarWidth } from './sidebarLayout'

describe('sidebarLayout', () => {
    it('choisit le mode selon la fenêtre et la largeur', () => {
        expect(sidebarMode(1440, 240)).toBe('expanded')
        expect(sidebarMode(1440, 60)).toBe('compact')
        expect(sidebarMode(700, 240)).toBe('narrow') // Fenêtre étroite : priorité
    })

    it('borne la largeur', () => {
        expect(clampSidebarWidth(10)).toBe(SIDEBAR.min)
        expect(clampSidebarWidth(9999)).toBe(SIDEBAR.max)
        expect(clampSidebarWidth(300.4)).toBe(300)
    })

    it('aimante sur le mode icônes au relâchement', () => {
        expect(snapSidebarWidth(120)).toBe(SIDEBAR.min)
        expect(snapSidebarWidth(200)).toBe(200)
    })
})
