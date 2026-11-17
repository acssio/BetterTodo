package io.github.acssio.bettertodo.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

/** Couleurs de la page web (web/src/index.css), pour que les deux se ressemblent. */
object Palette {
    val background = Color(0xFFFBFBFA) // --sidebar : fond de l'accueil mobile
    val surface = Color(0xFFFFFFFF)    // --surface
    val hover = Color(0xFFEDEDEA)      // --hover
    val border = Color(0xFFE6E6E3)     // --border
    val text = Color(0xFF1F1F1F)       // --text
    val muted = Color(0xFF6B6B6B)      // --muted
    val faint = Color(0xFF8A8A87)      // --faint
    val accent = Color(0xFF2F6FEB)     // --accent
    val danger = Color(0xFFE5484D)     // --danger
}

/** Thème Material 3 de l'app (clair seulement, comme le site pour l'instant). */
@Composable
fun BetterTodoTheme(content: @Composable () -> Unit) {
    val colors = lightColorScheme(
        primary = Palette.accent,
        onPrimary = Color.White,
        background = Palette.background,
        onBackground = Palette.text,
        surface = Palette.background,
        onSurface = Palette.text,
        surfaceVariant = Palette.hover,
        onSurfaceVariant = Palette.muted,
        surfaceContainerHighest = Palette.surface, // Fond des champs de saisie
        outline = Palette.border,
        outlineVariant = Palette.border,
        error = Palette.danger,
    )
    MaterialTheme(colorScheme = colors, content = content)
}
