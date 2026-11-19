import { describe, expect, it } from 'vitest'
import { selectRange } from './selection'

const order = ['a', 'b', 'c', 'd', 'e']

describe('selectRange', () => {
    it('sélectionne de l\'ancrage à la tâche cliquée, vers le bas', () => {
        expect(selectRange(order, 'b', 'd', ['b'])).toEqual(['b', 'c', 'd'])
    })

    it('marche aussi vers le haut', () => {
        expect(selectRange(order, 'd', 'b', ['d'])).toEqual(['d', 'b', 'c'])
    })

    it('garde la sélection existante et n\'ajoute pas de doublon', () => {
        expect(selectRange(order, 'b', 'e', ['a', 'c'])).toEqual(['a', 'c', 'b', 'd', 'e'])
    })

    it('sans ancrage : ajoute seulement la tâche cliquée', () => {
        expect(selectRange(order, null, 'c', [])).toEqual(['c'])
        expect(selectRange(order, null, 'c', ['c'])).toEqual(['c'])
    })

    it('ancrage dans une autre liste : ajoute seulement la tâche cliquée', () => {
        expect(selectRange(order, 'x', 'c', ['x'])).toEqual(['x', 'c'])
    })

    it('tâche cliquée inconnue : rien ne change', () => {
        expect(selectRange(order, 'a', 'z', ['a'])).toEqual(['a'])
    })

    it('Maj+clic sur l\'ancrage : rien de plus', () => {
        expect(selectRange(order, 'c', 'c', ['c'])).toEqual(['c'])
    })
})
