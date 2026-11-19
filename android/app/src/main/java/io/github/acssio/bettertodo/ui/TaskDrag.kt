package io.github.acssio.bettertodo.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameMillis
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.hapticfeedback.HapticFeedback
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.boundsInRoot
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInRoot
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.github.acssio.bettertodo.ui.theme.Palette
import kotlin.math.roundToInt

/** Une liste qui peut défiler toute seule pendant un glisser, et sa place à l'écran. */
class Scroller(val state: LazyListState, val rect: Rect)

/**
 * Glisser au doigt dans une liste (étape 6-5b), comme le LongPress + touchDrag du site mobile.
 *
 * Machine d'état d'un geste (un doigt posé sur une tâche) :
 *
 *   posé ──(bouge avant 0,5 s : défilement / page voisine, ou levé : toucher)──→ rien (la liste gère)
 *     └──(0,5 s sans bouger : vibration)──→ appui long ──(levé sans bouger)──→ sélection (onSelect)
 *                                               └──(bouge)──→ glisser ──(levé)──→ dépôt (onDrop)
 *
 * Le geste est suivi par l'ÉCRAN de la liste (Modifier.taskDragGesture), pas par la tâche :
 * pendant le défilement automatique, la tâche d'origine peut sortir de l'écran et être retirée
 * de la LazyColumn ; un geste suivi sur elle serait perdu (même leçon que le bug du Z Flip sur le site).
 *
 * Les tâches, hauts et bas de groupes se déclarent avec track() : leur place à l'écran sert à trouver
 * la tâche sous le doigt au départ et la cible du dépôt (DragDrop.computeDrop).
 * Coordonnées : celles de la fenêtre (« root ») partout.
 */
class TaskDragState {
    // --- Repères mesurés à l'écran (pas de l'état Compose : ils changent à chaque image) ---
    val elements = mutableMapOf<String, DropElement>()
    val scrollers = mutableMapOf<String, Scroller>()
    var origin = Offset.Zero // Coin haut-gauche de l'écran de la liste dans la fenêtre
    var width = 0f           // Largeur de l'écran de la liste (pour garder le fantôme dedans)

    /** Vue colonnes : passer au groupe voisin (-1 / 1). Null en vue sections. */
    var onEdge: ((Int) -> Unit)? = null

    // --- Glisser en cours (état Compose : la ligne et le fantôme se redessinent) ---
    var draggedIds by mutableStateOf<List<String>>(emptyList())
        private set
    var draggedTitle by mutableStateOf("")
        private set
    var pointer by mutableStateOf(Offset.Zero) // Doigt, dans la fenêtre
        private set
    var target by mutableStateOf<DropTarget?>(null)
        private set

    val dragging: Boolean get() = draggedIds.isNotEmpty()

    /** La tâche sous le doigt (coordonnées de la fenêtre), ou null. */
    fun taskAt(point: Offset): String? {
        return elements.values.firstOrNull { it.kind == DropKind.TASK && !it.rect.isEmpty && it.rect.contains(point) }?.taskId
    }

    fun start(ids: List<String>, title: String, at: Offset) {
        draggedIds = ids
        draggedTitle = title
        pointer = at
    }

    fun move(to: Offset) {
        pointer = to
    }

    /** Recalcule la cible (à chaque image : les tâches bougent pendant le défilement automatique). */
    fun updateTarget(visibleIds: Map<String, List<String>>) {
        target = DragDrop.computeDrop(elements.values, pointer.x, pointer.y, visibleIds, draggedIds.toSet())
    }

    fun stop() {
        draggedIds = emptyList()
        target = null
    }
}

/**
 * Déclare un repère (tâche, haut ou bas de groupe) : sa place à l'écran est mise à jour à chaque
 * placement, et il est retiré quand il quitte la composition (tâche sortie de l'écran).
 */
