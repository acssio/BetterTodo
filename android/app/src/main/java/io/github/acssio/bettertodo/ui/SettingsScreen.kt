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
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
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
import io.github.acssio.bettertodo.api.Tag
import io.github.acssio.bettertodo.api.TodoApi
import io.github.acssio.bettertodo.api.TodoList
import io.github.acssio.bettertodo.api.UrgencyLevel
import io.github.acssio.bettertodo.api.User
import io.github.acssio.bettertodo.data.AppearanceStore
import io.github.acssio.bettertodo.ui.theme.Palette
import io.github.acssio.bettertodo.ui.theme.ThemeMode
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch

/**
 * Données de l'écran Paramètres : le compte et le mot de passe (6-7a), les niveaux d'urgence et les tags (6-7b).
 * Comme BoardModel, ce n'est pas un ViewModel : HomeViewModel en crée un à chaque ouverture.
 *
 * Sans serveur (8-3) : pas de mot de passe ni d'email, mais une partie « Serveur » pour en ajouter un.
 *
 * @param onUserChanged le compte a changé : AppViewModel le garde (nom affiché ailleurs dans l'app)
 */
class SettingsModel(
    private val api: TodoApi,
    private val server: ApiClient?, // Le serveur (mot de passe), ou null sans serveur
    user: User,
    private val scope: CoroutineScope,
    private val onUnauthorized: () -> Unit,
    private val onUserChanged: (User) -> Unit,
) {
    var user by mutableStateOf(user)
        private set

    /** Vrai avec un serveur : mot de passe et email affichés. */
    val hasServer: Boolean get() = server != null
    var error by mutableStateOf<String?>(null) // Erreur du compte (nom, langue, vue)
        private set

    var passwordBusy by mutableStateOf(false)
        private set
    var passwordError by mutableStateOf<String?>(null)
        private set
    var passwordDone by mutableStateOf(false) // « Mot de passe changé » affiché sous le formulaire
        private set

    // --- Niveaux d'urgence et tags (étape 6-7b) ---
    var levels by mutableStateOf<List<UrgencyLevel>>(emptyList()) // Du moins urgent au plus urgent
        private set
    var tags by mutableStateOf<List<Tag>>(emptyList())
        private set

    /** Boîte ouverte (nom, couleur ou confirmation), null = aucune. */
    var dialog by mutableStateOf<SettingsDialog?>(null)
        private set

    init {
        scope.launch {
            try {
                coroutineScope { // Obligatoire autour des async (étape 6-1)
                    val levelsCall = async { api.urgencyLevels() }
                    val tagsCall = async { api.tags() }
                    levels = levelsCall.await().sortedBy { it.rank }
                    tags = tagsCall.await()
                }
            } catch (e: ApiError) {
                failed(e)
            }
        }
    }

    fun ask(dialog: SettingsDialog) {
        this.dialog = dialog
    }

    fun closeDialog() {
        dialog = null
    }

    /** OK dans une boîte de nom. */
    fun submitName(name: String) {
        when (val current = dialog) {
            SettingsDialog.NewLevel -> addLevel(name)
            is SettingsDialog.RenameLevel -> renameLevel(current.level, name)
            SettingsDialog.NewTag -> addTag(name)
            is SettingsDialog.RenameTag -> renameTag(current.tag, name)
            else -> {}
        }
        dialog = null
    }

    /** OK dans une confirmation. */
    fun confirm() {
        when (val current = dialog) {
            is SettingsDialog.DeleteLevel -> deleteLevel(current.level)
            is SettingsDialog.DeleteTag -> deleteTag(current.tag)
            else -> {}
        }
        dialog = null
    }

    /** Nouveau niveau, à la fin, avec la première couleur de la palette pas encore prise (comme le site). */
    private fun addLevel(name: String) {
        scope.launch {
            try {
                levels = levels + api.createUrgencyLevel(name, nextLevelColor(levels.map { it.color }))
            } catch (e: ApiError) {
                failed(e)
            }
        }
    }

    private fun renameLevel(level: UrgencyLevel, name: String) {
        if (name == level.name) return
        changeLevels(levels.map { if (it.id == level.id) it.copy(name = name) else it }) { api.updateUrgencyLevel(level.id, name = name) }
    }

    /** Couleur choisie dans la boîte « Couleur ». */
    fun setLevelColor(level: UrgencyLevel, color: String) {
        dialog = null
        if (color.equals(level.color, ignoreCase = true)) return
        changeLevels(levels.map { if (it.id == level.id) it.copy(color = color) else it }) { api.updateUrgencyLevel(level.id, color = color) }
    }

    /** Flèche ↑ (direction -1, moins urgent) ou ↓ (+1, plus urgent). */
    fun moveLevel(level: UrgencyLevel, direction: Int) {
        val index = levels.indexOfFirst { it.id == level.id }
        val target = index + direction
        if (index < 0 || target !in levels.indices) return
        changeLevels(reorderLevels(levels, index, target)) { api.updateUrgencyLevel(level.id, rank = target) }
    }

    private fun deleteLevel(level: UrgencyLevel) {
        changeLevels(levels.filter { it.id != level.id }) { api.deleteUrgencyLevel(level.id) }
    }

    /** L'écran d'abord, puis l'API ; en cas d'erreur, la liste d'avant et un bandeau. */
    private fun changeLevels(after: List<UrgencyLevel>, call: suspend () -> Unit) {
        val before = levels
        levels = after
        scope.launch {
            try {
                call()
            } catch (e: ApiError) {
                levels = before
                failed(e)
            }
        }
    }

    private fun addTag(name: String) {
        scope.launch {
            try {
                tags = (tags + api.createTag(name)).sortedBy { it.name.lowercase() }
            } catch (e: ApiError) {
                failed(e)
            }
        }
    }

    private fun renameTag(tag: Tag, name: String) {
        if (name == tag.name) return
        changeTags(tags.map { if (it.id == tag.id) it.copy(name = name) else it }) { api.renameTag(tag.id, name) }
    }

    private fun deleteTag(tag: Tag) {
        changeTags(tags.filter { it.id != tag.id }) { api.deleteTag(tag.id) }
    }

    private fun changeTags(after: List<Tag>, call: suspend () -> Unit) {
        val before = tags
        tags = after
        scope.launch {
            try {
                call()
            } catch (e: ApiError) {
                tags = before
                failed(e)
            }
        }
    }

    private fun failed(e: ApiError) {
        if (e.status == 401) onUnauthorized() else error = "Modification non enregistrée : ${e.message}"
    }

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

    /** @param due "none", "today" ou "tomorrow" */
    fun setQuickTaskDue(due: String) {
        if (due != user.quickTaskDue) updateMe(user.copy(quickTaskDue = due)) { api.updateMe(quickTaskDue = due) }
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
                user = (server ?: return@launch).changePassword(current, new)
                passwordDone = true
                onSuccess()
            } catch (e: ApiError) {
                if (e.status == 401) onUnauthorized() else passwordError = e.message
            }
            passwordBusy = false
        }
    }
}

