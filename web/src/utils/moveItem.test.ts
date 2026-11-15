import { describe, expect, it } from 'vitest'
import { moveItem } from './moveItem'

const items = ['a', 'b', 'c', 'd'].map(id => ({ id }))
const ids = (list: { id: string }[]) => list.map(item => item.id)

describe('moveItem', () => {
    it('déplace vers le bas', () => {
        expect(ids(moveItem(items, 'a', 2))).toEqual(['b', 'c', 'a', 'd'])
    })

    it('déplace vers le haut', () => {
        expect(ids(moveItem(items, 'd', 0))).toEqual(['d', 'a', 'b', 'c'])
    })

    it('un index trop grand met à la fin, un id inconnu ne change rien', () => {
        expect(ids(moveItem(items, 'b', 99))).toEqual(['a', 'c', 'd', 'b'])
        expect(moveItem(items, 'z', 0)).toBe(items)
    })
})
