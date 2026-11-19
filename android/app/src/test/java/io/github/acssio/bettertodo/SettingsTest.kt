package io.github.acssio.bettertodo

import io.github.acssio.bettertodo.api.Tag
import io.github.acssio.bettertodo.api.UrgencyLevel
import io.github.acssio.bettertodo.ui.deleteLevelMessage
import io.github.acssio.bettertodo.ui.deleteTagMessage
import io.github.acssio.bettertodo.ui.reorderLevels
import io.github.acssio.bettertodo.ui.nextLevelColor
import org.junit.Assert.assertEquals
import org.junit.Test

class SettingsTest {
    private val levels = listOf("Basse", "Moyenne", "Haute").mapIndexed { rank, name -> UrgencyLevel(name, name, "#3FA66B", rank) }

    @Test
    fun couleurDUnNouveauNiveau() {
        assertEquals("#E5484D", nextLevelColor(emptyList()))                   // La première de la palette
        assertEquals("#E8A33D", nextLevelColor(listOf("#e5484d")))              // Majuscules ignorées
        assertEquals("#6B6B6B", nextLevelColor(listOf("#E5484D", "#E8A33D", "#3FA66B", "#0E9F9A", "#2F6FEB", "#8E4EC6", "#D6409F", "#6B6B6B"))) // Toutes prises : la dernière
    }

    @Test
    fun monterEtDescendreUnNiveau() {
        val down = reorderLevels(levels, 0, 1) // « Basse » descend (devient plus urgente que « Moyenne »)
        assertEquals(listOf("Moyenne", "Basse", "Haute"), down.map { it.name })
        assertEquals(listOf(0, 1, 2), down.map { it.rank })                    // Rangs renumérotés
        assertEquals(listOf("Basse", "Haute", "Moyenne"), reorderLevels(levels, 2, 1).map { it.name })
    }

    @Test
    fun messagesDeConfirmation() {
        assertEquals("Supprimer le niveau « Haute » ? Les tâches de ce niveau passeront sans urgence.", deleteLevelMessage(levels[2]))
        assertEquals("Supprimer le tag « bio » ?", deleteTagMessage(Tag("t", "bio", 0)))
        assertEquals("Supprimer le tag « bio » ? Il sera enlevé de 1 tâche.", deleteTagMessage(Tag("t", "bio", 1)))
        assertEquals("Supprimer le tag « bio » ? Il sera enlevé de 3 tâches.", deleteTagMessage(Tag("t", "bio", 3)))
    }
}
