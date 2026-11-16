import { describe, expect, it } from 'vitest'
import { AUTO_SCROLL, autoScrollSpeed, sideEdge } from './dragDrop'

describe('autoScrollSpeed', () => {
    // Zone de 100 à 700 px
    it('ne défile pas au milieu', () => {
        expect(autoScrollSpeed(400, 100, 700)).toBe(0)
        expect(autoScrollSpeed(100 + AUTO_SCROLL.edge, 100, 700)).toBe(0)
    })

    it('remonte près du haut, d\'autant plus vite qu\'on est près du bord', () => {
        const near = autoScrollSpeed(110, 100, 700)
        const far = autoScrollSpeed(150, 100, 700)
        expect(near).toBeLessThan(0)
        expect(far).toBeLessThan(0)
        expect(Math.abs(near)).toBeGreaterThan(Math.abs(far))
    })

    it('descend près du bas', () => {
        expect(autoScrollSpeed(690, 100, 700)).toBeGreaterThan(0)
    })

    it('ne dépasse jamais la vitesse maximale, même hors de la zone', () => {
        expect(autoScrollSpeed(0, 100, 700)).toBe(-AUTO_SCROLL.maxSpeed)
        expect(autoScrollSpeed(900, 100, 700)).toBe(AUTO_SCROLL.maxSpeed)
    })
})

describe('sideEdge', () => {
    it('repère les bords gauche et droit', () => {
        expect(sideEdge(5, 0, 375)).toBe(-1)
        expect(sideEdge(370, 0, 375)).toBe(1)
        expect(sideEdge(180, 0, 375)).toBe(0)
    })
})
