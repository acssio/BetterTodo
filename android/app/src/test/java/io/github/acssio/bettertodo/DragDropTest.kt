package io.github.acssio.bettertodo

import androidx.compose.ui.geometry.Rect
import io.github.acssio.bettertodo.api.Task
import io.github.acssio.bettertodo.ui.DragDrop
import io.github.acssio.bettertodo.ui.DropElement
import io.github.acssio.bettertodo.ui.DropKind
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/** Comme dragDrop.test.ts et board.test.ts du site. */
class DragDropTest {
    private fun task(id: String, done: Boolean = false) = Task(id = id, groupId = "g", title = id, position = 0, done = done)

    // --- dropAnchor ---------------------------------------------------------

    @Test
    fun ancreAvantLaTacheAffichee() {
        val tasks = listOf(task("a"), task("b"), task("c"))
        assertEquals("b", DragDrop.dropAnchor(tasks, listOf("c"), 1, showDone = true))
        assertNull(DragDrop.dropAnchor(tasks, listOf("a"), 2, showDone = true)) // À la fin
    }

    @Test
    fun ancreAvecTachesFaitesMasquees() {
        // b est faite et masquée : déposer après a (index 1) doit passer après b aussi
        val tasks = listOf(task("a"), task("b", done = true), task("c"), task("d"))
        assertEquals("c", DragDrop.dropAnchor(tasks, listOf("d"), 1, showDone = false))
        // Après la dernière tâche visible : juste après elle, même s'il reste des faites derrière
        val withDoneAtEnd = listOf(task("a"), task("b", done = true))
        assertEquals("b", DragDrop.dropAnchor(withDoneAtEnd, listOf("x"), 1, showDone = false))
    }

    @Test
    fun ancreDansUnGroupeSansTacheVisible() {
        assertEquals("a", DragDrop.dropAnchor(listOf(task("a", done = true)), listOf("x"), 0, showDone = false))
        assertNull(DragDrop.dropAnchor(emptyList(), listOf("x"), 0, showDone = false))
    }

    // --- multiMovePositions -------------------------------------------------

    @Test
    fun positionsUneParUne() {
        // Déposer x puis y avant c dans [a, b, c] : x → 2, puis y → 3 (après x)
        assertEquals(listOf(2, 3), DragDrop.multiMovePositions(listOf("a", "b", "c"), listOf("x", "y"), "c"))
        assertEquals(listOf(3, 4), DragDrop.multiMovePositions(listOf("a", "b", "c"), listOf("x", "y"), null))
    }

    @Test
    fun positionsDansLeMemeGroupe() {
        // Monter c avant a dans [a, b, c] : position 0 (comptée sans c)
        assertEquals(listOf(0), DragDrop.multiMovePositions(listOf("a", "b", "c"), listOf("c"), "a"))
        // Descendre a à la fin : position 2 (comptée sans a)
        assertEquals(listOf(2), DragDrop.multiMovePositions(listOf("a", "b", "c"), listOf("a"), null))
    }

    // --- computeDrop ---------------------------------------------------------

    private val column = listOf(
        DropElement(DropKind.HEAD, "g", null, Rect(0f, 0f, 100f, 10f)),
        DropElement(DropKind.TASK, "g", "a", Rect(0f, 20f, 100f, 60f)),
        DropElement(DropKind.TASK, "g", "b", Rect(0f, 70f, 100f, 110f)),
        DropElement(DropKind.END, "g", null, Rect(0f, 120f, 100f, 160f)),
        // Une autre colonne, plus à droite (vue colonnes : page voisine)
        DropElement(DropKind.TASK, "h", "z", Rect(200f, 20f, 300f, 60f)),
    )
    private val visible = mapOf("g" to listOf("a", "b"), "h" to listOf("z"))

    @Test
    fun surUneTacheAvantOuApres() {
        assertEquals(0, DragDrop.computeDrop(column, 50f, 30f, visible, emptySet())!!.visibleIndex) // Moitié haute de a
        assertEquals(1, DragDrop.computeDrop(column, 50f, 50f, visible, emptySet())!!.visibleIndex) // Moitié basse de a
        assertEquals(60f, DragDrop.computeDrop(column, 50f, 50f, visible, emptySet())!!.lineY)
    }

    @Test
    fun hautEtBasDuGroupe() {
        assertEquals(0, DragDrop.computeDrop(column, 50f, 5f, visible, emptySet())!!.visibleIndex)
        assertEquals(2, DragDrop.computeDrop(column, 50f, 140f, visible, emptySet())!!.visibleIndex)
        // Sans les tâches glissées : b glissée, la fin du groupe est l'index 1
        assertEquals(1, DragDrop.computeDrop(column, 50f, 140f, visible, setOf("b"))!!.visibleIndex)
    }

    @Test
    fun entreDeuxElementsLePlusProche() {
        val target = DragDrop.computeDrop(column, 50f, 64f, visible, emptySet())!! // Entre a (60) et b (70) : a est plus proche
        assertEquals(1, target.visibleIndex)
    }

    @Test
    fun seulementLaColonneSousLeDoigt() {
        assertEquals("h", DragDrop.computeDrop(column, 250f, 30f, visible, emptySet())!!.groupId)
        assertNull(DragDrop.computeDrop(column, 150f, 30f, visible, emptySet())) // Entre les colonnes
    }

    @Test
    fun laTacheGlisseeNeRecoitPasLeDepot() {
        // Doigt sur a, mais a est glissée : on prend l'élément le plus proche (le haut du groupe ou b)
        val target = DragDrop.computeDrop(column, 50f, 40f, visible, setOf("a"))!!
        assertEquals("g", target.groupId)
    }

    @Test
    fun elementsCachesIgnores() {
        val hidden = listOf(DropElement(DropKind.TASK, "g", "a", Rect.Zero)) // Page hors de l'écran : rectangle vide
        assertNull(DragDrop.computeDrop(hidden, 0f, 0f, visible, emptySet()))
    }

    // --- Défilement et bords -----------------------------------------------

    @Test
    fun defilementAutomatique() {
        assertEquals(0f, DragDrop.autoScrollSpeed(500f, 0f, 1000f, 100f, 20f))
        assertEquals(-20f, DragDrop.autoScrollSpeed(0f, 0f, 1000f, 100f, 20f))   // Tout en haut : vitesse max vers le haut
        assertEquals(10f, DragDrop.autoScrollSpeed(950f, 0f, 1000f, 100f, 20f))  // À mi-chemin de la zone du bas
    }

    @Test
    fun bords() {
        assertEquals(-1, DragDrop.sideEdge(5f, 0f, 400f, 30f))
        assertEquals(0, DragDrop.sideEdge(200f, 0f, 400f, 30f))
        assertEquals(1, DragDrop.sideEdge(390f, 0f, 400f, 30f))
    }
}
