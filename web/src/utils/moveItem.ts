/**
 * Déplace l'élément id à l'index donné (compté SANS l'élément déplacé, comme l'API),
 * et renvoie une nouvelle liste. Un index trop grand met l'élément à la fin.
 *
 * Exemple : [A, B, C, D], moveItem(…, A, 2) → sans A [B, C, D] → [B, C, A, D]
 */
export function moveItem<T extends { id: string }>(items: T[], id: string, index: number): T[] {
    const moved = items.find(item => item.id === id)
    if (!moved) return items

    const others = items.filter(item => item.id !== id)
    others.splice(Math.max(0, Math.min(index, others.length)), 0, moved)
    return others
}
