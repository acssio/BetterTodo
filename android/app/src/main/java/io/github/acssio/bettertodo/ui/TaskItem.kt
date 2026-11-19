package io.github.acssio.bettertodo.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.composables.icons.lucide.R // Icônes de la bibliothèque icons-lucide-android
import io.github.acssio.bettertodo.api.ListRef
import io.github.acssio.bettertodo.api.Task
import io.github.acssio.bettertodo.api.UrgencyLevel
import io.github.acssio.bettertodo.ui.theme.Palette

/**
 * Une tâche : case ronde, titre (barré si faite), icône de note, puis tags / échéance / urgence.
 * Comme TaskCard (vue colonnes) et TaskRow (vue sections) du site, en un seul composable.
 *
 * Toucher la tâche ouvre le panneau du bas (onOpen) ; toucher la case la coche ;
 * appui long = sélection (onLongPress, avec une petite vibration ; dans une liste, c'est TaskDrag qui s'en occupe).
 *
 * @param card true = carte avec bord (vue colonnes), false = ligne (vue sections)
 * @param urgency le niveau d'urgence de la tâche, ou null
 * @param origin la liste d'origine, affichée dans Aujourd'hui / Planifié (null dans une liste)
 * @param selected dans la sélection : fond bleu clair
 */
@OptIn(ExperimentalLayoutApi::class, ExperimentalFoundationApi::class)
@Composable
fun TaskItem(
    task: Task,
    urgency: UrgencyLevel?,
    card: Boolean,
    onToggleDone: () -> Unit,
    onOpen: () -> Unit,
    onLongPress: (() -> Unit)?,
    modifier: Modifier = Modifier,
    origin: ListRef? = null,
    selected: Boolean = false,
) {
    val haptic = LocalHapticFeedback.current
    val shape = RoundedCornerShape(10.dp)
    val background = if (selected) Palette.accentLight else Palette.surface
    val taps = Modifier.combinedClickable(
        onClickLabel = "Ouvrir la tâche",
        onLongClickLabel = "Sélectionner",
        onClick = onOpen,
        // null dans une liste : l'appui long y est géré par le glisser (TaskDrag), au niveau de l'écran
        onLongClick = onLongPress?.let { action ->
            {
                haptic.performHapticFeedback(HapticFeedbackType.LongPress) // Petite vibration, comme les apps Android
                action()
            }
        },
    )
    val frame = if (card) {
        Modifier
            .clip(shape)
            .background(background)
            .border(1.dp, if (selected) Palette.accent else Palette.border, shape)
            .then(taps)
            .padding(end = 12.dp, top = 4.dp, bottom = 4.dp)
    } else {
        Modifier
            .background(background)
            .then(taps)
            .padding(end = 16.dp, top = 2.dp, bottom = 2.dp)
    }

    Row(modifier.fillMaxWidth().then(frame), verticalAlignment = Alignment.Top) {
        CheckCircle(task.done, onToggleDone)
        Column(Modifier.weight(1f).padding(top = 12.dp, bottom = 10.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                Text(
                    task.title,
                    fontSize = 15.sp,
                    color = if (task.done) Palette.done else Palette.text,
                    textDecoration = if (task.done) TextDecoration.LineThrough else null,
                    maxLines = 3,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f, fill = false),
                )
                if (task.notes.isNotEmpty()) {
                    Icon(painterResource(R.drawable.lucide_ic_sticky_note), contentDescription = "A une note", tint = Palette.faint, modifier = Modifier.size(14.dp))
                }
            }

            if (origin != null || task.tags.isNotEmpty() || task.dueDate != null || urgency != null) {
                FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    origin?.let { ListOrigin(it) }
                    task.tags.forEach { tag -> TagChip(tag) }
                    task.dueDate?.let { DueDate(it, task.dueTime, task.done) }
                    urgency?.let { UrgencyPill(it) }
                }
            }
        }
    }
}

/** Case ronde de 20 dp, dans une zone de 44 dp pour être facile à toucher (aussi utilisée par TaskSheet). */
@Composable
fun CheckCircle(done: Boolean, onToggle: () -> Unit) {
    val label = if (done) "Marquer comme à faire" else "Marquer comme faite"
    Box(
        Modifier
            .size(44.dp)
            .clip(CircleShape)
            .clickable(onClick = onToggle)
            .semantics { contentDescription = label },
        contentAlignment = Alignment.Center,
    ) {
        val circle = Modifier.size(20.dp)
        if (done) {
            Box(circle.background(Palette.success, CircleShape), contentAlignment = Alignment.Center) {
                Icon(painterResource(R.drawable.lucide_ic_check), contentDescription = null, tint = Color.White, modifier = Modifier.size(13.dp))
            }
        } else {
            Box(circle.border(1.5.dp, Palette.checkBorder, CircleShape))
        }
    }
}

/** Liste d'origine : petite icône dans sa couleur et nom (Aujourd'hui / Planifié). */
@Composable
private fun ListOrigin(list: ListRef) {
    val color = ListIcons.color(list.color)
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
        Icon(painterResource(ListIcons.drawable(list.icon)), contentDescription = null, tint = color, modifier = Modifier.size(12.dp))
        Text(list.name, fontSize = 12.sp, color = Palette.muted, maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
}

@Composable
private fun TagChip(tag: String) {
    Text(
        "#$tag",
        fontSize = 12.sp,
        color = Palette.tagText,
        modifier = Modifier.background(Palette.tagBackground, RoundedCornerShape(5.dp)).padding(horizontal = 6.dp, vertical = 2.dp),
    )
}

/** Échéance, en rouge si c'est aujourd'hui ou en retard (sauf si la tâche est faite). */
@Composable
private fun DueDate(iso: String, time: String?, done: Boolean) {
    val late = !done && DueDates.isDueOrLate(iso)
    val color = if (late) Palette.danger else Palette.muted
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
        Icon(painterResource(R.drawable.lucide_ic_calendar), contentDescription = null, tint = color, modifier = Modifier.size(12.dp))
        Text(DueDates.format(iso, time), fontSize = 12.sp, color = color, fontWeight = if (late) FontWeight.SemiBold else null)
    }
}

/** Pastille d'urgence : texte et point dans la couleur du niveau, fond très clair. */
@Composable
private fun UrgencyPill(urgency: UrgencyLevel) {
    val color = ListIcons.color(urgency.color)
    Row(
        Modifier.background(color.copy(alpha = 0.12f), RoundedCornerShape(10.dp)).padding(horizontal = 8.dp, vertical = 2.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(5.dp),
    ) {
        Box(Modifier.size(6.dp).background(color, CircleShape))
        Text(urgency.name, fontSize = 11.sp, fontWeight = FontWeight.SemiBold, color = color)
    }
}
