package io.github.acssio.bettertodo.ui

import androidx.compose.ui.geometry.Rect
import io.github.acssio.bettertodo.api.Task
import kotlin.math.abs
import kotlin.math.ceil
import kotlin.math.min

// =============================================================================
// Glisser au doigt (étape 6-5b) : les calculs, sans Compose, testés dans DragDropTest.
// Comme utils/dragDrop.ts et dropAnchor / multiMovePositions de utils/board.ts sur le site.
// =============================================================================

/** Ce qui peut recevoir un dépôt, mesuré à l'écran (coordonnées de la fenêtre). */
enum class DropKind {
    TASK, // Une tâche : dépôt avant ou après elle
    HEAD, // Le haut d'un groupe (titre en vue sections, haut de la page en vue colonnes) : en tête
    END,  // Le bas d'un groupe (« Ajouter une tâche ») : à la fin
}

data class DropElement(val kind: DropKind, val groupId: String, val taskId: String?, val rect: Rect)

/**
 * Où les tâches glissées seraient déposées.
 * @param visibleIndex place parmi les tâches AFFICHÉES du groupe, comptée sans les tâches glissées
 * @param lineY hauteur de la ligne bleue ; lineLeft / lineRight : sa largeur (la colonne)
 */
data class DropTarget(val groupId: String, val visibleIndex: Int, val lineY: Float, val lineLeft: Float, val lineRight: Float)

object DragDrop {

    /**
     * La cible sous le doigt (x, y) :
     *  - on ne garde que les éléments visibles de la colonne sous le doigt (vue colonnes : la page affichée),
     *    sans les tâches glissées elles-mêmes ;
     *  - l'élément sous le doigt, sinon le plus proche en hauteur ;
     *  - sur une tâche : avant elle si le doigt est sur sa moitié haute, après sinon.
     *
     * @param visibleIds pour chaque groupe, les ids des tâches affichées dans l'ordre (y compris celles hors de l'écran)
     * @return null si rien ne peut recevoir le dépôt sous le doigt
     */
    fun computeDrop(
        elements: Collection<DropElement>,
        x: Float,
        y: Float,
        visibleIds: Map<String, List<String>>,
        dragged: Set<String>,
    ): DropTarget? {
        val candidates = elements.filter { element ->
            !element.rect.isEmpty && x >= element.rect.left && x <= element.rect.right &&
                (element.taskId == null || element.taskId !in dragged)
        }
        val element = candidates.firstOrNull { y >= it.rect.top && y <= it.rect.bottom }
            ?: candidates.minByOrNull { distance(y, it.rect) }
            ?: return null

        val rect = element.rect
        return when (element.kind) {
            DropKind.HEAD -> DropTarget(element.groupId, 0, rect.bottom, rect.left, rect.right)
            DropKind.END -> {
                val count = visibleIds[element.groupId].orEmpty().count { it !in dragged }
                DropTarget(element.groupId, count, rect.top, rect.left, rect.right)
            }
            DropKind.TASK -> {
                val others = visibleIds[element.groupId].orEmpty().filter { it !in dragged }
                val index = others.indexOf(element.taskId).coerceAtLeast(0)
                val after = y > rect.center.y
                DropTarget(element.groupId, if (after) index + 1 else index, if (after) rect.bottom else rect.top, rect.left, rect.right)
            }
        }
    }

    /** Distance verticale entre y et le rectangle (0 si y est dedans). */
    private fun distance(y: Float, rect: Rect): Float = when {
        y < rect.top -> rect.top - y
        y > rect.bottom -> y - rect.bottom
        else -> 0f
    }

    /**
     * L'id de la tâche AVANT laquelle déposer (null = à la fin), à partir de la place parmi les tâches affichées.
     * Les tâches faites masquées comptent pour l'API : on s'accroche donc à une vraie tâche du groupe.
     * Copie de dropAnchor (utils/board.ts).
     */
    fun dropAnchor(tasks: List<Task>, movedIds: List<String>, visibleIndex: Int, showDone: Boolean): String? {
        val others = tasks.filter { it.id !in movedIds }
        val visible = if (showDone) others else others.filter { !it.done }
        if (visibleIndex < visible.size) return visible[visibleIndex].id
        if (visible.isEmpty()) return others.firstOrNull()?.id // Aucune tâche visible : en tête
        return others.getOrNull(others.indexOf(visible.last()) + 1)?.id
    }

    /**
     * Positions à envoyer à l'API pour déplacer les tâches UNE PAR UNE, dans l'ordre de movedIds,
     * juste avant beforeId (null = à la fin). groupTaskIds : les tâches actuelles du groupe cible.
     * L'API compte la position SANS la tâche déplacée : on rejoue les déplacements sur une copie.
     * Copie de multiMovePositions (utils/board.ts).
     */
    fun multiMovePositions(groupTaskIds: List<String>, movedIds: List<String>, beforeId: String?): List<Int> {
        val current = groupTaskIds.toMutableList()
        val positions = mutableListOf<Int>()
        for (id in movedIds) {
            current.remove(id) // Sans elle (si elle était déjà dans ce groupe)
            val position = if (beforeId == null) current.size else current.indexOf(beforeId)
            current.add(position, id)
            positions.add(position)
        }
        return positions
    }

    /**
     * Vitesse de défilement (px par image) quand le doigt est près du haut (négatif)
     * ou du bas (positif) de la zone [start, end]. 0 au milieu. Plus on est près du bord, plus ça va vite.
     * @param edge hauteur de la zone sensible, en px ; maxSpeed : vitesse au plus près du bord
     */
    fun autoScrollSpeed(pointer: Float, start: Float, end: Float, edge: Float, maxSpeed: Float): Float {
        if (pointer < start + edge) {
            val depth = min(1f, (start + edge - pointer) / edge)
            return -ceil(depth * maxSpeed)
        }
        if (pointer > end - edge) {
            val depth = min(1f, (pointer - (end - edge)) / edge)
            return ceil(depth * maxSpeed)
        }
        return 0f
    }

    /** Le doigt est-il contre le bord gauche (-1), droit (1), ou ni l'un ni l'autre (0) ? */
    fun sideEdge(x: Float, left: Float, right: Float, edge: Float): Int = when {
        x < left + edge -> -1
        x > right - edge -> 1
        else -> 0
    }

    /** Le doigt a-t-il bougé de plus de slop depuis le point de départ ? */
    fun movedBeyond(dx: Float, dy: Float, slop: Float): Boolean = abs(dx) > slop || abs(dy) > slop
}
