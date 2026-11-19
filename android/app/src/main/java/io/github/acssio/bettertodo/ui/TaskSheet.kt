package io.github.acssio.bettertodo.ui

import androidx.compose.foundation.background
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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.InputChip
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.remember
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.composables.icons.lucide.R // Icônes de la bibliothèque icons-lucide-android
import io.github.acssio.bettertodo.api.Task
import io.github.acssio.bettertodo.ui.theme.Palette
import kotlinx.coroutines.delay

/** Les notes sont enregistrées ce temps après la dernière frappe (comme NOTES_SAVE_DELAY du site). */
private const val NOTES_SAVE_DELAY_MS = 800L

/**
 * Panneau du bas pour modifier une tâche (comme TaskPopover en mode « sheet » sur le site mobile),
 * depuis une liste (BoardModel) ou Aujourd'hui / Planifié (SmartModel) : il ne connaît que TaskStore.
 * Chaque champ s'enregistre tout seul, il n'y a pas de bouton « Enregistrer » :
 *  - titre : à ↵, en quittant le champ, ou à la fermeture ;
 *  - notes : 0,8 s après la dernière frappe, et à la fermeture ;
 *  - échéance, groupe, urgence, tags : tout de suite.
 *
 * Les textes en cours de frappe sont en rememberSaveable : ils survivent à la rotation.
 */
@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun TaskSheet(model: TaskStore, task: Task, onClose: () -> Unit) {
    val current by rememberUpdatedState(task) // La tâche à jour, pour les enregistrements différés
    var title by rememberSaveable(task.id) { mutableStateOf(task.title) }
    var notes by rememberSaveable(task.id) { mutableStateOf(task.notes) }
    var confirmingDelete by rememberSaveable { mutableStateOf(false) }
    // Notes : relancé à chaque frappe, donc n'enregistre que 0,8 s après la dernière
    LaunchedEffect(notes) {
        delay(NOTES_SAVE_DELAY_MS)
        model.setNotes(current, notes)
    }

    // Fermeture (ou rotation) : enregistre ce qui est en cours de frappe
    DisposableEffect(Unit) {
        onDispose {
            model.setTitle(current, title)
            model.setNotes(current, notes)
        }
    }

    ModalBottomSheet(
        onDismissRequest = onClose,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        containerColor = Palette.surface,
    ) {
        // Lu ICI, dans le panneau : ModalBottomSheet est une fenêtre à part, avec son propre focus.
        // Lu au-dessus, clearFocus() agissait sur la fenêtre de la liste et le titre gardait le focus (bug testé).
        val focusManager = LocalFocusManager.current

        /** ↵ dans le titre : enregistre et quitte le champ (le clavier se ferme). */
        fun submitTitle() {
            if (title.isBlank()) title = current.title // Titre vide refusé : on remet l'ancien
            model.setTitle(current, title)
            focusManager.clearFocus()
        }

        Column(
            Modifier
                .fillMaxWidth()
                .imePadding() // Remonte au-dessus du clavier
                .verticalScroll(rememberScrollState())
                .padding(start = 12.dp, end = 20.dp, bottom = 24.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            // --- Case + titre + fermer ---
            Row(verticalAlignment = Alignment.CenterVertically) {
                CheckCircle(task.done, onToggle = { model.toggleDone(task) })
                OutlinedTextField(
                    value = title,
                    // Sur plusieurs lignes (titres longs), donc ↵ insère un retour à la ligne au lieu de « OK » :
                    // on le retire et on valide (submitTitle)
                    onValueChange = { value ->
                        if (value.contains('\n')) {
                            title = value.replace("\n", "")
                            submitTitle()
                        } else {
                            title = value
                        }
                    },
                    textStyle = TextStyle(fontSize = 18.sp, fontWeight = FontWeight.SemiBold),
                    keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences, imeAction = ImeAction.Done),
                    keyboardActions = KeyboardActions(onDone = { submitTitle() }),
                    modifier = Modifier.weight(1f).onFocusChanged { state ->
                        if (!state.isFocused) {
                            if (title.isBlank()) title = current.title // Titre vide refusé : on remet l'ancien
                            model.setTitle(current, title)
                        }
                    },
                )
                IconButton(onClick = onClose) {
                    Icon(painterResource(R.drawable.lucide_ic_x), contentDescription = "Fermer", modifier = Modifier.size(20.dp))
                }
            }

            Field("Échéance") { DueDateChips(task.dueDate, onChange = { model.setDueDate(task, it) }) }
            Field("Heure") { DueTimeField(task, onChange = { model.setDueTime(task, it) }) }
            Field("Groupe") { GroupPicker(model, task) }
            Field("Urgence") { UrgencyChips(model, task) }
            Field("Tags") { TagsEditor(model, task) }

            Field("Notes") {
                OutlinedTextField(
                    value = notes,
                    onValueChange = { notes = it },
                    placeholder = { Text("Ajouter une note…") },
                    minLines = 3,
                    keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences),
                    modifier = Modifier.fillMaxWidth(),
                )
            }

            TextButton(onClick = { confirmingDelete = true }) {
                Icon(painterResource(R.drawable.lucide_ic_trash_2), contentDescription = null, tint = Palette.danger, modifier = Modifier.size(16.dp))
                Text("  Supprimer la tâche", color = Palette.danger)
            }
        }
    }

    if (confirmingDelete) {
        AlertDialog(
            onDismissRequest = { confirmingDelete = false },
            title = { Text("Supprimer la tâche ?") },
            text = { Text("« ${task.title} » sera supprimée définitivement.") },
            confirmButton = {
                TextButton(onClick = {
                    confirmingDelete = false
                    model.deleteTask(task)
                }) { Text("Supprimer", color = Palette.danger) }
            },
            dismissButton = {
                TextButton(onClick = { confirmingDelete = false }) { Text("Annuler") }
            },
        )
    }
}

