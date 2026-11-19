package io.github.acssio.bettertodo.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import com.composables.icons.lucide.R // Icônes de la bibliothèque icons-lucide-android
import io.github.acssio.bettertodo.api.User
import io.github.acssio.bettertodo.ui.theme.Palette

/**
 * « Choisis ton mot de passe » (comme ChangePasswordPage du site) : affiché avant l'accueil
 * quand l'admin a créé le compte ou changé le mot de passe (must_change_password).
 * Les champs sont gardés ici (rememberSaveable) ; l'envoi est fait par AppViewModel.changeInitialPassword.
 */
@Composable
fun ChangePasswordScreen(
    user: User,
    busy: Boolean,
    error: String?,
    onSubmit: (current: String, new: String, confirm: String) -> Unit,
    onLogout: () -> Unit,
) {
    var current by rememberSaveable { mutableStateOf("") }
    var new by rememberSaveable { mutableStateOf("") }
    var confirm by rememberSaveable { mutableStateOf("") }
    val submit = { onSubmit(current, new, confirm) }

    Box(
        Modifier.fillMaxSize().safeDrawingPadding().imePadding().verticalScroll(rememberScrollState()),
        contentAlignment = Alignment.Center,
    ) {
        Column(
            Modifier.widthIn(max = 420.dp).fillMaxWidth().padding(24.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Box(Modifier.size(44.dp).background(Palette.primaryBg, RoundedCornerShape(12.dp)), contentAlignment = Alignment.Center) {
                Icon(painterResource(R.drawable.lucide_ic_check), contentDescription = null, tint = Palette.primaryText)
            }
            Text("Choisis ton mot de passe", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.SemiBold)
            Text(
                "Bonjour ${user.displayName} ! Ton mot de passe t'a été donné par l'administrateur. " +
                    "Choisis-en un nouveau (au moins ${PasswordForm.MIN_LENGTH} caractères) pour continuer.",
                color = Palette.muted,
            )

            PasswordField(current, { current = it }, "Mot de passe provisoire", ImeAction.Next)
            PasswordField(new, { new = it }, "Nouveau mot de passe", ImeAction.Next)
            PasswordField(confirm, { confirm = it }, "Confirmer le nouveau mot de passe", ImeAction.Done, onDone = submit)

            error?.let { Text(it, color = MaterialTheme.colorScheme.error) }

            Button(onClick = submit, enabled = !busy, modifier = Modifier.fillMaxWidth().height(48.dp)) {
                if (busy) {
                    CircularProgressIndicator(Modifier.size(20.dp), strokeWidth = 2.dp, color = Color.White)
                } else {
                    Text("Enregistrer le mot de passe")
                }
            }
            TextButton(onClick = onLogout, modifier = Modifier.align(Alignment.CenterHorizontally)) {
                Text("Se déconnecter")
            }
        }
    }
}

/**
 * Champ de mot de passe, avec l'œil pour l'afficher. Aussi utilisé par les Paramètres.
 * @param onDone appelé sur ↵ quand imeAction vaut Done
 */
@Composable
fun PasswordField(
    value: String,
    onValueChange: (String) -> Unit,
    label: String,
    imeAction: ImeAction,
    onDone: () -> Unit = {},
) {
    var visible by rememberSaveable { mutableStateOf(false) }
    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        label = { Text(label) },
        singleLine = true,
        visualTransformation = if (visible) VisualTransformation.None else PasswordVisualTransformation(),
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password, imeAction = imeAction),
        keyboardActions = KeyboardActions(onDone = { onDone() }),
        trailingIcon = {
            // Pas de focus sur l'œil : sinon « Suivant » du clavier s'arrête dessus au lieu d'aller au champ suivant (bug testé)
            IconButton(onClick = { visible = !visible }, modifier = Modifier.focusProperties { canFocus = false }) {
                Icon(
                    painterResource(if (visible) R.drawable.lucide_ic_eye_off else R.drawable.lucide_ic_eye),
                    contentDescription = if (visible) "Masquer le mot de passe" else "Afficher le mot de passe",
                    modifier = Modifier.size(20.dp),
                )
            }
        },
        modifier = Modifier.fillMaxWidth(),
    )
}
