// =============================================================================
// Glisser-déposer des tâches : types et calcul de l'index de dépôt.
// L'état du glisser-déposer est rangé dans Workspace, au-dessus de toutes les
// vues : c'est ce qui permettra de glisser d'une liste à l'autre (vue scindée).
// =============================================================================

/** Où la tâche glissée serait déposée : groupe + index (compté SANS la tâche glissée). */
export interface DropTarget {
    groupId: string
    index: number
}

/** Glisser-déposer en cours. */
export interface DragState {
    taskId: string
    target: DropTarget | null // null tant que la souris n'est au-dessus d'aucun groupe
}

/**
 * Index de dépôt dans un conteneur : avant le premier élément portant attribute
 * (par défaut data-task-id) dont le milieu est sous la souris. L'élément glissé est ignoré.
 */
export function computeDropIndex(
    container: HTMLElement, mouseY: number, dragId: string, attribute: string = 'data-task-id',
): number {
    const items = Array.from(container.querySelectorAll<HTMLElement>(`[${attribute}]`))
        .filter(item => item.getAttribute(attribute) !== dragId)

    for (let i = 0; i < items.length; i++) {
        const rect = items[i].getBoundingClientRect()
        if (mouseY < rect.top + rect.height / 2) {
            return i
        }
    }
    return items.length
}
