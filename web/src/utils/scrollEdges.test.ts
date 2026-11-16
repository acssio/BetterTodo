import { describe, expect, it } from 'vitest'
import { scrollEdges } from './scrollEdges'

describe('scrollEdges', () => {
    it('ne signale rien quand tout tient à l\'écran', () => {
        expect(scrollEdges(0, 1000, 1000)).toBe('none')
        expect(scrollEdges(0, 1000, 600)).toBe('none')
    })

    it('signale la droite au départ, la gauche à la fin', () => {
        expect(scrollEdges(0, 800, 2000)).toBe('right')
        expect(scrollEdges(1200, 800, 2000)).toBe('left')
    })

    it('signale les deux côtés au milieu', () => {
        expect(scrollEdges(500, 800, 2000)).toBe('both')
    })

    it('ignore les arrondis d\'un pixel', () => {
        expect(scrollEdges(0.5, 800, 800.6)).toBe('none')
        expect(scrollEdges(1199.5, 800, 2000)).toBe('left')
    })
})
