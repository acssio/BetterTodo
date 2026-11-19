package io.github.acssio.bettertodo.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.composables.icons.lucide.R // Icônes de la bibliothèque icons-lucide-android
import io.github.acssio.bettertodo.api.BoardGroup
import io.github.acssio.bettertodo.api.Folder
import io.github.acssio.bettertodo.api.TodoList
import io.github.acssio.bettertodo.ui.theme.Palette

// Gérer une liste de l'intérieur (étape 6-6b) : menu « … » de la liste, menu d'un groupe,
// boîtes de nom et de confirmation, panneau « Modifier la liste ».

/**
 * Menu « … » de l'en-tête d'une liste (comme le menu « Options de la liste » du site).
 * En vue colonnes, il propose aussi de renommer / supprimer le groupe affiché.
 */
@Composable
fun ListMenu(model: BoardModel) {
    var open by rememberSaveable { mutableStateOf(false) }
    val list = model.currentList
    val sections = list.defaultView == TodoList.VIEW_SECTIONS
    val shownGroup = if (sections) null else model.board?.groups?.firstOrNull { it.id == model.shownGroupId }
    val done = model.doneCount

    Box {
        IconButton(onClick = { open = true }) {
            Icon(painterResource(R.drawable.lucide_ic_ellipsis), contentDescription = "Options de la liste", modifier = Modifier.size(22.dp))
        }
        DropdownMenu(expanded = open, onDismissRequest = { open = false }) {
            MenuItem("Modifier la liste", R.drawable.lucide_ic_pencil) { open = false; model.openListEditor() }
            MenuItem(
                if (sections) "Afficher en colonnes" else "Afficher en sections",
                if (sections) R.drawable.lucide_ic_columns_3 else R.drawable.lucide_ic_rows_3,
            ) { open = false; model.toggleView() }
            MenuItem(
                if (list.showDone) "Masquer les tâches faites" else "Afficher les tâches faites ($done)",
                if (list.showDone) R.drawable.lucide_ic_eye_off else R.drawable.lucide_ic_eye,
            ) { open = false; model.toggleShowDone() }
            if (done > 0) {
                MenuItem("Vider les tâches faites ($done)", R.drawable.lucide_ic_trash_2, danger = true) { open = false; model.askClearDone() }
            }
            MenuItem("Nouveau groupe", R.drawable.lucide_ic_plus) { open = false; model.askNewGroup() }
            if (shownGroup != null) {
                MenuItem("Renommer « ${shownGroup.name} »", R.drawable.lucide_ic_pencil) { open = false; model.askRenameGroup(shownGroup) }
                MenuItem("Supprimer « ${shownGroup.name} »", R.drawable.lucide_ic_trash_2, danger = true) { open = false; model.askDeleteGroup(shownGroup) }
            }
        }
    }
}

/** Menu « … » à côté du titre d'un groupe (vue sections). */
@Composable
fun GroupMenu(group: BoardGroup, model: BoardModel) {
    var open by rememberSaveable { mutableStateOf(false) }
    Box {
        IconButton(onClick = { open = true }, modifier = Modifier.size(32.dp)) {
            Icon(painterResource(R.drawable.lucide_ic_ellipsis), contentDescription = "Options de ${group.name}", tint = Palette.muted, modifier = Modifier.size(18.dp))
        }
        DropdownMenu(expanded = open, onDismissRequest = { open = false }) {
            MenuItem("Renommer", R.drawable.lucide_ic_pencil) { open = false; model.askRenameGroup(group) }
            MenuItem("Supprimer le groupe", R.drawable.lucide_ic_trash_2, danger = true) { open = false; model.askDeleteGroup(group) }
        }
    }
}

/** Les boîtes de l'écran d'une liste (BoardModel.dialog). */
@Composable
fun ListDialogs(model: BoardModel) {
    when (val dialog = model.dialog) {
        ListDialog.NewGroup ->
            NameDialog("Nouveau groupe", "Nom du groupe", "Créer", onConfirm = model::submitName, onDismiss = model::closeDialog)
        is ListDialog.RenameGroup ->
            NameDialog("Renommer le groupe", "Nom du groupe", "Renommer", dialog.group.name, onConfirm = model::submitName, onDismiss = model::closeDialog)
        is ListDialog.DeleteGroup ->
            ConfirmDialog("Supprimer le groupe ?", BoardModel.deleteGroupMessage(dialog.group), "Supprimer", model::confirm, model::closeDialog)
        ListDialog.ClearDone ->
            ConfirmDialog("Vider les tâches faites ?", BoardModel.clearDoneMessage(model.currentList.name, model.doneCount), "Vider", model::confirm, model::closeDialog)
        ListDialog.DeleteList ->
            ConfirmDialog("Supprimer la liste ?", "Supprimer la liste « ${model.currentList.name} » et toutes ses tâches ?", "Supprimer", model::confirm, model::closeDialog)
        null -> {}
    }
}

/** Confirmation d'une action qui supprime (bouton en rouge). */
@Composable
fun ConfirmDialog(title: String, text: String, confirmLabel: String, onConfirm: () -> Unit, onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = { Text(text) },
        confirmButton = { TextButton(onClick = onConfirm) { Text(confirmLabel, color = Palette.danger) } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Annuler") } },
    )
}

