package io.github.acssio.bettertodo.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.PrimaryScrollableTabRow
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Tab
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.composables.icons.lucide.R // Icônes de la bibliothèque icons-lucide-android
import io.github.acssio.bettertodo.api.BoardGroup
import io.github.acssio.bettertodo.api.TodoList
import io.github.acssio.bettertodo.ui.theme.Palette
import kotlinx.coroutines.launch

/**
 * Écran d'une liste : en-tête (‹, icône, nom), puis ses groupes selon la vue de la liste :
 *  - colonnes : un groupe à la fois en pleine largeur, on glisse pour changer (onglets en haut) ;
 *  - sections : les groupes empilés.
 * Même rendu que l'écran de liste mobile du site.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ListScreen(model: BoardModel, onBack: () -> Unit) {
    val list = model.currentList

    Scaffold(
        topBar = {
            TopAppBar(
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(painterResource(R.drawable.lucide_ic_chevron_left), contentDescription = "Retour à l'accueil")
                    }
                },
                title = {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        ListIconBox(list)
                        Text(list.name, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = Palette.background),
            )
        },
    ) { padding ->
        // imePadding : la liste s'arrête au-dessus du clavier (sinon le champ « Ajouter » est caché dessous)
        Box(Modifier.fillMaxSize().padding(padding).consumeWindowInsets(padding).imePadding()) {
            when (model.state) {
                LoadState.Loading -> CircularProgressIndicator(Modifier.align(Alignment.Center))
                LoadState.Failed -> FailedMessage(model.error ?: "Erreur", onRetry = model::load)
                LoadState.Ready -> Column(Modifier.fillMaxSize()) {
                    model.error?.let { Box(Modifier.padding(horizontal = 12.dp)) { Notice(it) } }
                    PullToRefreshBox(
                        isRefreshing = model.refreshing,
                        onRefresh = model::refresh,
                        modifier = Modifier.weight(1f),
                    ) {
                        val groups = model.board?.groups ?: emptyList()
                        if (list.defaultView == TodoList.VIEW_SECTIONS) {
                            SectionsView(model, groups)
                        } else {
                            ColumnsView(model, groups)
                        }
                    }
                }
            }
        }
    }

    // Tâche touchée : panneau du bas par-dessus la liste
    model.openedTask?.let { task ->
        TaskSheet(model, task, onClose = model::closeTask)
    }
}

/** Vue colonnes : onglets des groupes, puis un groupe par page (glisser à gauche / à droite). */
@Composable
private fun ColumnsView(model: BoardModel, groups: List<BoardGroup>) {
    if (groups.isEmpty()) {
        EmptyList()
        return
    }

    val pager = rememberPagerState { groups.size }
    val scope = rememberCoroutineScope()

    Column(Modifier.fillMaxSize()) {
        PrimaryScrollableTabRow(
            selectedTabIndex = pager.currentPage.coerceIn(0, groups.size - 1),
            edgePadding = 12.dp,
            containerColor = Palette.background,
            divider = { HorizontalDivider(color = Palette.border) },
        ) {
            groups.forEachIndexed { index, group ->
                Tab(
                    selected = index == pager.currentPage,
                    onClick = { scope.launch { pager.animateScrollToPage(index) } },
                    text = { GroupTitle(group, selected = index == pager.currentPage) },
                )
            }
        }

        HorizontalPager(pager, Modifier.weight(1f), key = { groups[it].id }) { index ->
            val group = groups[index]
            val tasks = model.visibleTasks(group)
            LazyColumn(
                Modifier.fillMaxSize(),
                contentPadding = PaddingValues(12.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                items(tasks, key = { it.id }) { task ->
                    TaskItem(
                        task,
                        urgency = task.urgencyId?.let { model.urgencies[it] },
                        card = true,
                        onToggleDone = { model.toggleDone(task) },
                        onOpen = { model.openTask(task) },
                        modifier = Modifier.animateItem(), // Fondu quand une tâche cochée disparaît
                    )
                }
                item(key = "add-" + group.id) {
                    AddTaskRow(onAdd = { title -> model.createTask(group.id, title) })
                }
            }
        }
    }
}

/** Vue sections : chaque groupe avec son titre, ses tâches en lignes en dessous. */
@Composable
private fun SectionsView(model: BoardModel, groups: List<BoardGroup>) {
    if (groups.isEmpty()) {
        EmptyList()
        return
    }

    LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(vertical = 8.dp)) {
        for (group in groups) {
            sectionItems(model, group)
        }
    }
}

private fun LazyListScope.sectionItems(model: BoardModel, group: BoardGroup) {
    item(key = "group-" + group.id) {
        Box(Modifier.fillMaxWidth().padding(start = 16.dp, end = 16.dp, top = 16.dp, bottom = 6.dp)) {
            GroupTitle(group, selected = true)
        }
    }

    val tasks = model.visibleTasks(group)
    items(tasks, key = { it.id }) { task ->
        Column(Modifier.animateItem()) {
            TaskItem(
                task,
                urgency = task.urgencyId?.let { model.urgencies[it] },
                card = false,
                onToggleDone = { model.toggleDone(task) },
                onOpen = { model.openTask(task) },
            )
            HorizontalDivider(color = Palette.border)
        }
    }
    item(key = "add-" + group.id) {
        AddTaskRow(onAdd = { title -> model.createTask(group.id, title) }, modifier = Modifier.padding(horizontal = 4.dp))
    }
}

/** Nom du groupe et nombre de tâches à faire, comme l'en-tête de colonne du site. */
@Composable
private fun GroupTitle(group: BoardGroup, selected: Boolean) {
    val open = group.tasks.count { !it.done }
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        Text(
            group.name,
            fontWeight = FontWeight.SemiBold,
            fontSize = 14.sp,
            color = if (selected) Palette.text else Palette.muted,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        if (open > 0) {
            Text(
                open.toString(),
                fontSize = 12.sp,
                color = Palette.muted,
                modifier = Modifier.background(Palette.hover, RoundedCornerShape(8.dp)).padding(horizontal = 6.dp),
            )
        }
    }
}

/** Liste sans groupe (rare : le serveur en crée un à la création). Défilable pour que « tirer » marche. */
@Composable
private fun EmptyList() {
    LazyColumn(Modifier.fillMaxSize()) {
        item { Text("Cette liste n'a aucun groupe.", color = Palette.muted, modifier = Modifier.padding(24.dp)) }
    }
}
