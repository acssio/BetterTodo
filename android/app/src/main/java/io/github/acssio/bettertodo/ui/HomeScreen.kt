package io.github.acssio.bettertodo.ui

import androidx.activity.compose.BackHandler
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.TextButton
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.foundation.border
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.FabPosition
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import io.github.acssio.bettertodo.api.DueCounts
import io.github.acssio.bettertodo.data.QuickTaskMemory
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import com.composables.icons.lucide.R // Icônes de la bibliothèque icons-lucide-android
import io.github.acssio.bettertodo.api.ApiClient
import io.github.acssio.bettertodo.api.SmartList
import io.github.acssio.bettertodo.api.TodoApi
import io.github.acssio.bettertodo.sync.SyncEngine
import io.github.acssio.bettertodo.api.ApiError
import io.github.acssio.bettertodo.api.Folder
import io.github.acssio.bettertodo.api.TodoList
import io.github.acssio.bettertodo.api.User
import io.github.acssio.bettertodo.ui.theme.Palette
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch

/** Chargement des données d'un écran (accueil, liste). */
enum class LoadState { Loading, Ready, Failed }

/**
 * Page affichée une fois connecté (comme mobileScreen sur le site) :
 *
 *   Home ──(toucher une liste)───────────→ OpenList  ──(‹ ou bouton retour)──→ Home (rechargé)
 *        ──(toucher Aujourd'hui/Planifié)→ OpenSmart ──(‹ ou bouton retour)──→ Home (rechargé)
 *        ──(engrenage)───────────────────→ Settings  ──(‹ ou bouton retour)──→ Home (rechargé)
 */
sealed class HomePage {
    data object Home : HomePage()
    data class OpenList(val board: BoardModel) : HomePage()
    data class OpenSmart(val smart: SmartModel) : HomePage()
    data class Settings(val settings: SettingsModel) : HomePage()
}

/** Boîte de nom ouverte sur l'accueil. */
sealed class HomeDialog {
    /** @param folderId le groupe de listes où créer la liste, ou null pour hors groupe */
    data class NewList(val folderId: String?) : HomeDialog()
    data object NewFolder : HomeDialog()
    data class RenameFolder(val folder: Folder) : HomeDialog()
}

/** Les listes après la suppression du groupe de listes folderId : les siennes passent hors groupe. */
fun withoutFolder(lists: List<TodoList>, folderId: String): List<TodoList> {
    return lists.map { if (it.folderId == folderId) it.copy(folderId = null) else it }
}

/** Texte de la confirmation de suppression d'un groupe de listes. */
fun deleteFolderMessage(folder: Folder): String {
    return "« ${folder.name} » sera supprimé. Ses listes sont gardées, hors groupe."
}

/**
 * Données de l'accueil : compteurs d'Aujourd'hui / Planifié, groupes de listes et listes,
 * la page affichée et la Quick task.
 * Seuls AppViewModel et HomeViewModel (avec ses BoardModel, SmartModel et QuickTaskModel)
 * parlent à l'API, comme App et Workspace sur le site.
 */
