package io.github.acssio.bettertodo.ui

import androidx.activity.compose.BackHandler
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
 */
sealed class HomePage {
    data object Home : HomePage()
    data class OpenList(val board: BoardModel) : HomePage()
    data class OpenSmart(val smart: SmartModel) : HomePage()
}

/**
 * Données de l'accueil : compteurs d'Aujourd'hui / Planifié, groupes de listes et listes,
 * la page affichée et la Quick task.
 * Seuls AppViewModel et HomeViewModel (avec ses BoardModel, SmartModel et QuickTaskModel)
 * parlent à l'API, comme App et Workspace sur le site.
 */
class HomeViewModel(private val api: ApiClient, private val quickTaskMemory: QuickTaskMemory) : ViewModel() {
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

    /** Panneau Quick task ouvert (null = fermé). */
    var quickTask by mutableStateOf<QuickTaskModel?>(null)
        private set

    /** Message court affiché en bas (Snackbar), ex. après une Quick task. */
    var message by mutableStateOf<String?>(null)
        private set

    /** Groupes de listes repliés. Pour l'instant seulement sur ce téléphone (pas envoyé au serveur). */
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

    /** Tirer vers le bas. */
    fun refresh() {
        refreshing = true
        fetch()
    }

    fun toggleFolder(folderId: String) {
        collapsedIds = if (folderId in collapsedIds) collapsedIds - folderId else collapsedIds + folderId
    }

    /** Toucher une liste : un nouveau BoardModel, donc la liste est toujours rechargée. */
    fun openList(list: TodoList) {
        page = HomePage.OpenList(BoardModel(api, list, viewModelScope, onUnauthorized = { sessionExpired = true }))
    }

    /** Toucher « Aujourd'hui » ou « Planifié ». */
    fun openSmart(kind: SmartKind) {
        page = HomePage.OpenSmart(SmartModel(api, kind, viewModelScope, onUnauthorized = { sessionExpired = true }))
    }

    /** Les listes dans l'ordre de l'accueil : hors groupe, puis groupe de listes par groupe de listes. */
    val orderedLists: List<TodoList>
        get() = lists.filter { it.folderId == null } + folders.flatMap { folder -> lists.filter { it.folderId == folder.id } }

