import { describe, expect, it } from 'vitest'
import { isMobileLayout, keyboardInset } from './mobileLayout'

describe('isMobileLayout', () => {
    it('passe en mobile sur les téléphones visés (portrait)', () => {
        expect(isMobileLayout(375, 812, true)).toBe(true)  // iPhone 13 mini
        expect(isMobileLayout(412, 1004, true)).toBe(true) // Galaxy Z Flip 6 ouvert
        expect(isMobileLayout(440, 956, true)).toBe(true)  // iPhone 17 Pro Max
    })

    it('passe en mobile sur un téléphone à l\'horizontale', () => {
        expect(isMobileLayout(812, 375, true)).toBe(true)
        expect(isMobileLayout(956, 440, true)).toBe(true)
    })

    it('garde la barre latérale sur tablette et ordinateur', () => {
        expect(isMobileLayout(768, 1024, true)).toBe(false)  // Tablette
        expect(isMobileLayout(1024, 768, true)).toBe(false)  // Tablette à l'horizontale
        expect(isMobileLayout(1440, 900, false)).toBe(false) // Ordinateur
        expect(isMobileLayout(900, 450, false)).toBe(false)  // Fenêtre basse, mais souris
    })

    it('passe en mobile dans une fenêtre d\'ordinateur très étroite', () => {
        expect(isMobileLayout(599, 900, false)).toBe(true)
        expect(isMobileLayout(600, 900, false)).toBe(false)
    })
})

describe('keyboardInset', () => {
    it('vaut 0 sans clavier', () => {
        expect(keyboardInset(844, 844, 0)).toBe(0)
    })

    it('donne la hauteur du clavier', () => {
        expect(keyboardInset(844, 508, 0)).toBe(336)
    })

    it('tient compte de la page déjà remontée par le navigateur', () => {
        expect(keyboardInset(844, 508, 120)).toBe(216)
    })

    it('ne devient jamais négatif', () => {
        expect(keyboardInset(844, 850, 0)).toBe(0)
    })
})
