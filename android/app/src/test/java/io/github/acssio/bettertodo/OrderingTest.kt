package io.github.acssio.bettertodo

import io.github.acssio.bettertodo.local.LocalApi
import io.github.acssio.bettertodo.local.Ordering
import org.junit.Assert.assertEquals
import org.junit.Test

/** Comme ordering.move du serveur (helpers.go). */
class OrderingTest {
    private val abcd = listOf("A", "B", "C", "D")

    @Test
    fun deplacerVersLeBas() {
        assertEquals(listOf("B", "C", "A", "D"), Ordering.move(abcd, "A", 2))
    }

    @Test
    fun deplacerVersLeHaut() {
        assertEquals(listOf("D", "A", "B", "C"), Ordering.move(abcd, "D", 0))
    }

    @Test
    fun positionHorsLimites() {
        assertEquals(listOf("B", "C", "D", "A"), Ordering.move(abcd, "A", Ordering.END)) // À la fin
        assertEquals(listOf("C", "A", "B", "D"), Ordering.move(abcd, "C", -5))          // Au début
    }

    @Test
    fun elementQuiArriveDUnAutreGroupe() {
        assertEquals(listOf("A", "X", "B", "C", "D"), Ordering.move(abcd, "X", 1))
    }

    @Test
    fun positionDUnNouvelElement() {
        assertEquals(0, LocalApi.nextPosition(emptyList()))
        assertEquals(3, LocalApi.nextPosition(listOf(0, 2, 1)))
    }
}
