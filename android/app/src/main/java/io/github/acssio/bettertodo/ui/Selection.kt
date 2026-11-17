package io.github.acssio.bettertodo.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.composables.icons.lucide.R // Icônes de la bibliothèque icons-lucide-android
import io.github.acssio.bettertodo.api.TodoList
import io.github.acssio.bettertodo.ui.theme.Palette

// Mode sélection (appui long), commun à une liste et à Aujourd'hui / Planifié,
// comme SelectionHeader / SelectionActions / MoveTasksSheet sur le site mobile.

/** « 1 tâche » / « 3 tâches ». */
fun taskCount(count: Int): String = if (count > 1) "$count tâches" else "1 tâche"

/** En-tête pendant la sélection : « Annuler · N sélectionnées · Tout ». */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SelectionTopBar(model: TaskStore) {
    val count = model.selectedIds.size
    TopAppBar(
        navigationIcon = {
            TextButton(onClick = model::clearSelection) { Text("Annuler") }
        },
        title = {
            Text(if (count > 1) "$count sélectionnées" else "1 sélectionnée", fontWeight = FontWeight.SemiBold, fontSize = 18.sp)
        },
        actions = {
            TextButton(onClick = model::selectAll) { Text("Tout") }
        },
        colors = TopAppBarDefaults.topAppBarColors(containerColor = Palette.accentLight),
    )
}

/**
 * Barre du bas pendant la sélection : « Déplacer » et « Supprimer ».
 * Gère aussi la confirmation de suppression et le panneau « Déplacer ».
 *
 * @param lists les listes proposées par « Déplacer », dans l'ordre de l'accueil
 */
@Composable
fun SelectionBottomBar(model: TaskStore, lists: List<TodoList>) {
    var confirmingDelete by rememberSaveable { mutableStateOf(false) }
    val count = model.selectedIds.size

    Surface(color = Palette.surface) {
        Column(Modifier.navigationBarsPadding()) {
            HorizontalDivider(color = Palette.border)
            Row(Modifier.fillMaxWidth().padding(8.dp), horizontalArrangement = Arrangement.SpaceEvenly) {
                TextButton(onClick = { model.startMove(lists) }, enabled = lists.isNotEmpty()) {
                    Icon(painterResource(R.drawable.lucide_ic_arrow_right_left), contentDescription = null, modifier = Modifier.size(18.dp))
                    Text("  Déplacer")
                }
                TextButton(onClick = { confirmingDelete = true }) {
                    Icon(painterResource(R.drawable.lucide_ic_trash_2), contentDescription = null, tint = Palette.danger, modifier = Modifier.size(18.dp))
                    Text("  Supprimer", color = Palette.danger)
                }
            }
        }
    }

    if (confirmingDelete) {
        AlertDialog(
            onDismissRequest = { confirmingDelete = false },
            title = { Text("Supprimer ${taskCount(count)} ?") },
            text = { Text(if (count > 1) "Elles seront supprimées définitivement." else "Elle sera supprimée définitivement.") },
            confirmButton = {
                TextButton(onClick = {
                    confirmingDelete = false
                    model.deleteSelected()
                }) { Text("Supprimer", color = Palette.danger) }
            },
            dismissButton = {
                TextButton(onClick = { confirmingDelete = false }) { Text("Annuler") }
            },
        )
    }

    model.moving?.let { picker ->
        MoveTasksSheet(picker, count, onMove = model::confirmMove, onClose = model::cancelMove)
    }
}

/** « Déplacer N tâches vers… » : la liste et le groupe, puis « Déplacer » (à la fin du groupe). */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun MoveTasksSheet(picker: PlacePicker, count: Int, onMove: () -> Unit, onClose: () -> Unit) {
    ModalBottomSheet(
        onDismissRequest = onClose,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        containerColor = Palette.surface,
    ) {
        Column(
            Modifier.fillMaxWidth().imePadding().padding(start = 24.dp, end = 16.dp, bottom = 24.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    if (count > 1) "Déplacer $count tâches vers…" else "Déplacer la tâche vers…",
                    fontSize = 18.sp,
                    fontWeight = FontWeight.SemiBold,
                    modifier = Modifier.weight(1f),
                )
                IconButton(onClick = onClose) {
                    Icon(painterResource(R.drawable.lucide_ic_x), contentDescription = "Fermer", modifier = Modifier.size(20.dp))
                }
            }

            PlacePickerRow(picker)

            Button(
                onClick = onMove,
                enabled = picker.groupId != null,
                modifier = Modifier.fillMaxWidth().padding(end = 8.dp).height(48.dp),
            ) {
                Text("Déplacer", color = Color.White)
            }
        }
    }
}