/** Boîte ouverte dans les Paramètres. */
sealed class SettingsDialog {
    data object NewLevel : SettingsDialog()
    data class RenameLevel(val level: UrgencyLevel) : SettingsDialog()
    data class LevelColor(val level: UrgencyLevel) : SettingsDialog()
    data class DeleteLevel(val level: UrgencyLevel) : SettingsDialog()
    data object NewTag : SettingsDialog()
    data class RenameTag(val tag: Tag) : SettingsDialog()
    data class DeleteTag(val tag: Tag) : SettingsDialog()
}

/** Couleur d'un nouveau niveau : la première de la palette pas encore prise, sinon la dernière (comme le site). */
fun nextLevelColor(used: List<String>): String {
    val taken = used.map { it.uppercase() }
    return ListIcons.colors.firstOrNull { it.uppercase() !in taken } ?: ListIcons.colors.last()
}

/** Les niveaux après déplacement de from à to, rangs renumérotés 0, 1, 2… (comme le fera le serveur). */
fun reorderLevels(levels: List<UrgencyLevel>, from: Int, to: Int): List<UrgencyLevel> {
    val moved = levels.toMutableList()
    moved.add(to, moved.removeAt(from))
    return moved.mapIndexed { rank, level -> level.copy(rank = rank) }
}

/** Confirmation de suppression d'un niveau (texte du site). */
fun deleteLevelMessage(level: UrgencyLevel): String =
    "Supprimer le niveau « ${level.name} » ? Les tâches de ce niveau passeront sans urgence."

