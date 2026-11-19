package io.github.acssio.bettertodo

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.ui.Modifier
import io.github.acssio.bettertodo.ui.App
import io.github.acssio.bettertodo.ui.theme.BetterTodoTheme

/**
 * Point d'entrée de l'app (déclaré dans AndroidManifest.xml).
 * Elle ne fait qu'afficher l'interface Compose : toute la logique est dans ui/App.kt.
 */
class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge() // L'app dessine sous les barres du système ; Scaffold et safeDrawingPadding gardent les marges
        setContent {
            BetterTodoTheme {
                Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
                    App()
                }
            }
        }
    }
}
