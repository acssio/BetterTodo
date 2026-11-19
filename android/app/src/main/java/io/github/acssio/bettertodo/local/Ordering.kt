package io.github.acssio.bettertodo.local

/**
 * Ordre d'affichage, comme ordering.move du serveur (api/internal/handlers/helpers.go).
 *
 * Place movedId à l'index position parmi ses frères, puis on renumérote 0, 1, 2…
 * Une position trop grande le met à la fin, une position négative au début.
 *
 * Exemple : [A, B, C, D], on déplace A en position 2
 *   sans A    → [B, C, D]
 *   insertion → [B, C, A, D]   → positions B=0, C=1, A=2, D=3
 */
object Ordering {
    /** Position « à la fin » (comme endPosition du serveur). */
    const val END = Int.MAX_VALUE

    /**
     * @param ids les frères dans l'ordre actuel (movedId peut y être ou non)
     * @return les ids dans le nouvel ordre : l'index de chacun est sa nouvelle position
     */
    fun move(ids: List<String>, movedId: String, position: Int): List<String> {
        val others = ids.filter { it != movedId }.toMutableList()
        others.add(position.coerceIn(0, others.size), movedId)
        return others
    }
}