class HomeViewModel(
    private val api: TodoApi,
    private val quickTaskMemory: QuickTaskMemory,
    private val sync: SyncEngine? = null, // null sans serveur
) : ViewModel() {
    var page by mutableStateOf<HomePage>(HomePage.Home)
        private set

    var state by mutableStateOf(LoadState.Loading)
        private set
    var refreshing by mutableStateOf(false) // Tiré vers le bas (les listes restent affichées)
        private set
    var error by mutableStateOf<String?>(null)
        private set
    var sessionExpired by mutableStateOf(false) // 401 reçu : HomeScreen prévient AppViewModel
        private set

    var folders by mutableStateOf<List<Folder>>(emptyList())
        private set
    var lists by mutableStateOf<List<TodoList>>(emptyList())
        private set
    var dueCounts by mutableStateOf(DueCounts())
        private set
    var smartLists by mutableStateOf<List<SmartList>>(emptyList()) // Listes intelligentes perso (8-6)
        private set

    /** Panneau Quick task ouvert (null = fermé). */
    var quickTask by mutableStateOf<QuickTaskModel?>(null)
        private set

    /** Boîte de nom ouverte (null = fermée). */
    var nameDialog by mutableStateOf<HomeDialog?>(null)
        private set

    /** Groupe de listes dont la suppression attend une confirmation (null = aucun). */
    var folderToDelete by mutableStateOf<Folder?>(null)
        private set

    /** Message court affiché en bas (Snackbar), ex. après une Quick task. */
    var message by mutableStateOf<String?>(null)
        private set

    /** Groupes de listes repliés (retenu sur le serveur, comme sur le site). */
    var collapsedIds by mutableStateOf<Set<String>>(emptySet())
        private set

    init {
        load()
    }

    /** Premier chargement, ou « Réessayer » après une erreur. */
    fun load() {
        state = LoadState.Loading
        fetch()
    }

    /** Tirer vers le bas : synchronise d'abord avec le serveur s'il y en a un. */
    fun refresh() {
        refreshing = true
        viewModelScope.launch {
            sync?.syncNow()
            fetch()
        }
    }

    /** Après une synchronisation qui a changé la base : on relit, sans indicateur de chargement. */
    fun reloadAfterSync() {
        fetch()
        when (val current = page) {
            is HomePage.OpenList -> current.board.reloadQuietly()
            is HomePage.OpenSmart -> current.smart.reloadQuietly()
            else -> {}
        }
    }

    /** Tirer vers le bas dans une liste : synchronise d'abord, comme sur l'accueil. */
    private fun syncFirst(store: TaskStore): TaskStore {
        sync?.let { engine -> store.beforeRefresh = { engine.syncNow() } }
        return store
    }

    /** Replier / déplier : l'écran d'abord, puis le serveur (retenu comme sur le site). */
    fun toggleFolder(folderId: String) {
        val collapsed = folderId !in collapsedIds
        collapsedIds = if (collapsed) collapsedIds + folderId else collapsedIds - folderId
        viewModelScope.launch {
            try {
                api.setFolderCollapsed(folderId, collapsed)
            } catch (e: ApiError) {
                failed(e)
            }
        }
    }

    // --- Listes et groupes de listes (étape 6-6a) -------------------------------
    //
    //   aucune boîte ──(+ › Nouvelle liste / … › Nouvelle liste ici)──→ NewList ──(OK)──→ liste créée et ouverte
    //               ──(+ › Nouveau groupe de listes)─────────────────→ NewFolder ──(OK)──→ ajouté à la fin
    //               ──(… › Renommer)─────────────────────────────────→ RenameFolder ──(OK)──→ renommé
    //               ──(… › Supprimer)──→ confirmation ──(Supprimer)──→ supprimé, ses listes hors groupe

    fun askNewList(folderId: String?) {
        nameDialog = HomeDialog.NewList(folderId)
    }

    fun askNewFolder() {
        nameDialog = HomeDialog.NewFolder
    }

    fun askRenameFolder(folder: Folder) {
        nameDialog = HomeDialog.RenameFolder(folder)
    }

    fun closeDialog() {
        nameDialog = null
    }

    /** OK dans la boîte de nom. */
    fun submitName(name: String) {
        when (val dialog = nameDialog ?: return) {
            is HomeDialog.NewList -> createList(name, dialog.folderId)
            HomeDialog.NewFolder -> createFolder(name)
            is HomeDialog.RenameFolder -> renameFolder(dialog.folder, name)
        }
        nameDialog = null
    }

    /** La nouvelle liste s'ouvre tout de suite (on veut en général y ajouter des tâches). */
    private fun createList(name: String, folderId: String?) {
        viewModelScope.launch {
            try {
                val created = api.createList(name, folderId)
                lists = lists + created
                openList(created)
            } catch (e: ApiError) {
                failed(e)
            }
        }
    }

    private fun createFolder(name: String) {
        viewModelScope.launch {
            try {
                folders = folders + api.createFolder(name)
            } catch (e: ApiError) {
                failed(e)
            }
        }
    }

    private fun renameFolder(folder: Folder, name: String) {
        if (name == folder.name) return
        folders = folders.map { if (it.id == folder.id) it.copy(name = name) else it }
        viewModelScope.launch {
            try {
                api.renameFolder(folder.id, name)
            } catch (e: ApiError) {
                failed(e)
            }
        }
    }

    fun askDeleteFolder(folder: Folder) {
        folderToDelete = folder
    }

    fun cancelDeleteFolder() {
        folderToDelete = null
    }

    /** Après confirmation : l'écran d'abord (ses listes passent hors groupe), puis le serveur. */
    fun confirmDeleteFolder() {
        val folder = folderToDelete ?: return
        folderToDelete = null
        folders = folders.filter { it.id != folder.id }
        lists = withoutFolder(lists, folder.id)
        viewModelScope.launch {
            try {
                api.deleteFolder(folder.id)
            } catch (e: ApiError) {
                failed(e)
            }
        }
    }

    /** Une modification de l'accueil a échoué : 401 → session expirée ; sinon bandeau et rechargement. */
    private fun failed(e: ApiError) {
        if (e.status == 401) {
            sessionExpired = true
            return
        }
        fetch(message = "Modification non enregistrée : ${e.message}")
    }

    /** Toucher une liste : un nouveau BoardModel, donc la liste est toujours rechargée. */
    fun openList(list: TodoList) {
        val board = BoardModel(api, list, viewModelScope, onUnauthorized = { sessionExpired = true }, onDeleted = { closeList() })
        page = HomePage.OpenList(board)
        syncFirst(board)
    }

    /** Engrenage de l'en-tête : les Paramètres. */
    fun openSettings(server: ApiClient?, user: User, onUserChanged: (User) -> Unit) {
        page = HomePage.Settings(SettingsModel(api, server, sync, user, viewModelScope, onUnauthorized = { sessionExpired = true }, onUserChanged))
    }

    /** Toucher « Aujourd'hui » ou « Planifié ». */
    fun openSmart(kind: SmartKind) {
        val smart = SmartModel(api, kind, custom = null, viewModelScope, onUnauthorized = { sessionExpired = true })
        page = HomePage.OpenSmart(smart)
        syncFirst(smart)
    }

    /** Toucher une liste intelligente perso (8-6). */
    fun openCustomSmart(list: SmartList) {
        val smart = SmartModel(api, kind = null, custom = list, viewModelScope, onUnauthorized = { sessionExpired = true })
        page = HomePage.OpenSmart(smart)
        syncFirst(smart)
    }

    /** Les listes dans l'ordre de l'accueil : hors groupe, puis groupe de listes par groupe de listes. */
    val orderedLists: List<TodoList>
        get() = lists.filter { it.folderId == null } + folders.flatMap { folder -> lists.filter { it.folderId == folder.id } }

    fun openQuickTask(user: User) {
        quickTask = QuickTaskModel(
            api,
            orderedLists,
            user.quickTaskDue,
            quickTaskMemory,
            viewModelScope,
            onUnauthorized = { sessionExpired = true },
            onCreated = { where ->
                quickTask = null
                message = "Tâche ajoutée dans $where"
                fetch() // Compteurs à jour
            },
        )
    }

    fun closeQuickTask() {
        quickTask = null
    }

    fun messageShown() {
        message = null
    }

    /** Retour à l'accueil : rechargé sans indicateur, pour mettre à jour les compteurs. */
    fun closeList() {
        page = HomePage.Home
        fetch()
    }

    /** @param message affiché après le rechargement (ex. modification ratée), ou null */
    private fun fetch(message: String? = null) {
        viewModelScope.launch {
            try {
                // Les trois requêtes partent en même temps. coroutineScope est obligatoire :
                // sans lui, une erreur dans un async remonte à launch sans passer par ce try (plantage)
                val newFolders = coroutineScope {
                    val foldersCall = async { api.folders() }
                    val listsCall = async { api.lists() }
                    val countsCall = async { api.dueCounts(DueDates.todayIso()) } // Date du jour du téléphone
                    val smartCall = async { api.smartLists() }
                    lists = listsCall.await().sortedBy { it.position }
                    dueCounts = countsCall.await()
                    smartLists = smartCall.await()
                    foldersCall.await().sortedBy { it.position }
                }
                collapsedIds = newFolders.filter { it.collapsed }.map { it.id }.toSet() // Retenu sur le serveur, comme sur le site
                folders = newFolders
                error = message
                state = LoadState.Ready
            } catch (e: ApiError) {
                if (e.status == 401) {
                    sessionExpired = true
                } else if (state == LoadState.Ready) {
                    error = message ?: e.message // Rafraîchissement raté : on garde les listes affichées
                } else {
                    error = e.message
                    state = LoadState.Failed
                }
            }
            refreshing = false
        }
    }
}

