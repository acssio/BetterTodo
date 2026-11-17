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
    val accentLight = Color(0xFFE3ECFD) // --accent très clair (fond d'une puce sélectionnée)
    val danger = Color(0xFFE5484D)     // --danger
    val success = Color(0xFF3FA66B)    // --success : case cochée
    val done = Color(0xFFA3A3A0)       // --done : titre d'une tâche cochée
    val checkBorder = Color(0xFFB5B5B1) // Bord de la case à cocher (.card-check)
    val tagBackground = Color(0xFFF0F0EE) // .card-tag
    val tagText = Color(0xFF55554F)
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
        secondaryContainer = Palette.accentLight, // Puce sélectionnée (échéance, urgence…)
        onSecondaryContainer = Palette.accent,
        surfaceVariant = Palette.hover,
        onSurfaceVariant = Palette.muted,
        surfaceContainerHighest = Palette.surface, // Fond des champs de saisie
        surfaceContainerHigh = Palette.surface,    // Fond du calendrier et des boîtes de dialogue
        surfaceContainerLow = Palette.surface,     // Fond des panneaux du bas
        outline = Palette.border,
        outlineVariant = Palette.border,
        error = Palette.danger,
    )
    MaterialTheme(colorScheme = colors, content = content)
}
