// =============================================================================
// Bords à signaler quand des colonnes sont cachées (testé dans scrollEdges.test.ts)
// =============================================================================

/**
 * none  : tout est visible, rien à signaler
 * left  : des colonnes sont cachées à gauche seulement
 * right : des colonnes sont cachées à droite seulement
 * both  : des colonnes sont cachées des deux côtés
 */
export type ScrollEdges = 'none' | 'left' | 'right' | 'both'

/**
 * Calcule l'état à partir des trois mesures du navigateur (scrollLeft, clientWidth, scrollWidth).
 * 1 px de marge : avec un zoom, le navigateur arrondit et il peut manquer une fraction de pixel.
 */
export function scrollEdges(scrollLeft: number, clientWidth: number, scrollWidth: number): ScrollEdges {
    const hiddenLeft = scrollLeft > 1
    const hiddenRight = scrollLeft + clientWidth < scrollWidth - 1

    if (hiddenLeft && hiddenRight) return 'both'
    if (hiddenLeft) return 'left'
    if (hiddenRight) return 'right'
    return 'none'
}
