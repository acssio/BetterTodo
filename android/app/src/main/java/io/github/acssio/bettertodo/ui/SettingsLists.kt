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
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.composables.icons.lucide.R // Icônes de la bibliothèque icons-lucide-android
import io.github.acssio.bettertodo.api.Tag
import io.github.acssio.bettertodo.api.UrgencyLevel
import io.github.acssio.bettertodo.ui.theme.Palette

// Paramètres, étape 6-7b : niveaux d'urgence et tags (comme les parties correspondantes de SettingsDialog du site).

/**
 * Niveaux d'urgence, du moins au plus urgent : point de couleur (toucher = changer), nom (toucher = renommer),
 * flèches ↑ ↓ pour réordonner, corbeille ; « Ajouter un niveau » en dessous.
 */
@Composable
fun UrgencyLevelsSection(model: SettingsModel) {
    Text("Du moins urgent au plus urgent.", color = Palette.muted, fontSize = 14.sp)
    Column {
        model.levels.forEachIndexed { index, level ->
            Row(Modifier.fillMaxWidth().heightIn(min = 48.dp), verticalAlignment = Alignment.CenterVertically) {
                Box(
                    Modifier
                        .size(40.dp)
                        .clip(CircleShape)
                        .clickable { model.ask(SettingsDialog.LevelColor(level)) }
                        .semantics { contentDescription = "Couleur de ${level.name}" },
                    contentAlignment = Alignment.Center,
                ) {
                    Box(Modifier.size(18.dp).background(ListIcons.color(level.color), CircleShape))
                }
                Text(
                    level.name,
                    fontSize = 16.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier
                        .weight(1f)
                        .clickable { model.ask(SettingsDialog.RenameLevel(level)) }
                        .padding(vertical = 12.dp),
                )
                SmallIcon(R.drawable.lucide_ic_arrow_up, "Monter ${level.name} (moins urgent)", enabled = index > 0) { model.moveLevel(level, -1) }
                SmallIcon(R.drawable.lucide_ic_arrow_down, "Descendre ${level.name} (plus urgent)", enabled = index < model.levels.size - 1) { model.moveLevel(level, 1) }
                SmallIcon(R.drawable.lucide_ic_trash_2, "Supprimer ${level.name}", tint = Palette.danger) { model.ask(SettingsDialog.DeleteLevel(level)) }
            }
        }
    }
    TextButton(onClick = { model.ask(SettingsDialog.NewLevel) }) {
        Icon(painterResource(R.drawable.lucide_ic_plus), contentDescription = null, modifier = Modifier.size(16.dp))
        Text("  Ajouter un niveau")
    }
}

/** Tags : nom (toucher = renommer), nombre de tâches, corbeille ; « Nouveau tag » en dessous. */
@Composable
fun TagsSection(model: SettingsModel) {
    if (model.tags.isEmpty()) {
        Text("Aucun tag pour l'instant. Ils se créent aussi en les ajoutant à une tâche.", color = Palette.muted, fontSize = 14.sp)
    }
    Column {
        model.tags.forEach { tag ->
            Row(Modifier.fillMaxWidth().heightIn(min = 48.dp), verticalAlignment = Alignment.CenterVertically) {
                Text(
                    "#${tag.name}",
                    fontSize = 16.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier
                        .weight(1f)
                        .clickable { model.ask(SettingsDialog.RenameTag(tag)) }
                        .padding(vertical = 12.dp),
                )
                Text(taskCount(tag.taskCount), color = Palette.faint, fontSize = 13.sp)
                SmallIcon(R.drawable.lucide_ic_trash_2, "Supprimer ${tag.name}", tint = Palette.danger) { model.ask(SettingsDialog.DeleteTag(tag)) }
            }
        }
    }
    TextButton(onClick = { model.ask(SettingsDialog.NewTag) }) {
        Icon(painterResource(R.drawable.lucide_ic_plus), contentDescription = null, modifier = Modifier.size(16.dp))
        Text("  Nouveau tag")
    }
}

/** Les boîtes des Paramètres (SettingsModel.dialog). */
@Composable
fun SettingsDialogs(model: SettingsModel) {
    when (val dialog = model.dialog) {
        SettingsDialog.NewLevel ->
            NameDialog("Nouveau niveau d'urgence", "Nom du niveau", "Créer", onConfirm = model::submitName, onDismiss = model::closeDialog)
        is SettingsDialog.RenameLevel ->
            NameDialog("Renommer le niveau", "Nom du niveau", "Renommer", dialog.level.name, onConfirm = model::submitName, onDismiss = model::closeDialog)
        is SettingsDialog.LevelColor -> ColorDialog(dialog.level, onPick = { model.setLevelColor(dialog.level, it) }, onDismiss = model::closeDialog)
        is SettingsDialog.DeleteLevel ->
            ConfirmDialog("Supprimer le niveau ?", deleteLevelMessage(dialog.level), "Supprimer", model::confirm, model::closeDialog)
        SettingsDialog.NewTag ->
            NameDialog("Nouveau tag", "Nom du tag", "Créer", capitalize = false, onConfirm = model::submitName, onDismiss = model::closeDialog)
        is SettingsDialog.RenameTag ->
            NameDialog("Renommer le tag", "Nom du tag", "Renommer", dialog.tag.name, capitalize = false, onConfirm = model::submitName, onDismiss = model::closeDialog)
        is SettingsDialog.DeleteTag ->
            ConfirmDialog("Supprimer le tag ?", deleteTagMessage(dialog.tag), "Supprimer", model::confirm, model::closeDialog)
        null -> {}
    }
}

/** Choisir la couleur d'un niveau parmi les 8 de la palette (celles des listes, comme le site). */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun ColorDialog(level: UrgencyLevel, onPick: (String) -> Unit, onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Couleur de « ${level.name} »") },
        text = {
            FlowRow(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                ListIcons.colors.forEach { hex ->
                    val color = ListIcons.color(hex)
                    val selected = hex.equals(level.color, ignoreCase = true)
                    Box(
                        Modifier
                            .size(40.dp)
                            .clip(CircleShape)
                            .border(2.dp, if (selected) color else Color.Transparent, CircleShape)
                            .clickable { onPick(hex) }
                            .padding(5.dp)
                            .background(color, CircleShape)
                            .semantics { contentDescription = "Couleur $hex" + if (selected) " (choisie)" else "" },
                    )
                }
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text("Fermer") } },
    )
}

/** Petite icône cliquable d'une ligne (flèches, corbeille). */
@Composable
private fun SmallIcon(icon: Int, description: String, enabled: Boolean = true, tint: Color = Palette.muted, onClick: () -> Unit) {
    IconButton(onClick = onClick, enabled = enabled, modifier = Modifier.size(40.dp)) {
        Icon(
            painterResource(icon),
            contentDescription = description,
            tint = if (enabled) tint else Palette.border,
            modifier = Modifier.size(18.dp),
        )
    }
}