/**
 * Écran d'accueil : les listes hors groupe, puis chaque groupe de listes (repliable) avec ses listes.
 * Même disposition que l'accueil mobile du site (MobileHome).
 *
 * @param server le serveur (pour le mot de passe), ou null sans serveur : pas de bouton de déconnexion
 * @param sync la synchronisation (sans serveur, elle ne fait rien mais sa version recharge l'écran)
 * @param sessionKey change à chaque connexion : un nouveau HomeViewModel est créé
 * @param onConnectServer Paramètres › Se connecter à un serveur (sans serveur seulement)
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(
    api: TodoApi,
    server: ApiClient?,
    sync: SyncEngine?,
    user: User,
    sessionKey: String,
    onLogout: () -> Unit,
    onConnectServer: () -> Unit,
    onUserChanged: (User) -> Unit,
) {
    val context = LocalContext.current
    val model: HomeViewModel = viewModel(key = sessionKey) { HomeViewModel(api, QuickTaskMemory(context), sync) }
    val snackbar = remember { SnackbarHostState() }

    // Une synchronisation (ou un bouton de notification) a changé la base : l'accueil et l'écran ouvert se rechargent
    val syncVersion = sync?.version ?: 0
    LaunchedEffect(syncVersion) {
        if (syncVersion > 0) model.reloadAfterSync()
    }

    // Notification touchée : on ouvre « Aujourd'hui » (8-7)
    LaunchedEffect(AppLinks.openToday) {
        if (AppLinks.openToday) {
            AppLinks.openToday = false
            model.openSmart(SmartKind.TODAY)
        }
    }

    // Message court en bas de l'écran (Quick task créée)
    LaunchedEffect(model.message) {
        model.message?.let {
            snackbar.showSnackbar(it)
            model.messageShown()
        }
    }

    // Liste ou liste intelligente ouverte : son écran remplace l'accueil, et le bouton retour d'Android y ramène
    when (val page = model.page) {
        is HomePage.OpenList -> {
            BackHandler(onBack = model::closeList)
            ListScreen(page.board, model.orderedLists, model.folders, onBack = model::closeList)
            return
        }
        is HomePage.OpenSmart -> {
            BackHandler(onBack = model::closeList)
            SmartScreen(page.smart, model.orderedLists, onBack = model::closeList)
            return
        }
        is HomePage.Settings -> {
            BackHandler(onBack = model::closeList)
            SettingsScreen(page.settings, onBack = model::closeList, onConnectServer = onConnectServer)
            return
        }
        HomePage.Home -> {}
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("BetterTodo", fontWeight = FontWeight.SemiBold) },
                navigationIcon = {
                    Box(
                        Modifier.padding(start = 16.dp, end = 4.dp).size(28.dp).background(Palette.primaryBg, RoundedCornerShape(8.dp)),
                        contentAlignment = Alignment.Center,
                    ) {
                        Icon(painterResource(R.drawable.lucide_ic_check), contentDescription = null, tint = Palette.primaryText, modifier = Modifier.size(16.dp))
                    }
                },
                actions = {
                    NewMenu(onNewList = { model.askNewList(null) }, onNewFolder = model::askNewFolder)
                    IconButton(onClick = { model.openSettings(server, user, onUserChanged) }) {
                        Icon(painterResource(R.drawable.lucide_ic_settings), contentDescription = "Paramètres", modifier = Modifier.size(20.dp))
                    }
                    if (server != null) { // Sans serveur : rien à déconnecter
                        IconButton(onClick = onLogout) {
                            Icon(painterResource(R.drawable.lucide_ic_log_out), contentDescription = "Se déconnecter (${user.displayName})", modifier = Modifier.size(20.dp))
                        }
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = Palette.background),
            )
        },
        snackbarHost = { SnackbarHost(snackbar) },
        floatingActionButton = {
            // Bouton flottant centré en bas, seulement s'il y a une liste où ajouter
            if (model.state == LoadState.Ready && model.lists.isNotEmpty()) {
                ExtendedFloatingActionButton(
                    onClick = { model.openQuickTask(user) },
                    icon = { Icon(painterResource(R.drawable.lucide_ic_plus), contentDescription = null, modifier = Modifier.size(20.dp)) },
                    text = { Text("Quick task", fontWeight = FontWeight.SemiBold) },
                    containerColor = Palette.primaryBg,
                    contentColor = Palette.primaryText,
                    // Le texte du bouton n'arrivait pas aux services d'accessibilité (vu avec uiautomator) :
                    // on le donne explicitement, pour TalkBack
                    modifier = Modifier.semantics { contentDescription = "Quick task" },
                )
            }
        },
        floatingActionButtonPosition = FabPosition.Center,
    ) { padding ->
        Box(Modifier.fillMaxSize().padding(padding)) {
            when (model.state) {
                LoadState.Loading -> CircularProgressIndicator(Modifier.align(Alignment.Center))
                LoadState.Failed -> FailedMessage(model.error ?: "Erreur", onRetry = model::load)
                LoadState.Ready -> PullToRefreshBox(
                    isRefreshing = model.refreshing,
                    onRefresh = model::refresh,
                    modifier = Modifier.fillMaxSize(),
                ) {
                    HomeLists(model, user)
                }
            }
        }
    }

    model.quickTask?.let { quick ->
        QuickTaskSheet(quick, onClose = model::closeQuickTask)
    }

    // Boîte de nom : nouvelle liste, nouveau groupe de listes, renommer
    when (val dialog = model.nameDialog) {
        is HomeDialog.NewList -> NameDialog("Nouvelle liste", "Nom de la liste", "Créer", onConfirm = model::submitName, onDismiss = model::closeDialog)
        HomeDialog.NewFolder -> NameDialog("Nouveau groupe de listes", "Nom du groupe de listes", "Créer", onConfirm = model::submitName, onDismiss = model::closeDialog)
        is HomeDialog.RenameFolder -> NameDialog("Renommer le groupe de listes", "Nom du groupe de listes", "Renommer", dialog.folder.name, onConfirm = model::submitName, onDismiss = model::closeDialog)
        null -> {}
    }

    model.folderToDelete?.let { folder ->
        AlertDialog(
            onDismissRequest = model::cancelDeleteFolder,
            title = { Text("Supprimer le groupe de listes ?") },
            text = { Text(deleteFolderMessage(folder)) },
            confirmButton = { TextButton(onClick = model::confirmDeleteFolder) { Text("Supprimer", color = Palette.danger) } },
            dismissButton = { TextButton(onClick = model::cancelDeleteFolder) { Text("Annuler") } },
        )
    }
}

/** « + » de l'en-tête : nouvelle liste ou nouveau groupe de listes. */
@Composable
private fun NewMenu(onNewList: () -> Unit, onNewFolder: () -> Unit) {
    var open by rememberSaveable { mutableStateOf(false) }
    Box {
        IconButton(onClick = { open = true }) {
            Icon(painterResource(R.drawable.lucide_ic_plus), contentDescription = "Nouvelle liste ou groupe de listes", modifier = Modifier.size(22.dp))
        }
        DropdownMenu(expanded = open, onDismissRequest = { open = false }) {
            MenuItem("Nouvelle liste", R.drawable.lucide_ic_list_plus) { open = false; onNewList() }
            MenuItem("Nouveau groupe de listes", R.drawable.lucide_ic_folder_plus) { open = false; onNewFolder() }
        }
    }
}