/** Un champ du panneau : petit titre gris au-dessus (comme .pop-label sur le site). */
@Composable
private fun Field(label: String, content: @Composable () -> Unit) {
    Column(Modifier.padding(start = 12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Text(label.uppercase(), fontSize = 12.sp, fontWeight = FontWeight.SemiBold, color = Palette.faint)
        content()
    }
}

/**
 * Aujourd'hui / Demain / Choisir… / Aucune. Une autre date s'affiche à la place de « Choisir… ».
 * Aussi utilisé par la Quick task.
 * @param dueDate la date actuelle ("AAAA-MM-JJ"), ou null
 * @param onChange reçoit "AAAA-MM-JJ", ou null pour enlever l'échéance
 */
@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun DueDateChips(dueDate: String?, onChange: (String?) -> Unit) {
    var picking by rememberSaveable { mutableStateOf(false) }
    val today = DueDates.todayIso()
    val tomorrow = DueDates.tomorrowIso()
    val other = dueDate != null && dueDate != today && dueDate != tomorrow

    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        FilterChip(dueDate == today, onClick = { onChange(today) }, label = { Text("Aujourd'hui") })
        FilterChip(dueDate == tomorrow, onClick = { onChange(tomorrow) }, label = { Text("Demain") })
        FilterChip(
            other,
            onClick = { picking = true },
            label = { Text(if (other) DueDates.format(dueDate) else "Choisir…") },
            leadingIcon = { Icon(painterResource(R.drawable.lucide_ic_calendar), contentDescription = null, modifier = Modifier.size(16.dp)) },
        )
        FilterChip(dueDate == null, onClick = { onChange(null) }, label = { Text("Aucune") })
    }

    if (picking) {
        val state = rememberDatePickerState(initialSelectedDateMillis = dueDate?.let { DueDates.toPickerMillis(it) })
        DatePickerDialog(
            onDismissRequest = { picking = false },
            confirmButton = {
                TextButton(onClick = {
                    state.selectedDateMillis?.let { onChange(DueDates.fromPickerMillis(it)) }
                    picking = false
                }) { Text("OK") }
            },
            dismissButton = { TextButton(onClick = { picking = false }) { Text("Annuler") } },
        ) {
            DatePicker(state)
        }
    }
}

/**
 * Heure facultative, **tapée** (pas de liste d'heures, maquette v3) : "14:30", "1430", "14h30"…
 * (DueDates.parseTime). Enregistrée à ↵, en quittant le champ ou à la fermeture du panneau,
 * **jamais à chaque frappe** : sur le site, la réponse du serveur réécrivait le champ pendant la saisie (bug 7-3).
 * Champ vidé = plus d'heure ; heure incomprise = on remet l'ancienne. Grisé sans échéance.
 */
