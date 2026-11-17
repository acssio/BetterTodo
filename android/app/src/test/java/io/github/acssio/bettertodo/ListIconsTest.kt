package io.github.acssio.bettertodo

import com.composables.icons.lucide.R
import io.github.acssio.bettertodo.ui.ListIcons
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Test
import java.io.File

class ListIconsTest {

    /**
     * ListIcons.kt doit proposer exactement les mêmes icônes que la page web
     * (web/src/components/listIcons.ts), comme mcp/palette_test.go.
     * Les tests tournent depuis le dossier android/app.
     */
    @Test
    fun synchroniseAvecLaPageWeb() {
        val file = File("../../web/src/components/listIcons.ts")
        assumeTrue("page web introuvable", file.exists())

        // Lignes "    'graduation-cap': { icon: GraduationCap, label: '…' },"
        val webIcons = Regex("'([a-z0-9-]+)': \\{ icon: ").findAll(file.readText()).map { it.groupValues[1] }.toSet()
        assertEquals("absentes de ListIcons.kt", emptySet<String>(), webIcons - ListIcons.names)
        assertEquals("absentes de la page web", emptySet<String>(), ListIcons.names.toSet() - webIcons)
    }

    /** Mêmes mots-clés de recherche et mêmes couleurs que la page web. */
    @Test
    fun libellesEtCouleursSynchronisesAvecLaPageWeb() {
        val file = File("../../web/src/components/listIcons.ts")
        assumeTrue("page web introuvable", file.exists())
        val source = file.readText()

        Regex("'([a-z0-9-]+)': \\{ icon: \\w+, label: '([^']*)' \\}").findAll(source).forEach { match ->
            assertEquals("libellé de ${match.groupValues[1]}", match.groupValues[2], ListIcons.label(match.groupValues[1]))
        }
        val webColors = Regex("'(#[0-9A-Fa-f]{6})'").findAll(source).map { it.groupValues[1] }.toList()
        assertEquals(webColors, ListIcons.colors)
    }

    @Test
    fun recherche() {
        assertEquals(ListIcons.names, ListIcons.search("  "))                 // Rien tapé : toutes
        assertTrue("shopping-cart" in ListIcons.search("courses"))                // Mot-clé français
        assertTrue("shopping-cart" in ListIcons.search("cart"))                   // Nom anglais
        assertTrue("dog" in ListIcons.search("Chien"))                       // Majuscules ignorées
        assertTrue(ListIcons.search("xyzxyz").isEmpty())
    }

    @Test
    fun iconeInconnueDevientList() {
        assertEquals(R.drawable.lucide_ic_list, ListIcons.drawable("n-existe-pas"))
    }

    @Test
    fun couleurs() {
        assertEquals(0xFF2F6FEB.toInt(), ListIcons.color("#2F6FEB").toArgbInt())
        assertEquals(0xFF6B6B6B.toInt(), ListIcons.color("pas une couleur").toArgbInt()) // Gris par défaut
    }

    /** Color → 0xAARRGGBB, sans passer par android.graphics (absent des tests locaux). */
    private fun androidx.compose.ui.graphics.Color.toArgbInt(): Int {
        return (value shr 32).toInt()
    }
}