/** Menu « … » d'un groupe de listes. */
@Composable
private fun FolderMenu(folder: Folder, model: HomeViewModel) {
    var open by rememberSaveable { mutableStateOf(false) }
    Box {
        IconButton(onClick = { open = true }, modifier = Modifier.size(36.dp)) {
            Icon(painterResource(R.drawable.lucide_ic_ellipsis), contentDescription = "Options de ${folder.name}", tint = Palette.muted, modifier = Modifier.size(18.dp))
        }
        DropdownMenu(expanded = open, onDismissRequest = { open = false }) {
            MenuItem("Nouvelle liste ici", R.drawable.lucide_ic_list_plus) { open = false; model.askNewList(folder.id) }
            MenuItem("Renommer", R.drawable.lucide_ic_pencil) { open = false; model.askRenameFolder(folder) }
            MenuItem("Supprimer le groupe", R.drawable.lucide_ic_trash_2, danger = true) { open = false; model.askDeleteFolder(folder) }
        }
    }
}

/** Une ligne de menu avec son icône Lucide (en rouge si danger). */
@Composable
fun MenuItem(label: String, icon: Int, danger: Boolean = false, onClick: () -> Unit) {
    val color = if (danger) Palette.danger else Palette.text
    DropdownMenuItem(
        text = { Text(label, color = color) },
        leadingIcon = { Icon(painterResource(icon), contentDescription = null, tint = color, modifier = Modifier.size(18.dp)) },
        onClick = onClick,
    )
}

