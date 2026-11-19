package io.github.acssio.bettertodo.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.composables.icons.lucide.R // Icônes de la bibliothèque icons-lucide-android
import io.github.acssio.bettertodo.ui.theme.Palette

/**
 * Premier lancement (8-3) : l'app marche sans serveur, c'est le choix proposé en premier.
 * Un serveur peut être ajouté maintenant (« J'ai un serveur ») ou plus tard dans les Paramètres.
 */
@Composable
fun WelcomeScreen(onStartLocal: () -> Unit, onConnect: () -> Unit) {
    Box(Modifier.fillMaxSize().safeDrawingPadding(), contentAlignment = Alignment.Center) {
        Column(
            Modifier.widthIn(max = 420.dp).fillMaxWidth().padding(24.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            // Logo : coche sur carré foncé, comme le site
            Box(
                Modifier.size(44.dp).background(Palette.primaryBg, RoundedCornerShape(12.dp)),
                contentAlignment = Alignment.Center,
            ) {
                Icon(painterResource(R.drawable.lucide_ic_check), contentDescription = null, tint = Palette.primaryText)
            }
            Text("BetterTodo", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.SemiBold)
            Text(
                "Tes listes restent sur ce téléphone. Tu pourras ajouter ton serveur BetterTodo plus tard, dans les Paramètres.",
                color = Palette.muted,
            )
            Spacer(Modifier.height(4.dp))
            Button(onClick = onStartLocal, modifier = Modifier.fillMaxWidth().height(48.dp)) {
                Text("Commencer")
            }
            TextButton(onClick = onConnect, modifier = Modifier.fillMaxWidth()) {
                Text("J'ai un serveur BetterTodo")
            }
        }
    }
}
