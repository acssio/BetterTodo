package io.github.acssio.bettertodo.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color

/**
 * Les couleurs de l'app, en clair ou en sombre : celles de la page web (web/src/index.css),
 * pour que les deux se ressemblent. Même rôle que les variables CSS : une nouvelle couleur
 * = une propriété ici, avec ses deux valeurs (LightColors et DarkColors).
 */
class AppColors(
    val background: Color,   // --sidebar : fond de l'accueil et des écrans
    val surface: Color,      // --surface : tâches, panneaux du bas
    val hover: Color,        // --hover
    val border: Color,       // --border
    val header: Color,       // --header : barre du haut d'une liste (grise, pas teintée par la liste)
    val headerBorder: Color, // --header-border
    val text: Color,         // --text
    val muted: Color,        // --muted
    val faint: Color,        // --faint
    val accent: Color,       // --accent
    val accentLight: Color,  // --selected : fond d'une tâche sélectionnée, d'une puce choisie
    val danger: Color,       // --danger
    val success: Color,      // --success : case cochée
    val done: Color,         // --done : titre d'une tâche cochée
    val checkBorder: Color,  // --check-border : bord de la case à cocher
    val tagBackground: Color, // --tag-bg
    val tagText: Color,      // --tag-text
    val primaryBg: Color,    // --primary-bg : logo, bouton flottant (noir en clair, blanc cassé en sombre)
    val primaryText: Color,  // --primary-text : texte et icône posés sur primaryBg
)

val LightColors = AppColors(
    background = Color(0xFFFBFBFA),
    surface = Color(0xFFFFFFFF),
    hover = Color(0xFFEDEDEA),
    border = Color(0xFFE6E6E3),
    header = Color(0xFFEBEBE8),
    headerBorder = Color(0xFFDCDCD8),
    text = Color(0xFF1F1F1F),
    muted = Color(0xFF6B6B6B),
    faint = Color(0xFF8A8A87),
    accent = Color(0xFF2F6FEB),
    accentLight = Color(0xFFE3ECFD),
    danger = Color(0xFFE5484D),
    success = Color(0xFF3FA66B),
    done = Color(0xFFA3A3A0),
    checkBorder = Color(0xFFB5B5B1),
    tagBackground = Color(0xFFF0F0EE),
    tagText = Color(0xFF55554F),
    primaryBg = Color(0xFF1F1F1F),
    primaryText = Color(0xFFFFFFFF),
)

/** Couleurs de la maquette v3 (mêmes valeurs que :root[data-theme="dark"] du site). */
val DarkColors = AppColors(
    background = Color(0xFF19191C),
    surface = Color(0xFF222226),
    hover = Color(0xFF2C2C31),
    border = Color(0xFF313137),
    header = Color(0xFF26262B),
    headerBorder = Color(0xFF36363D),
    text = Color(0xFFECECEA),
    muted = Color(0xFFA3A3A0),
    faint = Color(0xFF85858A),
    accent = Color(0xFF5B8DEF),
    accentLight = Color(0xFF1D2A44),
    danger = Color(0xFFFF6369),
    success = Color(0xFF4CC38A),
    done = Color(0xFF6A6A6F),
    checkBorder = Color(0xFF5A5A61),
    tagBackground = Color(0xFF2E2E34),
    tagText = Color(0xFFC8C8C3),
    primaryBg = Color(0xFFECECEA),
    primaryText = Color(0xFF141416),
)

/** Les couleurs du thème en cours, fournies par BetterTodoTheme. */
val LocalAppColors = staticCompositionLocalOf { LightColors }

/**
 * Raccourci vers les couleurs du thème en cours : Palette.text, Palette.border…
 * Ne s'utilise que dans une fonction @Composable (la valeur dépend du thème affiché).
 */
object Palette {
    val background: Color @Composable @ReadOnlyComposable get() = LocalAppColors.current.background
    val surface: Color @Composable @ReadOnlyComposable get() = LocalAppColors.current.surface
    val hover: Color @Composable @ReadOnlyComposable get() = LocalAppColors.current.hover
    val border: Color @Composable @ReadOnlyComposable get() = LocalAppColors.current.border
    val header: Color @Composable @ReadOnlyComposable get() = LocalAppColors.current.header
    val headerBorder: Color @Composable @ReadOnlyComposable get() = LocalAppColors.current.headerBorder
    val text: Color @Composable @ReadOnlyComposable get() = LocalAppColors.current.text
    val muted: Color @Composable @ReadOnlyComposable get() = LocalAppColors.current.muted
    val faint: Color @Composable @ReadOnlyComposable get() = LocalAppColors.current.faint
    val accent: Color @Composable @ReadOnlyComposable get() = LocalAppColors.current.accent
    val accentLight: Color @Composable @ReadOnlyComposable get() = LocalAppColors.current.accentLight
    val danger: Color @Composable @ReadOnlyComposable get() = LocalAppColors.current.danger
    val success: Color @Composable @ReadOnlyComposable get() = LocalAppColors.current.success
    val done: Color @Composable @ReadOnlyComposable get() = LocalAppColors.current.done
    val checkBorder: Color @Composable @ReadOnlyComposable get() = LocalAppColors.current.checkBorder
    val tagBackground: Color @Composable @ReadOnlyComposable get() = LocalAppColors.current.tagBackground
    val tagText: Color @Composable @ReadOnlyComposable get() = LocalAppColors.current.tagText
    val primaryBg: Color @Composable @ReadOnlyComposable get() = LocalAppColors.current.primaryBg
    val primaryText: Color @Composable @ReadOnlyComposable get() = LocalAppColors.current.primaryText
}

/** Réglage Apparence (Paramètres), retenu par téléphone : comme bettertodo.theme sur le site. */
enum class ThemeMode(val key: String, val label: String) {
    SYSTEM("system", "Système"),
    LIGHT("light", "Clair"),
    DARK("dark", "Sombre");

    companion object {
        /** Valeur enregistrée → mode (Système si inconnue). */
        fun fromKey(key: String?): ThemeMode = entries.firstOrNull { it.key == key } ?: SYSTEM
    }
}

/** Sombre ou non : Système suit le réglage du téléphone. */
@Composable
fun ThemeMode.isDark(): Boolean = when (this) {
    ThemeMode.SYSTEM -> isSystemInDarkTheme()
    ThemeMode.LIGHT -> false
    ThemeMode.DARK -> true
}

/** Thème Material 3 de l'app, clair ou sombre, avec les couleurs du site. */
@Composable
fun BetterTodoTheme(dark: Boolean, content: @Composable () -> Unit) {
    val c = if (dark) DarkColors else LightColors
    val base = if (dark) darkColorScheme() else lightColorScheme()
    val colors = base.copy(
        primary = c.accent,
        onPrimary = Color.White,
        background = c.background,
        onBackground = c.text,
        surface = c.background,
        onSurface = c.text,
        secondaryContainer = c.accentLight, // Puce sélectionnée (échéance, urgence…)
        onSecondaryContainer = c.accent,
        surfaceVariant = c.hover,
        onSurfaceVariant = c.muted,
        surfaceContainerHighest = c.surface, // Fond des champs de saisie
        surfaceContainerHigh = c.surface,    // Fond du calendrier et des boîtes de dialogue
        surfaceContainerLow = c.surface,     // Fond des panneaux du bas
        surfaceContainer = c.surface,        // Fond des menus déroulants
        outline = c.border,
        outlineVariant = c.border,
        error = c.danger,
    )
    CompositionLocalProvider(LocalAppColors provides c) {
        MaterialTheme(colorScheme = colors, content = content)
    }
}
