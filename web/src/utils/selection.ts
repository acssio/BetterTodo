// =============================================================================
// Sélection de tâches sur ordinateur : Maj+clic = plage (testé dans selection.test.ts)
// =============================================================================

/**
 * Ajoute à la sélection toutes les tâches entre l'ancrage et la tâche cliquée.
 * @param order     ids des tâches dans l'ordre de l'écran (une liste ou une liste intelligente)
 * @param anchorId  dernière tâche ajoutée ou retirée par un clic (null = pas d'ancrage)
 * @param targetId  tâche cliquée avec Maj
 * @param selected  sélection actuelle
 * @return la nouvelle sélection (l'ancienne, plus la plage, sans doublon)
 */
export function selectRange(order: string[], anchorId: string | null, targetId: string, selected: string[]): string[] {
    const end = order.indexOf(targetId)
    if (end < 0) return selected // Tâche inconnue : on ne touche à rien

    const start = anchorId === null ? -1 : order.indexOf(anchorId)
    if (start < 0) {
        // Pas d'ancrage dans cette liste (ou aucun) : juste la tâche cliquée
        return selected.includes(targetId) ? selected : [...selected, targetId]
    }

    const range = order.slice(Math.min(start, end), Math.max(start, end) + 1)
    return [...selected, ...range.filter(id => !selected.includes(id))]
}