/** Le contenu défilant de l'accueil. */
@Composable
private fun HomeLists(model: HomeViewModel, user: User) {
    // En bas : de la place pour que le bouton Quick task ne cache pas la dernière liste
    LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(start = 12.dp, end = 12.dp, top = 8.dp, bottom = 88.dp)) {
        if (model.error != null) {
            item { Notice(model.error ?: "") }
        }

        // Tuiles des listes intelligentes, deux par ligne : Aujourd'hui et Planifié (si affichées), puis les perso visibles
        val tiles = buildList {
            if (user.showToday) add(Tile(SmartKind.TODAY.icon, SmartKind.TODAY.color, SmartKind.TODAY.title, model.dueCounts.today) { model.openSmart(SmartKind.TODAY) })
            if (user.showScheduled) {
                add(Tile(SmartKind.SCHEDULED.icon, SmartKind.SCHEDULED.color, SmartKind.SCHEDULED.title, model.dueCounts.scheduled) { model.openSmart(SmartKind.SCHEDULED) })
            }
            model.smartLists.filter { it.visible }.forEach { smart ->
                add(Tile(ListIcons.drawable(smart.icon), smart.color, smart.name, smart.openTasks) { model.openCustomSmart(smart) })
            }
        }
        items(tiles.chunked(2)) { row ->
            Row(Modifier.fillMaxWidth().padding(vertical = 4.dp), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                row.forEach { tile -> SmartTile(tile, Modifier.weight(1f)) }
                if (row.size == 1) Spacer(Modifier.weight(1f)) // Nombre impair : la tuile garde sa largeur
            }
        }

        item { SectionLabel("Listes") }

        val ungrouped = model.lists.filter { it.folderId == null }
        items(ungrouped, key = { it.id }) { list -> ListRow(list, model::openList) }

        for (folder in model.folders) {
            folderItems(folder, model.lists.filter { it.folderId == folder.id }, folder.id in model.collapsedIds, model)
        }

        if (model.lists.isEmpty()) {
            item {
                Text(
                    "Aucune liste : touche + en haut pour en créer une.",
                    color = Palette.muted,
                    modifier = Modifier.padding(12.dp),
                )
            }
        }
    }
}

