package io.github.acssio.bettertodo.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.composables.icons.lucide.R // Icônes de la bibliothèque icons-lucide-android
import io.github.acssio.bettertodo.api.SmartList
import io.github.acssio.bettertodo.ui.theme.Palette

// Paramètres › Listes intelligentes (8-6), comme la partie du même nom de SettingsDialog et SmartListDialog du site.

/**
 * Aujourd'hui et Planifié (interrupteurs), puis chaque liste perso : icône, nom, filtre,
 * interrupteur « affichée sur l'accueil », crayon. « Nouvelle liste intelligente » en dessous.
 */
@Composable
fun SmartListsSection(model: SettingsModel) {
    Text("Affichées en haut de l'accueil.", color = Palette.muted, fontSize = 14.sp)
    Column {
        SmartRow(SmartKind.TODAY.icon, SmartKind.TODAY.color, SmartKind.TODAY.title, null, model.user.showToday, model::setShowToday)
        SmartRow(SmartKind.SCHEDULED.icon, SmartKind.SCHEDULED.color, SmartKind.SCHEDULED.title, null, model.user.showScheduled, model::setShowScheduled)
        model.smartLists.forEach { list ->
            SmartRow(
                ListIcons.drawable(list.icon), list.color, list.name, list.summary, list.visible,
                onToggle = { model.setSmartVisible(list, it) },
                onEdit = { model.openSmartForm(list) },
            )
        }
    }
    TextButton(onClick = { model.openSmartForm(null) }) {
        Icon(painterResource(R.drawable.lucide_ic_plus), contentDescription = null, modifier = Modifier.size(16.dp))
        Text("  Nouvelle liste intelligente")
    }
}

/** Une ligne : icône, nom (et filtre en petit), interrupteur, crayon (listes perso seulement). */
@Composable
private fun SmartRow(icon: Int, color: String, title: String, summary: String?, shown: Boolean, onToggle: (Boolean) -> Unit, onEdit: (() -> Unit)? = null) {
    Row(
        Modifier.fillMaxWidth().heightIn(min = 52.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        SmartIconBox(icon, color, size = 28)
        Column(Modifier.weight(1f)) {
            Text(title, fontSize = 15.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
            summary?.let { Text(it, fontSize = 12.sp, color = Palette.muted, maxLines = 1, overflow = TextOverflow.Ellipsis) }
        }
        if (onEdit != null) {
            IconButton(onClick = onEdit) {
                Icon(painterResource(R.drawable.lucide_ic_pencil), contentDescription = "Modifier $title", tint = Palette.muted, modifier = Modifier.size(18.dp))
            }
        }
        Switch(checked = shown, onCheckedChange = onToggle)
    }
}

/**
 * Panneau « Nouvelle liste intelligente » / « Modifier la liste intelligente » : nom, type (par tags / par urgence),
 * tags et « au moins un / tous », ou niveaux d'urgence, couleur et icône. Rien n'est enregistré avant « Créer » / « Enregistrer ».
 */
@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun SmartListSheet(model: SettingsModel, form: SmartListForm) {
    var query by rememberSaveable { mutableStateOf("") }

    ModalBottomSheet(
        onDismissRequest = model::closeSmartForm,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        containerColor = Palette.surface,
    ) {
        val focusManager = LocalFocusManager.current // Dans le panneau : fenêtre à part (voir 6-3)
        Column(
            Modifier.fillMaxWidth().imePadding().verticalScroll(rememberScrollState()).padding(start = 24.dp, end = 16.dp, bottom = 24.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    if (form.id == null) "Nouvelle liste intelligente" else "Modifier la liste intelligente",
                    fontSize = 18.sp,
                    fontWeight = FontWeight.SemiBold,
                    modifier = Modifier.weight(1f),
                )
                IconButton(onClick = model::closeSmartForm) {
                    Icon(painterResource(R.drawable.lucide_ic_x), contentDescription = "Fermer", modifier = Modifier.size(20.dp))
                }
            }

            SheetField("Nom") {
                OutlinedTextField(
                    value = form.name,
                    onValueChange = { model.editSmartForm(form.copy(name = it)) },
                    placeholder = { Text("Ex. Urgent, Travail…") },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences, imeAction = ImeAction.Done),
                    keyboardActions = KeyboardActions(onDone = { focusManager.clearFocus() }),
                    modifier = Modifier.fillMaxWidth().padding(end = 8.dp),
                )
            }

            SheetField("Afficher les tâches") {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    FilterChip(form.kind == SmartList.KIND_TAGS, onClick = { model.editSmartForm(form.copy(kind = SmartList.KIND_TAGS)) }, label = { Text("Par tags") })
                    FilterChip(form.kind == SmartList.KIND_URGENCY, onClick = { model.editSmartForm(form.copy(kind = SmartList.KIND_URGENCY)) }, label = { Text("Par urgence") })
                }
            }

            if (form.kind == SmartList.KIND_TAGS) {
                SheetField("Tags") {
                    if (model.tags.isEmpty()) {
                        Text("Aucun tag pour l'instant : ajoute d'abord des tags à tes tâches.", color = Palette.muted, fontSize = 14.sp)
                    }
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        model.tags.forEach { tag ->
                            FilterChip(tag.id in form.tagIds, onClick = { model.editSmartForm(form.toggleTag(tag.id)) }, label = { Text("#${tag.name}") })
                        }
                    }
                }
                SheetField("Une tâche doit avoir") {
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        FilterChip(form.tagMatch == SmartList.MATCH_ANY, onClick = { model.editSmartForm(form.copy(tagMatch = SmartList.MATCH_ANY)) }, label = { Text("Au moins un") })
                        FilterChip(form.tagMatch == SmartList.MATCH_ALL, onClick = { model.editSmartForm(form.copy(tagMatch = SmartList.MATCH_ALL)) }, label = { Text("Tous") })
                    }
                }
            } else {
                SheetField("Niveaux d'urgence") {
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        model.levels.sortedByDescending { it.rank }.forEach { level -> // Du plus urgent au moins urgent
                            FilterChip(
                                level.id in form.urgencyIds,
                                onClick = { model.editSmartForm(form.toggleUrgency(level.id)) },
                                label = { Text(level.name) },
                                leadingIcon = { Box(Modifier.size(8.dp).background(ListIcons.color(level.color), CircleShape)) },
                            )
                        }
                    }
                }
            }

            SheetField("Couleur") {
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    ListIcons.colors.forEach { hex ->
                        ColorDot(hex, selected = hex.equals(form.color, ignoreCase = true)) { model.editSmartForm(form.copy(color = hex)) }
                    }
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
                if (found.isEmpty()) Text("Aucune icône pour « $query »", color = Palette.muted)
                FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    found.forEach { icon -> IconChoice(icon, form.color, selected = icon == form.icon) { model.editSmartForm(form.copy(icon = icon)) } }
                }
            }

            model.smartError?.let { Text(it, color = Palette.danger) }

            Button(onClick = model::saveSmartForm, enabled = form.canSave, modifier = Modifier.fillMaxWidth().padding(end = 8.dp).height(48.dp)) {
                Text(if (form.id == null) "Créer" else "Enregistrer")
            }
            if (form.id != null) {
                TextButton(onClick = model::askDeleteSmart) {
                    Icon(painterResource(R.drawable.lucide_ic_trash_2), contentDescription = null, tint = Palette.danger, modifier = Modifier.size(16.dp))
                    Text("  Supprimer la liste intelligente", color = Palette.danger)
                }
            }
        }
    }
}