@Composable
private fun DueTimeField(task: Task, onChange: (String?) -> Unit) {
    var text by rememberSaveable(task.id, task.dueTime) { mutableStateOf(task.dueTime ?: "") }
    var focused by remember { mutableStateOf(false) }
    val focusManager = LocalFocusManager.current // Appelé dans le panneau : c'est bien son focus à lui
    val current by rememberUpdatedState(task)

    fun save() {
        if (text.isBlank()) {
            text = ""
            onChange(null)
            return
        }
        val time = DueDates.parseTime(text)
        if (time == null) {
            text = current.dueTime ?: "" // Pas comprise : on remet l'ancienne
        } else {
            text = time
            onChange(time)
        }
    }

    // Panneau fermé pendant la frappe : on enregistre quand même
    val saveOnClose by rememberUpdatedState({ if (focused) save() })
    DisposableEffect(Unit) {
        onDispose { saveOnClose() }
    }

    OutlinedTextField(
        value = text,
        onValueChange = { text = it },
        enabled = task.dueDate != null,
        placeholder = { Text(if (task.dueDate == null) "Choisir d'abord une échéance" else "HH:MM") },
        singleLine = true,
        // Clavier de chiffres : « 1430 » suffit, parseTime le comprend
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number, imeAction = ImeAction.Done),
        keyboardActions = KeyboardActions(onDone = { focusManager.clearFocus() }), // → onFocusChanged → save
        modifier = Modifier.width(if (task.dueDate == null) 260.dp else 140.dp).onFocusChanged { state ->
            if (focused && !state.isFocused) save()
            focused = state.isFocused
        },
    )
}

/**
 * Menu des groupes de la liste : choisir un autre groupe y envoie la tâche, à la fin.
 * Depuis Aujourd'hui / Planifié, on ne connaît pas les autres groupes : « Liste › Groupe » en lecture seule.
 */
@Composable
private fun GroupPicker(model: TaskStore, task: Task) {
    val groups = model.groupsFor(task)
    if (groups == null) {
        Text(model.placeOf(task), color = Palette.text, fontSize = 15.sp)
        return
    }
    Dropdown(
        options = groups.map { it.id to it.name },
        selectedId = task.groupId,
        onSelect = { model.moveToGroup(task, it) },
    )
}

/** Aucune + les niveaux d'urgence (du moins au plus urgent), avec leur point de couleur. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun UrgencyChips(model: TaskStore, task: Task) {
    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        FilterChip(task.urgencyId == null, onClick = { model.setUrgency(task, null) }, label = { Text("Aucune") })
        model.urgencies.values.sortedBy { it.rank }.forEach { level ->
            val color = ListIcons.color(level.color)
            FilterChip(
                task.urgencyId == level.id,
                onClick = { model.setUrgency(task, level.id) },
                label = { Text(level.name) },
                leadingIcon = { Box(Modifier.size(8.dp).background(color, CircleShape)) },
            )
        }
    }
}

/** Les tags (✕ pour enlever) et un champ pour en ajouter (↵ ajoute, le clavier reste ouvert). */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun TagsEditor(model: TaskStore, task: Task) {
    var input by rememberSaveable(task.id) { mutableStateOf("") }

    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        if (task.tags.isNotEmpty()) {
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                task.tags.forEach { tag ->
                    InputChip(
                        selected = false,
                        onClick = { model.removeTag(task, tag) },
                        label = { Text("#$tag") },
                        trailingIcon = { Icon(painterResource(R.drawable.lucide_ic_x), contentDescription = "Enlever $tag", modifier = Modifier.size(14.dp)) },
                    )
                }
            }
        }
        OutlinedTextField(
            value = input,
            onValueChange = { input = it },
            placeholder = { Text("Ajouter un tag") },
            singleLine = true,
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
            keyboardActions = KeyboardActions(onDone = {
                model.addTag(task, input)
                input = ""
            }),
            modifier = Modifier.fillMaxWidth(),
        )
    }
}