@Composable
fun TaskDragState.track(key: String, kind: DropKind, groupId: String, taskId: String? = null): Modifier {
    DisposableEffect(key) {
        onDispose { elements.remove(key) }
    }
    return Modifier.onGloballyPositioned { elements[key] = DropElement(kind, groupId, taskId, it.boundsInRoot()) }
}

/** Déclare une liste qui défile (pour le défilement automatique). */
@Composable
fun TaskDragState.trackScroller(key: String, state: LazyListState): Modifier {
    DisposableEffect(key) {
        onDispose { scrollers.remove(key) }
    }
    return Modifier.onGloballyPositioned { scrollers[key] = Scroller(state, it.boundsInRoot()) }
}

/**
 * Le geste, posé sur tout l'écran de la liste. Il regarde les événements AVANT les tâches et la liste
 * (PointerEventPass.Initial) : tant que l'appui long n'est pas atteint, il les laisse passer (toucher,
 * défilement, page voisine) ; ensuite, il les garde pour lui (consume), donc la tâche ne reçoit pas
 * de « toucher » au moment où le doigt se lève.
 *
 * @param onSelect appui long sans bouger : la tâche est ajoutée à / retirée de la sélection
 * @param onDragStart début du glisser : renvoie les tâches glissées (la sélection si la tâche en fait partie)
 * @param onDrop doigt levé pendant un glisser
 */
fun Modifier.taskDragGesture(
    drag: TaskDragState,
    haptic: HapticFeedback,
    onSelect: (taskId: String) -> Unit,
    onDragStart: (taskId: String) -> Unit,
    onDrop: () -> Unit,
): Modifier = this
    .onGloballyPositioned {
        drag.origin = it.positionInRoot()
        drag.width = it.size.width.toFloat()
    }
    .pointerInput(drag) {
        awaitEachGesture {
            val down = awaitFirstDown(requireUnconsumed = false, pass = PointerEventPass.Initial)
            val taskId = drag.taskAt(drag.origin + down.position) ?: return@awaitEachGesture
            val slop = viewConfiguration.touchSlop

            // Posé : attendre l'appui long, sauf si le doigt bouge (défilement) ou se lève (toucher).
            // withTimeoutOrNull renvoie null si les 0,5 s passent : c'est l'appui long.
            val interrupted = withTimeoutOrNull(viewConfiguration.longPressTimeoutMillis) {
                while (true) {
                    val change = awaitPointerEvent(PointerEventPass.Initial).changes.firstOrNull { it.id == down.id }
                        ?: return@withTimeoutOrNull false
                    if (!change.pressed) return@withTimeoutOrNull false
                    val delta = change.position - down.position
                    if (DragDrop.movedBeyond(delta.x, delta.y, slop)) return@withTimeoutOrNull false
                }
                @Suppress("UNREACHABLE_CODE")
                false
            }
            if (interrupted != null) {
                return@awaitEachGesture // Toucher ou défilement : la tâche et la liste s'en occupent
            }

            // Appui long atteint
            haptic.performHapticFeedback(HapticFeedbackType.LongPress)
            var dragging = false
            while (true) {
                val change = awaitPointerEvent(PointerEventPass.Initial).changes.firstOrNull { it.id == down.id } ?: break
                change.consume() // La suite du geste est pour nous : ni toucher, ni défilement
                if (!change.pressed) {
                    if (dragging) onDrop() else onSelect(taskId)
                    return@awaitEachGesture
                }
                val delta = change.position - down.position
                if (!dragging && DragDrop.movedBeyond(delta.x, delta.y, slop)) {
                    dragging = true
                    onDragStart(taskId)
                }
                if (dragging) {
                    drag.move(drag.origin + change.position)
                }
            }
            // Geste interrompu (ex. un autre doigt, l'écran qui change) : on abandonne le glisser
            drag.stop()
        }
    }

/**
 * Pendant un glisser, à chaque image : défilement automatique près du haut / du bas,
 * page voisine après 0,5 s contre le bord (vue colonnes), et nouvelle cible.
 */
