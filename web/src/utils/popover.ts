// =============================================================================
// Outils communs aux overlays (tâche, liste) : position et fermeture.
// =============================================================================

import type * as React from 'react'

const MARGIN = 12       // Écart entre l'élément cliqué et l'overlay
const WINDOW_EDGE = 8   // Distance minimale au bord de la fenêtre

/**
 * Position d'un overlay ouvert à côté de anchor : à droite s'il y a la place,
 * sinon à gauche, et toujours dans la fenêtre.
 */
export function popoverPosition(anchor: DOMRect, width: number, estimatedHeight: number): React.CSSProperties {
    let left = anchor.right + MARGIN
    if (left + width > window.innerWidth - WINDOW_EDGE) {
        left = anchor.left - MARGIN - width
    }
    left = Math.max(WINDOW_EDGE, left)

    const top = Math.max(WINDOW_EDGE, Math.min(anchor.top - WINDOW_EDGE, window.innerHeight - estimatedHeight))
    return { left, top, width }
}

/**
 * Ferme un overlay sur Échap ou sur un clic en dehors.
 *
 *   componentDidMount    → this.dismiss.start()
 *   componentWillUnmount → this.dismiss.stop()
 *
 * ignoreSelector : un clic sur un élément qui correspond (ex. la carte qui a ouvert
 * l'overlay) ne ferme pas.
 */
export class DismissListener {
    private root: React.RefObject<HTMLElement | null>
    private onDismiss: () => void
    private ignoreSelector: string | null

    constructor(root: React.RefObject<HTMLElement | null>, onDismiss: () => void, ignoreSelector: string | null = null) {
        this.root = root
        this.onDismiss = onDismiss
        this.ignoreSelector = ignoreSelector
    }

    start(): void {
        document.addEventListener('mousedown', this.onMouseDown)
        document.addEventListener('keydown', this.onKeyDown)
    }

    stop(): void {
        document.removeEventListener('mousedown', this.onMouseDown)
        document.removeEventListener('keydown', this.onKeyDown)
    }

    private onMouseDown = (event: MouseEvent): void => {
        const target = event.target as Element
        if (this.root.current?.contains(target)) return
        if (this.ignoreSelector && target.closest(this.ignoreSelector)) return
        this.onDismiss()
    }

    private onKeyDown = (event: KeyboardEvent): void => {
        // defaultPrevented : un champ (InlineInput) a déjà utilisé Échap pour s'annuler
        if (event.key === 'Escape' && !event.defaultPrevented) this.onDismiss()
    }
}
