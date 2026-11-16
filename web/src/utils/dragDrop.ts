// =============================================================================
// Glisser-déposer des tâches : types et calcul de l'index de dépôt.
// L'état du glisser-déposer est rangé dans Workspace, au-dessus de toutes les
// vues : c'est ce qui permettra de glisser d'une liste à l'autre (vue scindée).
// =============================================================================

/** Où les tâches glissées seraient déposées : groupe + index (compté SANS les tâches glissées). */
export interface DropTarget {
    groupId: string
    index: number
}

/** Glisser-déposer en cours. */
export interface DragState {
    taskIds: string[]         // Tâches déplacées : celle qu'on tient, ou toute la sélection (dans l'ordre d'affichage)
    target: DropTarget | null // null tant que la souris n'est au-dessus d'aucun groupe
}

/**
 * Index de dépôt dans un conteneur : avant le premier élément portant attribute
 * (par défaut data-task-id) dont le milieu est sous la souris. Les éléments glissés sont ignorés.
 */
export function computeDropIndex(
    container: HTMLElement, mouseY: number, dragIds: string[], attribute: string = 'data-task-id',
): number {
    const items = Array.from(container.querySelectorAll<HTMLElement>(`[${attribute}]`))
        .filter(item => !dragIds.includes(item.getAttribute(attribute) ?? ''))

    for (let i = 0; i < items.length; i++) {
        const rect = items[i].getBoundingClientRect()
        if (mouseY < rect.top + rect.height / 2) {
            return i
        }
    }
    return items.length
}

// =============================================================================
// Glisser au doigt (mode mobile) : défilement automatique près des bords
// =============================================================================

export const AUTO_SCROLL = {
    edge: 56,         // px : zone près du haut / du bas où la liste défile toute seule
    maxSpeed: 14,     // px par image au plus près du bord
    sideEdge: 28,     // px : bord gauche / droit qui fait passer au groupe voisin (vue colonnes)
    sideDelay: 500,   // ms à garder le doigt sur ce bord
}

/**
 * Vitesse de défilement (px par image) quand le doigt est près du haut (négatif)
 * ou du bas (positif) de la zone [start, end]. 0 au milieu. Plus on est près du bord, plus ça va vite.
 */
export function autoScrollSpeed(pointer: number, start: number, end: number): number {
    if (pointer < start + AUTO_SCROLL.edge) {
        const depth = Math.min(1, (start + AUTO_SCROLL.edge - pointer) / AUTO_SCROLL.edge)
        return -Math.ceil(depth * AUTO_SCROLL.maxSpeed)
    }
    if (pointer > end - AUTO_SCROLL.edge) {
        const depth = Math.min(1, (pointer - (end - AUTO_SCROLL.edge)) / AUTO_SCROLL.edge)
        return Math.ceil(depth * AUTO_SCROLL.maxSpeed)
    }
    return 0
}

/** Le doigt est-il contre le bord gauche (-1), droit (1), ou ni l'un ni l'autre (0) ? */
export function sideEdge(x: number, left: number, right: number): number {
    if (x < left + AUTO_SCROLL.sideEdge) return -1
    if (x > right - AUTO_SCROLL.sideEdge) return 1
    return 0
}