@Composable
fun TaskDragLoop(drag: TaskDragState, visibleIds: () -> Map<String, List<String>>) {
    val density = LocalDensity.current
    val edge = with(density) { 56.dp.toPx() }     // Zone près du haut / du bas qui fait défiler
    val maxSpeed = with(density) { 6.dp.toPx() }  // Vitesse au plus près du bord, par image
    val side = with(density) { 28.dp.toPx() }     // Bord gauche / droit (vue colonnes)

    LaunchedEffect(drag.dragging) {
        var sideSince = 0L // Depuis quand le doigt est contre un bord (0 = pas contre un bord)
        while (drag.dragging) {
            val now = withFrameMillis { it }
            val p = drag.pointer
            val scroller = drag.scrollers.values.firstOrNull { !it.rect.isEmpty && p.x >= it.rect.left && p.x <= it.rect.right }
            if (scroller != null) {
                val speed = DragDrop.autoScrollSpeed(p.y, scroller.rect.top, scroller.rect.bottom, edge, maxSpeed)
                if (speed != 0f) scroller.state.dispatchRawDelta(speed)

                val onEdge = drag.onEdge
                val direction = DragDrop.sideEdge(p.x, scroller.rect.left, scroller.rect.right, side)
                if (onEdge == null || direction == 0) {
                    sideSince = 0L
                } else if (sideSince == 0L) {
                    sideSince = now
                } else if (now - sideSince >= SIDE_DELAY_MS) {
                    onEdge(direction)
                    sideSince = now // Encore 0,5 s pour passer au groupe suivant
                }
            }
            drag.updateTarget(visibleIds())
        }
    }
}

private const val SIDE_DELAY_MS = 500L

/** La ligne bleue de dépôt et le « fantôme » qui suit le doigt. À poser au-dessus de la liste. */
@Composable
fun TaskDragOverlay(drag: TaskDragState) {
    if (!drag.dragging) return
    val density = LocalDensity.current

    drag.target?.let { target ->
        val lineHeight = with(density) { 3.dp.toPx() }
        Box(
            Modifier
                .offset { IntOffset((target.lineLeft - drag.origin.x).roundToInt(), (target.lineY - drag.origin.y - lineHeight / 2).roundToInt()) }
                .width(with(density) { (target.lineRight - target.lineLeft).toDp() })
                .height(3.dp)
                .background(Palette.accent, RoundedCornerShape(2.dp)),
        )
    }

    // Fantôme : le titre de la tâche, et le nombre de tâches si on en glisse plusieurs.
    // Centré au-dessus du doigt (pour que le doigt ne le cache pas), sans sortir de l'écran.
    val ghostWidth = with(density) { 240.dp.toPx() }
    val margin = with(density) { 8.dp.toPx() }
    val above = with(density) { 64.dp.toPx() }
    val ghostX = (drag.pointer.x - drag.origin.x - ghostWidth / 2).coerceIn(margin, (drag.width - ghostWidth - margin).coerceAtLeast(margin))
    Surface(
        shape = RoundedCornerShape(10.dp),
        color = Palette.surface,
        shadowElevation = 8.dp,
        modifier = Modifier
            .offset { IntOffset(ghostX.roundToInt(), (drag.pointer.y - drag.origin.y - above).roundToInt()) }
            .width(240.dp),
    ) {
        Row(Modifier.padding(horizontal = 12.dp, vertical = 10.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(drag.draggedTitle, fontSize = 15.sp, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f, fill = false))
            if (drag.draggedIds.size > 1) {
                Text(
                    "+${drag.draggedIds.size - 1}",
                    fontSize = 12.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = Color.White,
                    modifier = Modifier.background(Palette.accent, CircleShape).padding(horizontal = 7.dp, vertical = 2.dp),
                )
            }
        }
    }
}