/** Un groupe de listes : son en-tête (toucher = replier / déplier), puis ses listes s'il est ouvert. */
private fun LazyListScope.folderItems(
    folder: Folder,
    lists: List<TodoList>,
    collapsed: Boolean,
    model: HomeViewModel,
) {
    item(key = "folder-" + folder.id) {
        Row(
            Modifier
                .fillMaxWidth()
                .heightIn(min = 44.dp)
                .clickable { model.toggleFolder(folder.id) }
                .padding(start = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Icon(
                painterResource(if (collapsed) R.drawable.lucide_ic_chevron_right else R.drawable.lucide_ic_chevron_down),
                contentDescription = if (collapsed) "Déplier" else "Replier",
                tint = Palette.muted,
                modifier = Modifier.size(16.dp),
            )
            Text(
                folder.name,
                fontWeight = FontWeight.SemiBold,
                color = Palette.muted,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f),
            )
            if (collapsed) {
                Text(lists.sumOf { it.openTasks }.toString(), color = Palette.faint, fontSize = 13.sp)
            }
            FolderMenu(folder, model)
        }
    }
    if (!collapsed) {
        items(lists, key = { it.id }) { list -> ListRow(list, model::openList) }
    }
}

/** Une liste : icône sur carré teinté, nom, nombre de tâches à faire. Toucher = ouvrir la liste. */
@Composable
private fun ListRow(list: TodoList, onOpen: (TodoList) -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .heightIn(min = 48.dp)
            .clip(RoundedCornerShape(10.dp))
            .clickable { onOpen(list) }
            .padding(horizontal = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        ListIconBox(list)
        Text(list.name, fontSize = 15.sp, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f))
        if (list.openTasks > 0) {
            Text(list.openTasks.toString(), color = Palette.faint, fontSize = 13.sp)
        }
    }
}

