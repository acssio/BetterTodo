package io.github.acssio.bettertodo.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.composables.icons.lucide.R // Icônes de la bibliothèque icons-lucide-android
import io.github.acssio.bettertodo.ui.theme.Palette

/**
 * Panneau « Quick task » (comme QuickTaskSheet du site mobile) : titre, liste, groupe, échéance.
 * ↵ ou « Créer la tâche » → création ; le panneau est fermé par HomeViewModel en cas de succès.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun QuickTaskSheet(model: QuickTaskModel, onClose: () -> Unit) {
    var title by rememberSaveable { mutableStateOf("") }
    val focus = remember { FocusRequester() }
    LaunchedEffect(Unit) { focus.requestFocus() } // Le clavier s'ouvre tout de suite

    ModalBottomSheet(
        onDismissRequest = onClose,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        containerColor = Palette.surface,
    ) {
        Column(
            Modifier
                .fillMaxWidth()
                .imePadding()
                .verticalScroll(rememberScrollState())
                .padding(start = 24.dp, end = 16.dp, bottom = 24.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("Quick task", fontSize = 18.sp, fontWeight = FontWeight.SemiBold, modifier = Modifier.weight(1f))
                IconButton(onClick = onClose) {
                    Icon(painterResource(R.drawable.lucide_ic_x), contentDescription = "Fermer", modifier = Modifier.size(20.dp))
                }
            }

            OutlinedTextField(
                value = title,
                onValueChange = { title = it },
                placeholder = { Text("Titre de la tâche") },
                singleLine = true,
                keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences, imeAction = ImeAction.Done),
                keyboardActions = KeyboardActions(onDone = { model.create(title) }),
                modifier = Modifier.fillMaxWidth().padding(end = 8.dp).focusRequester(focus),
            )

            // Liste, puis groupe de cette liste
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                Dropdown(
                    options = model.lists.map { it.id to it.name },
                    selectedId = model.listId,
                    onSelect = model::selectList,
                    modifier = Modifier.weight(1f, fill = false),
                )
                Text("›", color = Palette.muted)
                Dropdown(
                    options = model.groups.map { it.id to it.name },
                    selectedId = model.groupId,
                    onSelect = model::selectGroup,
                    placeholder = if (model.loadingGroups) "…" else "Aucun groupe",
                    modifier = Modifier.weight(1f, fill = false),
                )
            }

            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text("ÉCHÉANCE", fontSize = 12.sp, fontWeight = FontWeight.SemiBold, color = Palette.faint)
                DueDateChips(model.dueDate, onChange = model::selectDueDate)
            }

            model.error?.let { Text(it, color = Palette.danger) }

            Button(
                onClick = { model.create(title) },
                enabled = model.canCreate(title),
                modifier = Modifier.fillMaxWidth().padding(end = 8.dp).height(48.dp),
            ) {
                if (model.busy) {
                    CircularProgressIndicator(Modifier.size(20.dp), strokeWidth = 2.dp, color = Color.White)
                } else {
                    Text("Créer la tâche")
                }
            }
        }
    }
}
