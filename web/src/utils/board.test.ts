import { describe, expect, it } from 'vitest'
import type { Board, Task } from '../api/types'
import {
    appendTask, boardWithGroup, boardWithTask, countDoneTasks, countOpenTasks, findTask, hideDoneTasks, indexInGroup,
    moveTask, placeTask, removeTask, replaceTask, visibleToFullIndex,
} from './board'

/** Crée une tâche minimale pour les tests. */
function task(id: string, groupId: string, done = false): Task {
    return {
        id, group_id: groupId, title: id, notes: '', due_date: null, urgency_id: null,
        done, done_at: null, position: 0, tags: [], created_at: '', updated_at: '',
    }
}

/** Board de test : groupe g1 = [a, b, c], groupe g2 = [d] */
function makeBoard(): Board {
    return {
        list: {
            id: 'l', folder_id: null, name: 'L', color: '#000000', icon: 'list',
            default_view: 'columns', show_done: true, position: 0, open_tasks: 4,
        },
        groups: [
            { id: 'g1', list_id: 'l', name: 'G1', position: 0, tasks: [task('a', 'g1'), task('b', 'g1'), task('c', 'g1')] },
            { id: 'g2', list_id: 'l', name: 'G2', position: 1, tasks: [task('d', 'g2', true)] },
        ],
    }
}

const ids = (board: Board, groupIndex: number) => board.groups[groupIndex].tasks.map(t => t.id)

describe('moveTask', () => {
    it('déplace vers le bas dans le même groupe', () => {
        // a en position 2 : sans a → [b, c], insertion en 2 → [b, c, a]
        expect(ids(moveTask(makeBoard(), 'a', 'g1', 2), 0)).toEqual(['b', 'c', 'a'])
    })

    it('déplace vers le haut dans le même groupe', () => {
        expect(ids(moveTask(makeBoard(), 'c', 'g1', 0), 0)).toEqual(['c', 'a', 'b'])
    })

    it('déplace dans un autre groupe et met à jour group_id', () => {
        const board = moveTask(makeBoard(), 'b', 'g2', 0)
        expect(ids(board, 0)).toEqual(['a', 'c'])
        expect(ids(board, 1)).toEqual(['b', 'd'])
        expect(findTask(board, 'b')?.group_id).toBe('g2')
    })

    it('un index trop grand met à la fin', () => {
        expect(ids(moveTask(makeBoard(), 'a', 'g2', 99), 1)).toEqual(['d', 'a'])
    })

    it('ne modifie pas le board de départ', () => {
        const board = makeBoard()
        moveTask(board, 'a', 'g2', 0)
        expect(ids(board, 0)).toEqual(['a', 'b', 'c'])
    })
})

describe('autres fonctions', () => {
    it('replaceTask garde la place', () => {
        const board = replaceTask(makeBoard(), { ...task('b', 'g1'), title: 'B modifié' })
        expect(board.groups[0].tasks[1].title).toBe('B modifié')
    })

    it('removeTask et appendTask', () => {
        expect(ids(removeTask(makeBoard(), 'b'), 0)).toEqual(['a', 'c'])
        expect(ids(appendTask(makeBoard(), task('e', 'g2')), 1)).toEqual(['d', 'e'])
    })

    it('indexInGroup', () => {
        expect(indexInGroup(makeBoard(), 'c')).toBe(2)
        expect(indexInGroup(makeBoard(), 'inconnue')).toBe(-1)
    })

    it('countOpenTasks ignore les tâches faites', () => {
        expect(countOpenTasks(makeBoard())).toBe(3)
    })
})

describe('tâches faites masquées', () => {
    // a, x (faite), b, y (faite), c
    const tasks = [task('a', 'g'), task('x', 'g', true), task('b', 'g'), task('y', 'g', true), task('c', 'g')]

    it('hideDoneTasks et countDoneTasks', () => {
        expect(ids(hideDoneTasks(makeBoard()), 1)).toEqual([])
        expect(countDoneTasks(makeBoard())).toBe(1)
    })

    it('convertit un index visible en index réel', () => {
        // On déplace c. Visibles sans c : [a, b] ; toutes sans c : [a, x, b, y]
        expect(visibleToFullIndex(tasks, 'c', 0)).toBe(0) // avant a
        expect(visibleToFullIndex(tasks, 'c', 1)).toBe(2) // avant b (donc après x)
        expect(visibleToFullIndex(tasks, 'c', 2)).toBe(3) // après b (avant y)
    })

    it('groupe sans tâche visible : en tête', () => {
        expect(visibleToFullIndex([task('x', 'g', true)], 'z', 0)).toBe(0)
    })
})

describe('vue scindée : placeTask entre deux boards', () => {
    // Deuxième board : liste m, groupe h = [e]
    function otherBoard(): Board {
        const board = makeBoard()
        return {
            list: { ...board.list, id: 'm' },
            groups: [{ id: 'h', list_id: 'm', name: 'H', position: 0, tasks: [task('e', 'h')] }],
        }
    }

    it('retire la tâche du board source et l\'insère dans le board cible', () => {
        const source = makeBoard()
        const moving = findTask(source, 'b')!
        const newSource = placeTask(source, moving, 'h', 0)
        const newTarget = placeTask(otherBoard(), moving, 'h', 0)

        expect(ids(newSource, 0)).toEqual(['a', 'c'])
        expect(ids(newTarget, 0)).toEqual(['b', 'e'])
        expect(findTask(newTarget, 'b')?.group_id).toBe('h')
    })

    it('boardWithGroup et boardWithTask', () => {
        const boards = [makeBoard(), otherBoard()]
        expect(boardWithGroup(boards, 'h')?.list.id).toBe('m')
        expect(boardWithTask(boards, 'a')?.list.id).toBe('l')
        expect(boardWithTask(boards, 'zz')).toBeUndefined()
    })
})