/** Une tuile de liste intelligente : icône, couleur, nom, nombre de tâches, et ce que fait le toucher. */
private class Tile(val icon: Int, val color: String, val title: String, val count: Int, val onClick: () -> Unit)

/** Tuile d'une liste intelligente : icône, compteur, nom (comme les tuiles de MobileHome). */
@Composable
private fun SmartTile(tile: Tile, modifier: Modifier) {
    val shape = RoundedCornerShape(12.dp)
    Column(
        modifier
            .clip(shape)
            .background(Palette.surface)
            .border(1.dp, Palette.border, shape)
            .clickable(onClick = tile.onClick)
            .padding(12.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            SmartIconBox(tile.icon, tile.color)
            Text(tile.count.toString(), fontSize = 22.sp, fontWeight = FontWeight.SemiBold, textAlign = TextAlign.End, modifier = Modifier.weight(1f))
        }
        Text(tile.title, fontWeight = FontWeight.SemiBold, color = Palette.muted, maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
}

/** Icône d'une liste sur un carré teinté : la couleur de la liste à 10 % d'opacité, comme ListIcon sur le site. */
@Composable
fun ListIconBox(list: TodoList) {
    val color = ListIcons.color(list.color)
    Box(
        Modifier.size(30.dp).background(color.copy(alpha = 0.1f), RoundedCornerShape(8.dp)),
        contentAlignment = Alignment.Center,
    ) {
        Icon(painterResource(ListIcons.drawable(list.icon)), contentDescription = null, tint = color, modifier = Modifier.size(18.dp))
    }
}

@Composable
private fun SectionLabel(text: String) {
    Text(
        text.uppercase(),
        color = Palette.faint,
        fontSize = 12.sp,
        fontWeight = FontWeight.SemiBold,
        modifier = Modifier.padding(start = 12.dp, top = 8.dp, bottom = 4.dp),
    )
}

/** Bandeau d'information en haut d'un écran (accueil, liste). */
@Composable
fun Notice(text: String) {
    Text(
        text,
        fontSize = 14.sp,
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 6.dp)
            .background(Palette.hover, RoundedCornerShape(10.dp))
            .padding(12.dp),
    )
}

/** Chargement raté : message et « Réessayer » au centre de l'écran. */
@Composable
fun FailedMessage(message: String, onRetry: () -> Unit) {
    Column(
        Modifier.fillMaxSize().padding(32.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp, Alignment.CenterVertically),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(message, textAlign = TextAlign.Center, style = MaterialTheme.typography.bodyLarge)
        Button(onClick = onRetry) { Text("Réessayer") }
    }
}
