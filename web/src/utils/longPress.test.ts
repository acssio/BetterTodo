import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import { LONG_PRESS, LongPress } from './longPress'

describe('LongPress', () => {
    let events: string[]
    let dragAllowed: boolean
    let press: LongPress

    beforeEach(() => {
        vi.useFakeTimers()
        events = []
        dragAllowed = true
        press = new LongPress({
            onArm: () => events.push('arm'),
            onLongPress: () => events.push('select'),
            canDrag: () => dragAllowed,
            onDragStart: (x, y) => events.push(`start ${x},${y}`),
            onDragMove: (x, y) => events.push(`move ${x},${y}`),
            onDragEnd: drop => events.push(drop ? 'drop' : 'cancel'),
        })
    })

    afterEach(() => {
        vi.useRealTimers()
    })

    it('appui long sans bouger : sélection au relâchement, et le clic qui suit est ignoré', () => {
        press.down(100, 100)
        vi.advanceTimersByTime(LONG_PRESS.delay)
        expect(events).toEqual(['arm'])          // Soulevée, pas encore sélectionnée
        expect(press.blocksScroll()).toBe(true)

        press.up()
        expect(events).toEqual(['arm', 'select'])
        expect(press.consumeClick()).toBe(true)  // Le clic du relâchement est ignoré…
        expect(press.consumeClick()).toBe(false) // …mais un seul
    })

    it('appui long puis glisser : déplacement, pas de sélection', () => {
        press.down(100, 100)
        vi.advanceTimersByTime(LONG_PRESS.delay)
        press.move(104, 103)                     // Petit tremblement : toujours soulevée
        press.move(100, 140)                     // Assez loin : on glisse
        press.move(100, 180)
        press.up()
        expect(events).toEqual(['arm', 'start 100,140', 'move 100,180', 'drop'])
        expect(press.consumeClick()).toBe(true)
    })

    it('glisser annulé par le navigateur : pas de dépôt', () => {
        press.down(100, 100)
        vi.advanceTimersByTime(LONG_PRESS.delay)
        press.move(100, 140)
        press.abort()
        expect(events).toEqual(['arm', 'start 100,140', 'cancel'])
        expect(press.state).toBe('idle')
    })

    it('sans glisser possible (listes intelligentes), bouger après l\'appui long garde la sélection', () => {
        dragAllowed = false
        press.down(100, 100)
        vi.advanceTimersByTime(LONG_PRESS.delay)
        press.move(100, 160)
        press.up()
        expect(events).toEqual(['arm', 'select'])
    })

    it('un simple toucher laisse passer le clic', () => {
        press.down(100, 100)
        vi.advanceTimersByTime(LONG_PRESS.delay - 100)
        press.up()
        vi.advanceTimersByTime(1000)
        expect(events).toEqual([])
        expect(press.consumeClick()).toBe(false)
        expect(press.blocksScroll()).toBe(false)
    })

    it('un défilement avant 500 ms annule tout', () => {
        press.down(100, 100)
        press.move(100, 100 + LONG_PRESS.tolerance + 1)
        vi.advanceTimersByTime(1000)
        expect(events).toEqual([])
        expect(press.state).toBe('idle')
    })

    it('un nouveau toucher repart de zéro si aucun clic n\'a suivi le geste', () => {
        press.down(100, 100)
        vi.advanceTimersByTime(LONG_PRESS.delay)
        press.up() // Pas de clic envoyé par le navigateur
        press.down(100, 100)
        press.up()
        expect(press.consumeClick()).toBe(false) // Le toucher suivant n'est pas bloqué
    })
})
