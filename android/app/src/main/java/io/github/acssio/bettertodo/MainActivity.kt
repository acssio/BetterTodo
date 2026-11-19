package io.github.acssio.bettertodo

import android.graphics.Color
import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import io.github.acssio.bettertodo.data.AppearanceStore
import io.github.acssio.bettertodo.notify.Notifier
import io.github.acssio.bettertodo.ui.App
import io.github.acssio.bettertodo.ui.AppLinks
import io.github.acssio.bettertodo.ui.theme.BetterTodoTheme
import io.github.acssio.bettertodo.ui.theme.ThemeMode
import io.github.acssio.bettertodo.ui.theme.isDark

/**
 * Point d'entrée de l'app (déclaré dans AndroidManifest.xml).
 * Elle ne fait qu'afficher l'interface Compose : toute la logique est dans ui/App.kt.
 * Elle suit aussi le réglage Apparence (AppearanceStore) pour passer en clair ou en sombre.
 */
class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        Notifier.createChannels(this)
        handleOpen(intent)
        enableEdgeToEdge() // Dès la première image ; le style des icônes est corrigé plus bas selon le thème
        val appearance = AppearanceStore(this)
        setContent {
            val mode by appearance.mode.collectAsState(initial = ThemeMode.SYSTEM)
            val dark = mode.isDark()

            // L'app dessine sous les barres du système (Scaffold et safeDrawingPadding gardent les marges).
            // Icônes de la barre d'état claires sur fond sombre, et l'inverse : selon le thème de l'app,
            // pas seulement celui du téléphone (on peut forcer Sombre sur un téléphone en clair).
            LaunchedEffect(dark) {
                val style = if (dark) SystemBarStyle.dark(Color.TRANSPARENT)
                else SystemBarStyle.light(Color.TRANSPARENT, Color.TRANSPARENT)
                enableEdgeToEdge(statusBarStyle = style, navigationBarStyle = style)
            }

            BetterTodoTheme(dark) {
                Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
                    App()
                }
            }
        }
    }

    /** App déjà ouverte (launchMode singleTop) : une notification touchée arrive ici. */
    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        handleOpen(intent)
    }

    /** Une notification demande d'ouvrir « Aujourd'hui ». */
    private fun handleOpen(intent: Intent?) {
        if (intent?.getStringExtra(EXTRA_OPEN) == OPEN_TODAY) AppLinks.openToday = true
    }

    companion object {
        const val EXTRA_OPEN = "open"
        const val OPEN_TODAY = "today"
    }
}
