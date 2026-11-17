package io.github.acssio.bettertodo.ui

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.clickable
import androidx.compose.foundation.relocation.BringIntoViewRequester
import androidx.compose.foundation.relocation.bringIntoViewRequester
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.composables.icons.lucide.R // Icônes de la bibliothèque icons-lucide-android
import io.github.acssio.bettertodo.ui.theme.Palette
import kotlinx.coroutines.delay

/**
 * « + Ajouter une tâche » en bas d'un groupe (comme AddTask sur le site).
 * Toucher → un champ. ↵ ajoute la tâche et **garde le champ ouvert** pour enchaîner.
 * ↵ sur un champ vide, ou ✕, referme.
 *
 * Machine d'état : fermé ──(toucher)──→ ouvert ──(↵ vide / ✕)──→ fermé
 *                                         └──(↵ avec texte)──→ ouvert (tâche ajoutée, champ vidé)
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun AddTaskRow(onAdd: (String) -> Unit, modifier: Modifier = Modifier) {
    var open by rememberSaveable { mutableStateOf(false) }
    var title by rememberSaveable { mutableStateOf("") }

    if (!open) {
        Row(
            modifier
                .fillMaxWidth()
                .heightIn(min = 44.dp)
                .clip(RoundedCornerShape(10.dp))
                .clickable { open = true }
                .padding(horizontal = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Icon(painterResource(R.drawable.lucide_ic_plus), contentDescription = null, tint = Palette.muted, modifier = Modifier.size(18.dp))
            Text("Ajouter une tâche", color = Palette.muted, fontSize = 15.sp)
        }
        return
    }

    val focus = remember { FocusRequester() }
    val bringIntoView = remember { BringIntoViewRequester() }
    var added by remember { mutableIntStateOf(0) } // Nombre de tâches ajoutées : relance le défilement

    LaunchedEffect(Unit) { focus.requestFocus() } // Le clavier s'ouvre tout de suite

    // À l'ouverture et après chaque ajout (la tâche ajoutée au-dessus pousse le champ vers le bas) :
    // on attend que le clavier et la nouvelle tâche aient pris leur place, puis on ramène le champ en vue
    LaunchedEffect(added) {
        delay(300)
        bringIntoView.bringIntoView()
    }

    val close = {
        open = false
        title = ""
    }

    OutlinedTextField(
        value = title,
        onValueChange = { title = it },
        placeholder = { Text("Titre de la tâche") },
        singleLine = true,
        keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences, imeAction = ImeAction.Done),
        keyboardActions = KeyboardActions(onDone = {
            // Pas d'appel à l'action par défaut : le clavier reste ouvert pour la tâche suivante
            if (title.isBlank()) {
                close()
            } else {
                onAdd(title)
                title = ""
                added++
            }
        }),
        trailingIcon = {
            IconButton(onClick = close) {
                Icon(painterResource(R.drawable.lucide_ic_x), contentDescription = "Fermer", modifier = Modifier.size(18.dp))
            }
        },
        colors = OutlinedTextFieldDefaults.colors(focusedContainerColor = Palette.surface, unfocusedContainerColor = Palette.surface),
        modifier = modifier.fillMaxWidth().padding(vertical = 4.dp).bringIntoViewRequester(bringIntoView).focusRequester(focus),
    )
}