/** Confirmation de suppression d'un tag, avec le nombre de tâches (texte du site). */
fun deleteTagMessage(tag: Tag): String {
    val usage = if (tag.taskCount > 0) " Il sera enlevé de ${tag.taskCount} tâche${if (tag.taskCount > 1) "s" else ""}." else ""
    return "Supprimer le tag « ${tag.name} » ?$usage"
}

/**
 * Écran Paramètres (comme SettingsDialog du site, en plein écran comme sur le site mobile) :
 * Compte (nom affiché, langue), Mot de passe, Niveaux d'urgence, Tags, Affichage par défaut.
 * Jetons d'accès et console admin : sur le site.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(model: SettingsModel, onBack: () -> Unit, onConnectServer: () -> Unit) {
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

            // --- Apparence (8-1) : par téléphone, pas sur le serveur ---
            SectionTitle("Apparence")
            AppearanceSection()

            HorizontalDivider(color = Palette.border)

            // --- Serveur (8-3) : sans serveur, on peut en ajouter un ---
            if (!model.hasServer) {
                SectionTitle("Serveur")
                Text(
                    "Tes listes sont sur ce téléphone, sans serveur. Avec un serveur BetterTodo, tu les retrouves aussi sur le site.",
                    color = Palette.muted,
                    fontSize = 14.sp,
                )
                OutlinedButton(onClick = onConnectServer) { Text("Se connecter à un serveur") }

                HorizontalDivider(color = Palette.border)
            }

            // --- Compte ---
            SectionTitle("Compte")
            if (model.hasServer) Text(user.email, color = Palette.muted)
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

            // --- Mot de passe (avec un serveur seulement) ---
            if (model.hasServer) {
                SectionTitle("Mot de passe")
                PasswordSection(model)

                HorizontalDivider(color = Palette.border)
            }

            // --- Niveaux d'urgence (6-7b) ---
            SectionTitle("Niveaux d'urgence")
            UrgencyLevelsSection(model)

            HorizontalDivider(color = Palette.border)

            // --- Tags (6-7b) ---
            SectionTitle("Tags")
            TagsSection(model)

            HorizontalDivider(color = Palette.border)

            // --- Affichage par défaut ---
            SectionTitle("Affichage par défaut")
            Text("La vue des nouvelles listes.", color = Palette.muted, fontSize = 14.sp)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                FilterChip(user.defaultView == TodoList.VIEW_COLUMNS, onClick = { model.setDefaultView(TodoList.VIEW_COLUMNS) }, label = { Text("Colonnes") })
                FilterChip(user.defaultView == TodoList.VIEW_SECTIONS, onClick = { model.setDefaultView(TodoList.VIEW_SECTIONS) }, label = { Text("Sections") })
            }

            HorizontalDivider(color = Palette.border)

            // --- Quick task (8-2) : réglage du compte, comme sur le site ---
            SectionTitle("Quick task")
            Text("Échéance proposée quand on ouvre la Quick task.", color = Palette.muted, fontSize = 14.sp)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                FilterChip(user.quickTaskDue == User.QUICK_DUE_NONE, onClick = { model.setQuickTaskDue(User.QUICK_DUE_NONE) }, label = { Text("Aucune") })
                FilterChip(user.quickTaskDue == User.QUICK_DUE_TODAY, onClick = { model.setQuickTaskDue(User.QUICK_DUE_TODAY) }, label = { Text("Aujourd'hui") })
                FilterChip(user.quickTaskDue == User.QUICK_DUE_TOMORROW, onClick = { model.setQuickTaskDue(User.QUICK_DUE_TOMORROW) }, label = { Text("Demain") })
            }
            Box(Modifier.height(24.dp))
        }
    }

    SettingsDialogs(model) // Boîtes de nom, de couleur et confirmations (6-7b)
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

/**
 * Système / Clair / Sombre. Retenu par téléphone dans AppearanceStore (comme bettertodo.theme sur le site) :
 * MainActivity suit ce réglage et redessine toute l'app dès qu'il change.
 */
@Composable
private fun AppearanceSection() {
    val context = LocalContext.current
    val store = remember { AppearanceStore(context) }
    val scope = rememberCoroutineScope()
    val mode by store.mode.collectAsState(initial = ThemeMode.SYSTEM)

    Text("Système : suit le mode clair ou sombre du téléphone.", color = Palette.muted, fontSize = 14.sp)
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        ThemeMode.entries.forEach { choice ->
            FilterChip(mode == choice, onClick = { scope.launch { store.save(choice) } }, label = { Text(choice.label) })
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
