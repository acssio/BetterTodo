package io.github.acssio.bettertodo.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.fillMaxSize
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
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.composables.icons.lucide.R // Icônes de la bibliothèque icons-lucide-android
import io.github.acssio.bettertodo.api.ApiClient
import io.github.acssio.bettertodo.api.ApiError
import io.github.acssio.bettertodo.api.TodoList
import io.github.acssio.bettertodo.api.User
import io.github.acssio.bettertodo.ui.theme.Palette
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch

/**
 * Données de l'écran Paramètres (étape 6-7a) : le compte et le mot de passe.
 * Comme BoardModel, ce n'est pas un ViewModel : HomeViewModel en crée un à chaque ouverture.
 *
 * @param onUserChanged le compte a changé : AppViewModel le garde (nom affiché ailleurs dans l'app)
 */
class SettingsModel(
    private val api: ApiClient,
    user: User,
    private val scope: CoroutineScope,
    private val onUnauthorized: () -> Unit,
    private val onUserChanged: (User) -> Unit,
) {
    var user by mutableStateOf(user)
        private set
    var error by mutableStateOf<String?>(null) // Erreur du compte (nom, langue, vue)
        private set

    var passwordBusy by mutableStateOf(false)
        private set
    var passwordError by mutableStateOf<String?>(null)
        private set
    var passwordDone by mutableStateOf(false) // « Mot de passe changé » affiché sous le formulaire
        private set

    /** Nom vide ou inchangé : rien n'est envoyé. */
    fun setDisplayName(name: String) {
        val clean = name.trim()
        if (clean.isNotEmpty() && clean != user.displayName) {
            updateMe(user.copy(displayName = clean)) { api.updateMe(displayName = clean) }
        }
    }

    fun setLanguage(language: String) {
        if (language != user.language) updateMe(user.copy(language = language)) { api.updateMe(language = language) }
    }

    fun setDefaultView(view: String) {
        if (view != user.defaultView) updateMe(user.copy(defaultView = view)) { api.updateMe(defaultView = view) }
    }

    /** L'écran d'abord, puis l'API ; en cas d'erreur, le compte d'avant et un message. */
    private fun updateMe(after: User, call: suspend () -> User) {
        val before = user
        user = after
        error = null
        scope.launch {
            try {
                user = call()
                onUserChanged(user)
            } catch (e: ApiError) {
                user = before
                if (e.status == 401) onUnauthorized() else error = "Modification non enregistrée : ${e.message}"
            }
        }
    }

    /**
     * « Changer le mot de passe ». Les autres sessions du compte sont fermées par le serveur.
     * @param onSuccess vide les champs du formulaire
     */
    fun changePassword(current: String, new: String, confirm: String, onSuccess: () -> Unit) {
        passwordDone = false
        PasswordForm.check(current, new, confirm)?.let {
            passwordError = it
            return
        }
        passwordBusy = true
        passwordError = null
        scope.launch {
            try {
                user = api.changePassword(current, new)
                passwordDone = true
                onSuccess()
            } catch (e: ApiError) {
                if (e.status == 401) onUnauthorized() else passwordError = e.message
            }
            passwordBusy = false
        }
    }
}