/**
 * Panneau « Modifier la liste » (comme ListEditPopover du site) : nom, groupe de listes,
 * couleur, icône (avec recherche), et « Supprimer la liste ».
 * Chaque champ est enregistré tout de suite ; le nom à ↵, et à la fermeture.
 */
@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun ListEditSheet(model: BoardModel, folders: List<Folder>) {
    val list = model.currentList
    val current by rememberUpdatedState(model)
    var name by rememberSaveable { mutableStateOf(list.name) }
    var query by rememberSaveable { mutableStateOf("") }

    DisposableEffect(Unit) {
        onDispose { current.setListName(name) } // Fermeture (ou rotation) : enregistre le nom en cours
    }

    ModalBottomSheet(
        onDismissRequest = model::closeListEditor,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        containerColor = Palette.surface,
    ) {
        val focusManager = LocalFocusManager.current // Dans le panneau : fenêtre à part (voir 6-3)
        Column(
            Modifier.fillMaxWidth().imePadding().verticalScroll(rememberScrollState()).padding(start = 24.dp, end = 16.dp, bottom = 24.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("Modifier la liste", fontSize = 18.sp, fontWeight = FontWeight.SemiBold, modifier = Modifier.weight(1f))
                IconButton(onClick = model::closeListEditor) {
                    Icon(painterResource(R.drawable.lucide_ic_x), contentDescription = "Fermer", modifier = Modifier.size(20.dp))
                }
            }

            SheetField("Nom") {
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences, imeAction = ImeAction.Done),
                    keyboardActions = KeyboardActions(onDone = {
                        if (name.isBlank()) name = list.name // Nom vide refusé : on remet l'ancien
                        model.setListName(name)
                        focusManager.clearFocus()
                    }),
                    modifier = Modifier.fillMaxWidth().padding(end = 8.dp),
                )
            }

            SheetField("Groupe de listes") {
                Dropdown(
                    options = listOf("" to "Aucun") + folders.map { it.id to it.name },
                    selectedId = list.folderId ?: "",
                    onSelect = { model.setListFolder(it.ifEmpty { null }) },
                )
            }

            SheetField("Couleur") {
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    ListIcons.colors.forEach { hex -> ColorDot(hex, selected = hex.equals(list.color, ignoreCase = true)) { model.setListColor(hex) } }
                }
            }

            SheetField("Icône") {
                OutlinedTextField(
                    value = query,
                    onValueChange = { query = it },
                    placeholder = { Text("Rechercher une icône…") },
                    leadingIcon = { Icon(painterResource(R.drawable.lucide_ic_search), contentDescription = null, modifier = Modifier.size(18.dp)) },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                    keyboardActions = KeyboardActions(onSearch = { focusManager.clearFocus() }),
                    modifier = Modifier.fillMaxWidth().padding(end = 8.dp),
                )
                val found = ListIcons.search(query)
                if (found.isEmpty()) {
                    Text("Aucune icône pour « $query »", color = Palette.muted)
                }
                FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    found.forEach { icon -> IconChoice(icon, list.color, selected = icon == list.icon) { model.setListIcon(icon) } }
                }
            }

            TextButton(onClick = model::askDeleteList) {
                Icon(painterResource(R.drawable.lucide_ic_trash_2), contentDescription = null, tint = Palette.danger, modifier = Modifier.size(16.dp))
                Text("  Supprimer la liste", color = Palette.danger)
            }
        }
    }
}

@Composable
fun SheetField(label: String, content: @Composable () -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Text(label.uppercase(), fontSize = 12.sp, fontWeight = FontWeight.SemiBold, color = Palette.faint)
        content()
    }
}

/** Une couleur de la palette : rond plein, anneau autour si choisie. */
@Composable
fun ColorDot(hex: String, selected: Boolean, onClick: () -> Unit) {
    val color = ListIcons.color(hex)
    Box(
        Modifier
            .size(34.dp)
            .clip(CircleShape)
            .border(2.dp, if (selected) color else Color.Transparent, CircleShape)
            .clickable(onClick = onClick)
            .padding(4.dp)
            .background(color, CircleShape)
            .semantics { contentDescription = "Couleur $hex" + if (selected) " (choisie)" else "" },
    )
}

/** Une icône à choisir : dans la couleur de la liste, sur fond teinté si choisie. */
@Composable
fun IconChoice(name: String, listColor: String, selected: Boolean, onClick: () -> Unit) {
    val color = ListIcons.color(listColor)
    val shape = RoundedCornerShape(8.dp)
    Box(
        Modifier
            .size(40.dp)
            .clip(shape)
            .background(if (selected) color.copy(alpha = 0.15f) else Color.Transparent)
            .border(1.dp, if (selected) color else Palette.border, shape)
            .clickable(onClick = onClick)
            .semantics { contentDescription = "Icône ${ListIcons.label(name)}" + if (selected) " (choisie)" else "" },
        contentAlignment = Alignment.Center,
    ) {
        Icon(painterResource(ListIcons.drawable(name)), contentDescription = null, tint = if (selected) color else Palette.muted, modifier = Modifier.size(20.dp))
    }
}
