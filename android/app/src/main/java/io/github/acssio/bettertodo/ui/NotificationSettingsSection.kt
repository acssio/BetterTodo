package io.github.acssio.bettertodo.ui

import android.Manifest
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.FilterChip
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.LifecycleResumeEffect
import io.github.acssio.bettertodo.notify.NotificationSettings
import io.github.acssio.bettertodo.notify.NotificationStore
import io.github.acssio.bettertodo.notify.Notifier
import io.github.acssio.bettertodo.notify.ReminderScheduler
import io.github.acssio.bettertodo.ui.theme.Palette
import kotlinx.coroutines.launch

/**
 * Paramètres › Notifications (8-7), par téléphone, comme la maquette v3 :
 *  - « Rappels d'échéance » : À l'heure / 15 min avant / 1 h avant ;
 *  - « Tâches sans heure : rappel à 09:00 » (résumé du matin, heure tapée comme celle d'une tâche) ;
 *  - ce qu'Android doit autoriser : les notifications (Android 13+) et, si possible, les alarmes à la minute près.
 * Chaque changement est enregistré tout de suite, puis les rappels sont replanifiés.
 */
@Composable
fun NotificationSettingsSection() {
    val context = LocalContext.current
    val store = remember { NotificationStore(context) }
    val scope = rememberCoroutineScope()
    val settings by store.settings.collectAsState(initial = NotificationSettings())

    // Ce qu'Android autorise : relu à chaque retour sur l'écran (l'utilisateur a pu aller dans les réglages)
    var allowed by remember { mutableStateOf(Notifier.allowed(context)) }
    var exact by remember { mutableStateOf(ReminderScheduler.canBeExact(context)) }
    LifecycleResumeEffect(Unit) {
        allowed = Notifier.allowed(context)
        exact = ReminderScheduler.canBeExact(context)
        onPauseOrDispose {}
    }
    val askPermission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted -> allowed = granted }

    fun save(changed: NotificationSettings) {
        scope.launch {
            store.save(changed)
            ReminderScheduler.reschedule(context)
        }
    }

    /** Demande la permission (Android 13+), ou ouvre les réglages de notification de l'app. */
    fun allow() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            askPermission.launch(Manifest.permission.POST_NOTIFICATIONS)
        } else {
            context.startActivity(Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS).putExtra(Settings.EXTRA_APP_PACKAGE, context.packageName))
        }
    }

    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) {
            Text("Rappels d'échéance", fontSize = 15.sp)
            Text("Sur ce téléphone seulement.", fontSize = 13.sp, color = Palette.muted)
        }
        Switch(
            checked = settings.enabled,
            onCheckedChange = { on ->
                save(settings.copy(enabled = on))
                if (on && !allowed) allow()
            },
        )
    }

    if (settings.enabled) { // Sans rappels, rien d'autre à régler (pas de « return » anticipé en Compose)
        if (!allowed) {
            Text("Les notifications de BetterTodo sont bloquées par Android.", color = Palette.danger, fontSize = 14.sp)
            OutlinedButton(onClick = ::allow) { Text("Autoriser les notifications") }
        }

        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            NotificationSettings.OFFSETS.forEach { (minutes, label) ->
                FilterChip(settings.offsetMinutes == minutes, onClick = { save(settings.copy(offsetMinutes = minutes)) }, label = { Text(label) })
            }
        }

        SummaryTimeRow(settings.summaryTime, onSave = { save(settings.copy(summaryTime = it)) })

        if (!exact && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            Text("Les rappels peuvent arriver avec quelques minutes de retard.", color = Palette.muted, fontSize = 13.sp)
            TextButton(onClick = {
                context.startActivity(Intent(Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM, Uri.parse("package:${context.packageName}")))
            }) { Text("Rappels à la minute près") }
        }
    }
}

/** « Tâches sans heure : rappel à [09:00] » : heure tapée, enregistrée à ↵ ou en quittant le champ (comme TaskSheet). */
@Composable
private fun SummaryTimeRow(time: String, onSave: (String) -> Unit) {
    var text by rememberSaveable(time) { mutableStateOf(time) }
    var focused by remember { mutableStateOf(false) }
    val focusManager = LocalFocusManager.current

    fun save() {
        val parsed = DueDates.parseTime(text)
        if (parsed == null) text = time else {
            text = parsed
            if (parsed != time) onSave(parsed)
        }
    }

    LaunchedEffect(time) { text = time }

    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        Text("Tâches sans heure : rappel à", fontSize = 14.sp, modifier = Modifier.weight(1f))
        OutlinedTextField(
            value = text,
            onValueChange = { text = it },
            singleLine = true,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number, imeAction = ImeAction.Done),
            keyboardActions = KeyboardActions(onDone = { focusManager.clearFocus() }),
            modifier = Modifier.width(110.dp).onFocusChanged { state ->
                if (focused && !state.isFocused) save()
                focused = state.isFocused
            },
        )
    }
}