/**
 * Écran Paramètres (comme SettingsDialog du site, en plein écran comme sur le site mobile) :
 * Compte (nom affiché, langue), Mot de passe, Affichage par défaut.
 * Niveaux d'urgence et tags : étape 6-7b. Jetons d'accès et console admin : sur le site.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(model: SettingsModel, onBack: () -> Unit) {
    val user = model.user
    val focusManager = LocalFocusManager.current
    var name by rememberSaveable { mutableStateOf(user.displayName) }
    val current by rememberUpdatedState(model)
    DisposableEffect(Unit) {
        onDispose { current.setDisplayName(name) } // En quittant l'écran : enregistre le nom en cours
    }

    Scaffold(
        topBar = {
            TopAppBar(
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(painterResource(R.drawable.lucide_ic_chevron_left), contentDescription = "Retour à l'accueil")
                    }
                },
                title = { Text("Paramètres", fontWeight = FontWeight.SemiBold) },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = Palette.background),
            )
        },
    ) { padding ->
        Column(
            Modifier
                .fillMaxSize()
                .padding(padding)
                .consumeWindowInsets(padding)
                .imePadding()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            model.error?.let { Notice(it) }

            // --- Compte ---
            SectionTitle("Compte")
            Text(user.email, color = Palette.muted)
            OutlinedTextField(
                value = name,
                onValueChange = { name = it },
                label = { Text("Nom affiché") },
                singleLine = true,
                keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Words, imeAction = ImeAction.Done),
                keyboardActions = KeyboardActions(onDone = {
                    if (name.isBlank()) name = user.displayName // Nom vide refusé
                    model.setDisplayName(name)
                    focusManager.clearFocus()
                }),
                modifier = Modifier.fillMaxWidth(),
            )
            Label("Langue")
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                FilterChip(user.language == "fr", onClick = { model.setLanguage("fr") }, label = { Text("Français") })
                FilterChip(user.language == "en", onClick = { model.setLanguage("en") }, label = { Text("English") })
            }
            Text("Pour l'instant, la langue ne sert qu'au nom du groupe créé avec chaque nouvelle liste.", color = Palette.faint, fontSize = 13.sp)

            HorizontalDivider(color = Palette.border)

            // --- Mot de passe ---
            SectionTitle("Mot de passe")
            PasswordSection(model)

            HorizontalDivider(color = Palette.border)

            // --- Affichage par défaut ---
            SectionTitle("Affichage par défaut")
            Text("La vue des nouvelles listes.", color = Palette.muted, fontSize = 14.sp)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                FilterChip(user.defaultView == TodoList.VIEW_COLUMNS, onClick = { model.setDefaultView(TodoList.VIEW_COLUMNS) }, label = { Text("Colonnes") })
                FilterChip(user.defaultView == TodoList.VIEW_SECTIONS, onClick = { model.setDefaultView(TodoList.VIEW_SECTIONS) }, label = { Text("Sections") })
            }
            Box(Modifier.height(24.dp))
        }
    }
}

/** Changer son mot de passe : actuel, nouveau, confirmation ; erreurs et confirmation sous le formulaire. */
@Composable
private fun PasswordSection(model: SettingsModel) {
    var current by rememberSaveable { mutableStateOf("") }
    var new by rememberSaveable { mutableStateOf("") }
    var confirm by rememberSaveable { mutableStateOf("") }
    val submit = {
        model.changePassword(current, new, confirm, onSuccess = {
            current = ""
            new = ""
            confirm = ""
        })
    }

    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        PasswordField(current, { current = it }, "Mot de passe actuel", ImeAction.Next)
        PasswordField(new, { new = it }, "Nouveau mot de passe", ImeAction.Next)
        PasswordField(confirm, { confirm = it }, "Confirmer le nouveau mot de passe", ImeAction.Done, onDone = submit)
        model.passwordError?.let { Text(it, color = Palette.danger) }
        if (model.passwordDone) {
            Text("Mot de passe changé. Tes autres appareils devront se reconnecter.", color = Palette.success)
        }
        Button(onClick = submit, enabled = !model.passwordBusy, modifier = Modifier.height(44.dp)) {
            if (model.passwordBusy) {
                CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp, color = Color.White)
            } else {
                Text("Changer le mot de passe")
            }
        }
    }
}

@Composable
private fun SectionTitle(text: String) {
    Text(text, fontSize = 17.sp, fontWeight = FontWeight.SemiBold)
}

@Composable
private fun Label(text: String) {
    Text(text.uppercase(), fontSize = 12.sp, fontWeight = FontWeight.SemiBold, color = Palette.faint)
}
