// =============================================================================
// Appui long au doigt (mode mobile) : machine d'état testée dans longPress.test.ts
// =============================================================================

export const LONG_PRESS = {
    delay: 500,     // ms à garder le doigt posé
    tolerance: 10,  // px : au-delà, c'est un défilement, pas un appui long
}

/**
 *   idle ──down──► pressing ──(500 ms sans bouger)──► fired ──click──► idle
 *                     │                                  (le clic qui suit est ignoré)
 *                     ├──move > 10 px──► idle  (défilement)
 *                     └──up avant 500 ms──► idle  (simple toucher : le clic passe)
 *
 * down() repart toujours de zéro : si un navigateur n'envoie pas de clic après un
 * appui long, l'état « fired » ne bloque pas le toucher suivant.
 */
export type LongPressState = 'idle' | 'pressing' | 'fired'

export class LongPress {
    state: LongPressState = 'idle'
    private onLongPress: () => void
    private timer: ReturnType<typeof setTimeout> | undefined
    private startX: number = 0
    private startY: number = 0

    constructor(onLongPress: () => void) {
        this.onLongPress = onLongPress
    }

    /** Doigt posé. */
    down(x: number, y: number): void {
        this.cancel()
        this.state = 'pressing'
        this.startX = x
        this.startY = y
        this.timer = setTimeout(() => {
            this.state = 'fired'
            this.onLongPress()
        }, LONG_PRESS.delay)
    }

    /** Doigt qui glisse : trop loin → c'est un défilement, on abandonne. */
    move(x: number, y: number): void {
        if (this.state !== 'pressing') return
        if (Math.hypot(x - this.startX, y - this.startY) > LONG_PRESS.tolerance) this.cancel()
    }

    /** Doigt levé (ou geste annulé par le navigateur). Après un appui long, on attend le clic à ignorer. */
    up(): void {
        if (this.state === 'pressing') this.cancel()
    }

    /** À appeler dans onClick : true = ce clic suit un appui long, il faut l'ignorer. */
    consumeClick(): boolean {
        const fired = this.state === 'fired'
        if (fired) this.state = 'idle'
        return fired
    }

    /** Retour au repos (aussi au démontage du composant). */
    cancel(): void {
        clearTimeout(this.timer)
        this.timer = undefined
        this.state = 'idle'
    }
}
