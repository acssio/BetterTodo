// =============================================================================
// Curseur de texte à l'endroit d'un clic (modification d'un titre sur place)
// =============================================================================

/**
 * Position du curseur dans element, à l'endroit cliqué (x, y), ou null si on n'a pas
 * cliqué sur le texte. caretPositionFromPoint : Firefox et Chrome récents ;
 * caretRangeFromPoint : Safari.
 */
export function caretOffsetAt(element: HTMLElement | null, x: number, y: number): number | null {
    if (!element) return null
    if (document.caretPositionFromPoint) {
        const position = document.caretPositionFromPoint(x, y)
        if (position && element.contains(position.offsetNode)) return position.offset
    } else if (document.caretRangeFromPoint) {
        const range = document.caretRangeFromPoint(x, y)
        if (range && element.contains(range.startContainer)) return range.startOffset
    }
    return null
}
