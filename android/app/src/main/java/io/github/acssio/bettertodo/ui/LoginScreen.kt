package io.github.acssio.bettertodo.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import com.composables.icons.lucide.R // Icônes de la bibliothèque icons-lucide-android
import io.github.acssio.bettertodo.ui.theme.Palette

/**
 * Écran de connexion : adresse du serveur (chacun héberge le sien), email, mot de passe.
 * Les champs sont gardés ici (rememberSaveable : ils survivent à la rotation) ;
 * l'appel à l'API est fait par AppViewModel.login.
 *
 * @param message information affichée au-dessus du formulaire (ex. session expirée), ou null
 * @param error erreur de la dernière tentative, affichée sous le formulaire, ou null
 * @param onUseLocal « Utiliser l'app sans serveur » : les données restent sur le téléphone (8-3)
 */
@Composable
fun LoginScreen(
    serverUrl: String,
    message: String?,
    busy: Boolean,
    error: String?,
    onLogin: (server: String, email: String, password: String) -> Unit,
    onUseLocal: () -> Unit,
) {
    var server by rememberSaveable { mutableStateOf(serverUrl) }
    var email by rememberSaveable { mutableStateOf("") }
    var password by rememberSaveable { mutableStateOf("") }
    var showPassword by rememberSaveable { mutableStateOf(false) }

    val onSubmit = { onLogin(server, email, password) }

    Box(
        Modifier.fillMaxSize().safeDrawingPadding().imePadding().verticalScroll(rememberScrollState()),
        contentAlignment = Alignment.Center,
    ) {
        Column(
            Modifier.widthIn(max = 420.dp).fillMaxWidth().padding(24.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            // Logo : coche blanche sur carré foncé, comme le site
            Box(
                Modifier.size(44.dp).background(Palette.primaryBg, RoundedCornerShape(12.dp)),
                contentAlignment = Alignment.Center,
            ) {
                Icon(painterResource(R.drawable.lucide_ic_check), contentDescription = null, tint = Palette.primaryText)
            }
            Text("BetterTodo", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.SemiBold)
            Text("Connecte-toi à ton serveur", color = Palette.muted)

            if (message != null) {
                Text(message, color = Palette.accent)
            }
            Spacer(Modifier.height(4.dp))

            OutlinedTextField(
                value = server,
                onValueChange = { server = it },
                label = { Text("Adresse du serveur") },
                placeholder = { Text("https://todo.exemple.fr") },
                singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri, imeAction = ImeAction.Next, autoCorrectEnabled = false),
                modifier = Modifier.fillMaxWidth(),
            )
            OutlinedTextField(
                value = email,
                onValueChange = { email = it },
                label = { Text("Email") },
                singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email, imeAction = ImeAction.Next, autoCorrectEnabled = false),
                modifier = Modifier.fillMaxWidth(),
            )
            OutlinedTextField(
                value = password,
                onValueChange = { password = it },
                label = { Text("Mot de passe") },
                singleLine = true,
                visualTransformation = if (showPassword) VisualTransformation.None else PasswordVisualTransformation(),
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password, imeAction = ImeAction.Go),
                keyboardActions = KeyboardActions(onGo = { onSubmit() }),
                trailingIcon = {
                    IconButton(onClick = { showPassword = !showPassword }) {
                        Icon(
                            painterResource(if (showPassword) R.drawable.lucide_ic_eye_off else R.drawable.lucide_ic_eye),
                            contentDescription = if (showPassword) "Masquer le mot de passe" else "Afficher le mot de passe",
                            modifier = Modifier.size(20.dp),
                        )
                    }
                },
                modifier = Modifier.fillMaxWidth(),
            )

            if (error != null) {
                Text(error, color = MaterialTheme.colorScheme.error)
            }

            Button(onClick = onSubmit, enabled = !busy, modifier = Modifier.fillMaxWidth().height(48.dp)) {
                if (busy) {
                    CircularProgressIndicator(Modifier.size(20.dp), strokeWidth = 2.dp, color = Color.White)
                } else {
                    Text("Se connecter")
                }
            }
            TextButton(onClick = onUseLocal, modifier = Modifier.fillMaxWidth()) {
                Text("Utiliser l'app sans serveur")
            }
        }
    }
}
