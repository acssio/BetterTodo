package io.github.acssio.bettertodo.ui

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.composables.icons.lucide.R // Icônes de la bibliothèque icons-lucide-android
import io.github.acssio.bettertodo.ui.theme.Palette

/**
 * Menu déroulant : un bouton avec le choix actuel, qui ouvre la liste des choix
 * (groupe d'une tâche, liste et groupe de la Quick task).
 *
 * @param options paires (id, nom affiché), dans l'ordre
 * @param placeholder affiché quand rien n'est choisi (ex. pendant le chargement)
 */
@Composable
fun Dropdown(
    options: List<Pair<String, String>>,
    selectedId: String?,
    onSelect: (String) -> Unit,
    modifier: Modifier = Modifier,
    placeholder: String = "",
) {
    var open by rememberSaveable { mutableStateOf(false) }
    val selectedName = options.firstOrNull { it.first == selectedId }?.second ?: placeholder

    Box(modifier) {
        OutlinedButton(onClick = { open = true }, enabled = options.isNotEmpty()) {
            Text(selectedName, color = Palette.text, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f, fill = false))
            Icon(painterResource(R.drawable.lucide_ic_chevron_down), contentDescription = null, tint = Palette.muted, modifier = Modifier.padding(start = 6.dp).size(16.dp))
        }
        DropdownMenu(expanded = open, onDismissRequest = { open = false }) {
            options.forEach { (id, name) ->
                DropdownMenuItem(
                    text = { Text(name, fontWeight = if (id == selectedId) FontWeight.SemiBold else null) },
                    onClick = {
                        open = false
                        onSelect(id)
                    },
                )
            }
        }
    }
}
