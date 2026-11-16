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

/**
 * Relie un LongPress aux événements du doigt, écoutés sur TOUTE LA FENÊTRE pendant le geste.
 *
 * Pourquoi pas sur la tâche elle-même : pendant un glisser, la page change sous le doigt
 * (ligne bleue, tâche grisée, défilement) et le navigateur peut envoyer le « doigt levé »
 * à un autre élément (bug vu sur Galaxy Z Flip : le glisser ne se terminait jamais, la
 * tâche restait « en l'air »). Sur window, on le reçoit toujours.
 * touchend / touchcancel servent de filet de sécurité si un pointerup se perd quand même.
 *
 *   onPointerDown de la tâche → this.gesture.start(event)
 *   componentWillUnmount      → this.gesture.stop()
 */
export class GestureTracker {
    private press: LongPress
    private onCancel: () => void      // Geste annulé par le navigateur (le composant remet son affichage)
    private pointerId: number | null = null

    constructor(press: LongPress, onCancel: () => void) {
        this.press = press
        this.onCancel = onCancel
    }

    /** Doigt posé sur la tâche : on suit ce doigt-là jusqu'à ce qu'il se lève. */
    start(pointerId: number, x: number, y: number): void {
        this.stop()
        this.pointerId = pointerId
        window.addEventListener('pointermove', this.onPointerMove)
        window.addEventListener('pointerup', this.onPointerUp)
        window.addEventListener('pointercancel', this.onPointerCancel)
        window.addEventListener('touchend', this.onTouchEnd)
        window.addEventListener('touchcancel', this.onTouchCancel)
        this.press.down(x, y)
    }

    /** Arrête d'écouter (geste fini, ou composant démonté). */
    stop(): void {
        window.removeEventListener('pointermove', this.onPointerMove)
        window.removeEventListener('pointerup', this.onPointerUp)
        window.removeEventListener('pointercancel', this.onPointerCancel)
        window.removeEventListener('touchend', this.onTouchEnd)
        window.removeEventListener('touchcancel', this.onTouchCancel)
        this.pointerId = null
    }

    private onPointerMove = (event: PointerEvent): void => {
        if (event.pointerId === this.pointerId) this.press.move(event.clientX, event.clientY)
    }

    private onPointerUp = (event: PointerEvent): void => {
        if (event.pointerId !== this.pointerId) return
        this.stop()
        this.press.up()
    }

    private onPointerCancel = (event: PointerEvent): void => {
        if (event.pointerId !== this.pointerId) return
        this.cancel()
    }

    /** Filet de sécurité : le doigt est levé mais aucun pointerup n'est arrivé. */
    private onTouchEnd = (): void => {
        if (this.pointerId === null) return
        this.stop()
        this.press.up()
    }

    private onTouchCancel = (): void => {
        if (this.pointerId !== null) this.cancel()
    }

    private cancel(): void {
        this.stop()
        this.press.abort()
        this.onCancel()
    }
}
