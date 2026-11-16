import { describe, expect, it } from 'vitest'
import type { Group, TodoList } from '../api/types'
import { chooseGroup, chooseList, parseQuickTaskMemory } from './quickTask'

const list = (id: string): TodoList => ({
    id, folder_id: null, name: id, color: '#2F6FEB', icon: 'list',
    default_view: 'columns', show_done: false, position: 0, open_tasks: 0,
})
const group = (id: string): Group => ({ id, list_id: 'l1', name: id, position: 0 })

describe('chooseList', () => {
    it('reprend la liste retenue si elle existe encore', () => {
        expect(chooseList('l2', [list('l1'), list('l2')])).toBe('l2')
    })

    it('prend la première liste si la liste retenue a été supprimée', () => {
        expect(chooseList('supprimée', [list('l1'), list('l2')])).toBe('l1')
    })

    it('prend la première liste sans mémoire', () => {
        expect(chooseList(null, [list('l1')])).toBe('l1')
    })

    it('renvoie null sans aucune liste', () => {
        expect(chooseList('l1', [])).toBeNull()
    })
})

describe('chooseGroup', () => {
    it('reprend le groupe retenu s\'il est dans la liste', () => {
        expect(chooseGroup('g2', [group('g1'), group('g2')])).toBe('g2')
    })

    it('prend le premier groupe sinon', () => {
        expect(chooseGroup('autre', [group('g1'), group('g2')])).toBe('g1')
        expect(chooseGroup(null, [group('g1')])).toBe('g1')
    })

    it('renvoie null si la liste n\'a pas de groupe', () => {
        expect(chooseGroup('g1', [])).toBeNull()
    })
})

describe('parseQuickTaskMemory', () => {
    it('lit une mémoire valide', () => {
        expect(parseQuickTaskMemory('{"listId":"l1","groupId":"g1"}')).toEqual({ listId: 'l1', groupId: 'g1' })
    })

    it('ignore une valeur absente, abîmée ou incomplète', () => {
        expect(parseQuickTaskMemory(null)).toBeNull()
        expect(parseQuickTaskMemory('pas du json')).toBeNull()
        expect(parseQuickTaskMemory('{"listId":"l1"}')).toBeNull()
        expect(parseQuickTaskMemory('null')).toBeNull()
    })
})
