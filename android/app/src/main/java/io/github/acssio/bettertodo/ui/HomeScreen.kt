package io.github.acssio.bettertodo.ui

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

/** Chargement des données de l'accueil. */
enum class LoadState { Loading, Ready, Failed }

/**
 * Données de l'accueil : groupes de listes et listes (lecture seule pour l'instant).
 * Seuls AppViewModel et HomeViewModel parlent à l'API, comme App et Workspace sur le site.
 */
class HomeViewModel(private val api: ApiClient) : ViewModel() {
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

    private fun fetch() {
        viewModelScope.launch {
            try {
                // Les deux requêtes partent en même temps. coroutineScope est obligatoire :
                // sans lui, une erreur dans un async remonte à launch sans passer par ce try (plantage)
                val (newFolders, newLists) = coroutineScope {
                    val foldersCall = async { api.folders() }
                    val listsCall = async { api.lists() }
                    foldersCall.await().sortedBy { it.position } to listsCall.await().sortedBy { it.position }
                }
                lists = newLists
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
    val model: HomeViewModel = viewModel(key = sessionKey) { HomeViewModel(api) }

    LaunchedEffect(model.sessionExpired) {
        if (model.sessionExpired) {
            onSessionExpired()
        }
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
}

/** Le contenu défilant de l'accueil. */
@Composable
private fun HomeLists(model: HomeViewModel, user: User) {
    LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(horizontal = 12.dp, vertical = 8.dp)) {
        if (user.mustChangePassword) {
            item { Notice("Ton mot de passe a été donné par l'administrateur : change-le depuis le site (Paramètres › Mot de passe).") }
        }
        if (model.error != null) {
            item { Notice(model.error ?: "") }
        }

        item { SectionLabel("Listes") }

        val ungrouped = model.lists.filter { it.folderId == null }
        items(ungrouped, key = { it.id }) { list -> ListRow(list) }

        for (folder in model.folders) {
            folderItems(folder, model.lists.filter { it.folderId == folder.id }, folder.id in model.collapsedIds, model::toggleFolder)
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
private fun LazyListScope.folderItems(folder: Folder, lists: List<TodoList>, collapsed: Boolean, onToggle: (String) -> Unit) {
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
        items(lists, key = { it.id }) { list -> ListRow(list) }
    }
}

/** Une liste : icône sur carré teinté, nom, nombre de tâches à faire. */
@Composable
private fun ListRow(list: TodoList) {
    val color = ListIcons.color(list.color)
    Row(
        Modifier.fillMaxWidth().heightIn(min = 48.dp).padding(horizontal = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        // Fond = la couleur de la liste à 10 % d'opacité, comme ListIcon sur le site
        Box(
            Modifier.size(30.dp).background(color.copy(alpha = 0.1f), RoundedCornerShape(8.dp)),
            contentAlignment = Alignment.Center,
        ) {
            Icon(painterResource(ListIcons.drawable(list.icon)), contentDescription = null, tint = color, modifier = Modifier.size(18.dp))
        }
        Text(list.name, fontSize = 15.sp, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f))
        if (list.openTasks > 0) {
            Text(list.openTasks.toString(), color = Palette.faint, fontSize = 13.sp)
        }
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

/** Bandeau d'information en haut de la liste. */
@Composable
private fun Notice(text: String) {
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

@Composable
private fun FailedMessage(message: String, onRetry: () -> Unit) {
    Column(
        Modifier.fillMaxSize().padding(32.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp, Alignment.CenterVertically),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(message, textAlign = TextAlign.Center, style = MaterialTheme.typography.bodyLarge)
        Button(onClick = onRetry) { Text("Réessayer") }
    }
}
