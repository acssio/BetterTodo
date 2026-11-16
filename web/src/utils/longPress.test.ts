import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import { LONG_PRESS, LongPress } from './longPress'

describe('LongPress', () => {
    let fired: number
    let press: LongPress

    beforeEach(() => {
        vi.useFakeTimers()
        fired = 0
        press = new LongPress(() => fired++)
    })

    afterEach(() => {
        vi.useRealTimers()
    })

    it('se déclenche après le délai, et ignore le clic qui suit', () => {
        press.down(100, 100)
        vi.advanceTimersByTime(LONG_PRESS.delay)
        expect(fired).toBe(1)
        expect(press.state).toBe('fired')

        press.up()
        expect(press.consumeClick()).toBe(true)  // Le clic du relâchement est ignoré…
        expect(press.consumeClick()).toBe(false) // …mais un seul
    })

    it('un simple toucher laisse passer le clic', () => {
        press.down(100, 100)
        vi.advanceTimersByTime(LONG_PRESS.delay - 100)
        press.up()
        vi.advanceTimersByTime(1000)
        expect(fired).toBe(0)
        expect(press.consumeClick()).toBe(false)
    })

    it('un défilement annule l\'appui long', () => {
        press.down(100, 100)
        press.move(100, 100 + LONG_PRESS.tolerance + 1)
        vi.advanceTimersByTime(1000)
        expect(fired).toBe(0)
        expect(press.state).toBe('idle')
    })

    it('un petit tremblement du doigt n\'annule pas', () => {
        press.down(100, 100)
        press.move(104, 103)
        vi.advanceTimersByTime(LONG_PRESS.delay)
        expect(fired).toBe(1)
    })

    it('un nouveau toucher repart de zéro si aucun clic n\'a suivi l\'appui long', () => {
        press.down(100, 100)
        vi.advanceTimersByTime(LONG_PRESS.delay)
        press.up() // Pas de clic envoyé par le navigateur
        press.down(100, 100)
        press.up()
        expect(press.consumeClick()).toBe(false) // Le toucher suivant n'est pas bloqué
    })
})
