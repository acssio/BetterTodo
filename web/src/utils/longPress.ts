// =============================================================================
// Appui long au doigt (mode mobile) : machine d'état testée dans longPress.test.ts
// =============================================================================

export const LONG_PRESS = {
    delay: 500,     // ms à garder le doigt posé
    tolerance: 10,  // px : avant l'appui long, au-delà c'est un défilement ; après, c'est un glisser
}

/**
 *   idle ──down──► pressing ──(500 ms sans bouger)──► armed ──up sans bouger──► consumed  (sélection)
 *                     │                                 │
 *                     │                                 └──move > 10 px──► dragging ──up──► consumed  (dépôt)
 *                     ├──move > 10 px──► idle  (le doigt fait défiler)
 *                     └──up avant 500 ms──► idle  (simple toucher : le clic passe)
 *
 *   consumed : le clic qui suit le relâchement est ignoré (consumeClick), puis retour à idle.
 *   abort() (pointercancel) : retour à idle ; un glisser en cours est annulé sans dépôt.
 *
 * down() repart toujours de zéro : si un navigateur n'envoie pas de clic après le geste,
 * l'état « consumed » ne bloque pas le toucher suivant.
 */
export type LongPressState = 'idle' | 'pressing' | 'armed' | 'dragging' | 'consumed'

/** Ce que fait le composant à chaque étape. */
export interface LongPressHandlers {
    onArm: () => void                   // 500 ms : la tâche se soulève
    onLongPress: () => void             // Relâché sans bouger : sélection
    canDrag: () => boolean              // false : bouger après l'appui long ne glisse pas (listes intelligentes)
    onDragStart: (x: number, y: number) => void
    onDragMove: (x: number, y: number) => void
    onDragEnd: (drop: boolean) => void  // drop = false : glisser annulé (pointercancel)
}

export class LongPress {
    state: LongPressState = 'idle'
    private handlers: LongPressHandlers
    private timer: ReturnType<typeof setTimeout> | undefined
    private startX: number = 0
    private startY: number = 0

    constructor(handlers: LongPressHandlers) {
        this.handlers = handlers
    }

    /** Doigt posé. */
    down(x: number, y: number): void {
        this.abort()
        this.state = 'pressing'
        this.startX = x
        this.startY = y
        this.timer = setTimeout(() => {
            this.state = 'armed'
            this.handlers.onArm()
        }, LONG_PRESS.delay)
    }

    /** Doigt qui glisse. */
    move(x: number, y: number): void {
        const far = Math.hypot(x - this.startX, y - this.startY) > LONG_PRESS.tolerance
        if (this.state === 'pressing' && far) {
            this.clearTimer()
            this.state = 'idle' // Défilement : on abandonne
        } else if (this.state === 'armed' && far && this.handlers.canDrag()) {
            this.state = 'dragging'
            this.handlers.onDragStart(x, y)
        } else if (this.state === 'dragging') {
            this.handlers.onDragMove(x, y)
        }
    }

    /** Doigt levé. */
    up(): void {
        if (this.state === 'pressing') {
            this.clearTimer()
            this.state = 'idle' // Simple toucher : le clic passera
        } else if (this.state === 'armed') {
            this.state = 'consumed'
            this.handlers.onLongPress()
        } else if (this.state === 'dragging') {
            this.state = 'consumed'
            this.handlers.onDragEnd(true)
        }
    }

    /** Geste interrompu par le navigateur (pointercancel), ou composant démonté. */
    abort(): void {
        this.clearTimer()
        if (this.state === 'dragging') this.handlers.onDragEnd(false)
        this.state = 'idle'
    }

    /** À appeler dans onClick : true = ce clic termine un appui long ou un glisser, il faut l'ignorer. */
    consumeClick(): boolean {
        const consumed = this.state === 'consumed'
        if (consumed) this.state = 'idle'
        return consumed
    }

    /** Faut-il empêcher le navigateur de faire défiler ? (tâche soulevée ou glissée) */
    blocksScroll(): boolean {
        return this.state === 'armed' || this.state === 'dragging'
    }

    private clearTimer(): void {
        clearTimeout(this.timer)
        this.timer = undefined
    }
}