    fun openQuickTask() {
        quickTask = QuickTaskModel(
            api,
            orderedLists,
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

    private fun fetch() {
        viewModelScope.launch {
            try {
                // Les trois requêtes partent en même temps. coroutineScope est obligatoire :
                // sans lui, une erreur dans un async remonte à launch sans passer par ce try (plantage)
                val newFolders = coroutineScope {
                    val foldersCall = async { api.folders() }
                    val listsCall = async { api.lists() }
                    val countsCall = async { api.dueCounts(DueDates.todayIso()) } // Date du jour du téléphone
                    lists = listsCall.await().sortedBy { it.position }
                    dueCounts = countsCall.await()
                    foldersCall.await().sortedBy { it.position }
                }
                if (state != LoadState.Ready) {
                    collapsedIds = newFolders.filter { it.collapsed }.map { it.id }.toSet() // Comme sur le site
                }
                folders = newFolders
                error = null
                state = LoadState.Ready
            } catch (e: ApiError) {
                if (e.status == 401) {
                    sessionExpired = true
                } else if (state == LoadState.Ready) {
                    error = e.message // Rafraîchissement raté : on garde les listes affichées
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
 * @param sessionKey le jeton : un nouveau HomeViewModel est créé à chaque connexion
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(
    api: ApiClient,
    user: User,
    sessionKey: String,
    onLogout: () -> Unit,
    onSessionExpired: () -> Unit,
) {
    val context = LocalContext.current
    val model: HomeViewModel = viewModel(key = sessionKey) { HomeViewModel(api, QuickTaskMemory(context)) }
    val snackbar = remember { SnackbarHostState() }

    LaunchedEffect(model.sessionExpired) {
        if (model.sessionExpired) {
            onSessionExpired()
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
            ListScreen(page.board, onBack = model::closeList)
            return
        }
        is HomePage.OpenSmart -> {
            BackHandler(onBack = model::closeList)
            SmartScreen(page.smart, onBack = model::closeList)
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
                        Modifier.padding(start = 16.dp, end = 4.dp).size(28.dp).background(Palette.text, RoundedCornerShape(8.dp)),
                        contentAlignment = Alignment.Center,
                    ) {
                        Icon(painterResource(R.drawable.lucide_ic_check), contentDescription = null, tint = Color.White, modifier = Modifier.size(16.dp))
                    }
                },
                actions = {
                    IconButton(onClick = onLogout) {
                        Icon(painterResource(R.drawable.lucide_ic_log_out), contentDescription = "Se déconnecter (${user.displayName})", modifier = Modifier.size(20.dp))
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
                    onClick = model::openQuickTask,
                    icon = { Icon(painterResource(R.drawable.lucide_ic_plus), contentDescription = null, modifier = Modifier.size(20.dp)) },
                    text = { Text("Quick task", fontWeight = FontWeight.SemiBold) },
                    containerColor = Palette.text,
                    contentColor = Color.White,
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
}

/** Le contenu défilant de l'accueil. */
@Composable
private fun HomeLists(model: HomeViewModel, user: User) {
    // En bas : de la place pour que le bouton Quick task ne cache pas la dernière liste
    LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(start = 12.dp, end = 12.dp, top = 8.dp, bottom = 88.dp)) {
        if (user.mustChangePassword) {
            item { Notice("Ton mot de passe a été donné par l'administrateur : change-le depuis le site (Paramètres › Mot de passe).") }
        }
        if (model.error != null) {
            item { Notice(model.error ?: "") }
        }

        item {
            Row(Modifier.fillMaxWidth().padding(vertical = 4.dp), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                SmartTile(SmartKind.TODAY, model.dueCounts.today, Modifier.weight(1f), onClick = { model.openSmart(SmartKind.TODAY) })
                SmartTile(SmartKind.SCHEDULED, model.dueCounts.scheduled, Modifier.weight(1f), onClick = { model.openSmart(SmartKind.SCHEDULED) })
            }
        }

        item { SectionLabel("Listes") }

        val ungrouped = model.lists.filter { it.folderId == null }
        items(ungrouped, key = { it.id }) { list -> ListRow(list, model::openList) }

        for (folder in model.folders) {
            folderItems(folder, model.lists.filter { it.folderId == folder.id }, folder.id in model.collapsedIds, model::toggleFolder, model::openList)
        }

        if (model.lists.isEmpty()) {
            item {
                Text(
                    "Aucune liste : crée-en une depuis le site.",
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
    onToggle: (String) -> Unit,
    onOpen: (TodoList) -> Unit,
) {
    item(key = "folder-" + folder.id) {
        Row(
            Modifier
                .fillMaxWidth()
                .heightIn(min = 44.dp)
                .clickable { onToggle(folder.id) }
                .padding(horizontal = 12.dp),
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
        }
    }
    if (!collapsed) {
        items(lists, key = { it.id }) { list -> ListRow(list, onOpen) }
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

/** Tuile « Aujourd'hui » ou « Planifié » : icône, compteur, nom (comme les tuiles de MobileHome). */
@Composable
private fun SmartTile(kind: SmartKind, count: Int, modifier: Modifier, onClick: () -> Unit) {
    val shape = RoundedCornerShape(12.dp)
    Column(
        modifier
            .clip(shape)
            .background(Palette.surface)
            .border(1.dp, Palette.border, shape)
            .clickable(onClick = onClick)
            .padding(12.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            SmartIconBox(kind)
            Text(count.toString(), fontSize = 22.sp, fontWeight = FontWeight.SemiBold, textAlign = TextAlign.End, modifier = Modifier.weight(1f))
        }
        Text(kind.title, fontWeight = FontWeight.SemiBold, color = Palette.muted)
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
