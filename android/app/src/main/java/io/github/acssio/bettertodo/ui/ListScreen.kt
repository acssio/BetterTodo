package io.github.acssio.bettertodo.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.platform.LocalHapticFeedback
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
fun ListScreen(model: BoardModel, lists: List<TodoList>, onBack: () -> Unit) {
    val list = model.currentList

    // Pendant une sélection, le bouton retour d'Android l'annule (au lieu de revenir à l'accueil)
    BackHandler(enabled = model.selecting, onBack = model::clearSelection)

    // Glisser au doigt : l'état, et à chaque image le défilement automatique et la cible
    val drag = remember(model) { TaskDragState() }
    val haptic = LocalHapticFeedback.current
    TaskDragLoop(drag, visibleIds = model::visibleIdsByGroup)

    Scaffold(
        topBar = {
            // Pas de « return » anticipé dans un lambda @Composable : l'écran entier restait blanc (bug testé)
            if (model.selecting) {
                SelectionTopBar(model)
            } else TopAppBar(
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
        bottomBar = {
            if (model.selecting) SelectionBottomBar(model, lists)
        },
    ) { padding ->
        // imePadding : la liste s'arrête au-dessus du clavier (sinon le champ « Ajouter » est caché dessous)
        // Le geste du glisser est suivi ici, sur tout l'écran, pas sur la tâche (voir TaskDrag)
        Box(
            Modifier
                .fillMaxSize()
                .padding(padding)
                .consumeWindowInsets(padding)
                .imePadding()
                .taskDragGesture(
                    drag,
                    haptic,
                    onSelect = { taskId -> model.toggleSelection(taskId) },
                    onDragStart = { taskId -> drag.start(model.idsToDrag(taskId), model.titleOf(taskId), drag.pointer) },
                    onDrop = {
                        drag.target?.let { target -> model.dropTasks(drag.draggedIds, target.groupId, target.visibleIndex) }
                        drag.stop()
                    },
                ),
        ) {
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
                            SectionsView(model, groups, drag)
                        } else {
                            ColumnsView(model, groups, drag)
                        }
                    }
                }
            }
            TaskDragOverlay(drag) // Ligne bleue et fantôme, par-dessus la liste
        }
    }

    // Tâche touchée : panneau du bas par-dessus la liste
    model.openedTask?.let { task ->
        TaskSheet(model, task, onClose = model::closeTask)
    }
}

/** Vue colonnes : onglets des groupes, puis un groupe par page (glisser à gauche / à droite). */
@Composable
private fun ColumnsView(model: BoardModel, groups: List<BoardGroup>, drag: TaskDragState) {
    if (groups.isEmpty()) {
        EmptyList()
        return
    }

    val pager = rememberPagerState { groups.size }
    val scope = rememberCoroutineScope()

    // Glisser : doigt gardé 0,5 s contre le bord gauche / droit → groupe voisin
    SideEffect {
        drag.onEdge = { direction ->
            scope.launch { pager.animateScrollToPage((pager.currentPage + direction).coerceIn(0, groups.size - 1)) }
        }
    }

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
            val listState = rememberLazyListState()
            LazyColumn(
                Modifier.fillMaxSize().then(drag.trackScroller("column-" + group.id, listState)),
                state = listState,
                contentPadding = PaddingValues(start = 12.dp, end = 12.dp, bottom = 12.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                // Haut de la colonne : déposer ici = en tête du groupe
                item(key = "head-" + group.id) {
                    Spacer(Modifier.fillMaxWidth().height(4.dp).then(drag.track("head-" + group.id, DropKind.HEAD, group.id)))
                }
                items(tasks, key = { it.id }) { task ->
                    TaskItem(
                        task,
                        urgency = task.urgencyId?.let { model.urgencies[it] },
                        card = true,
                        onToggleDone = { model.toggleDone(task) },
                        onOpen = { model.tap(task) },
                        onLongPress = null, // Géré par le glisser (appui long sans bouger = sélection)
                        selected = task.id in model.selectedIds,
                        modifier = Modifier
                            .animateItem() // Fondu quand une tâche cochée disparaît
                            .then(drag.track("task-" + task.id, DropKind.TASK, group.id, task.id))
                            .alpha(if (task.id in drag.draggedIds) 0.35f else 1f), // Tâches glissées : estompées
                    )
                }
                // Bas de la colonne : déposer ici = à la fin du groupe
                item(key = "add-" + group.id) {
                    Box(drag.track("end-" + group.id, DropKind.END, group.id)) {
                        if (model.selecting) {
                            Spacer(Modifier.fillMaxWidth().height(48.dp)) // Pas d'ajout pendant une sélection
                        } else {
                            AddTaskRow(onAdd = { title -> model.createTask(group.id, title) })
                        }
                    }
                }
            }
        }
    }
}

/** Vue sections : chaque groupe avec son titre, ses tâches en lignes en dessous. */
@Composable
private fun SectionsView(model: BoardModel, groups: List<BoardGroup>, drag: TaskDragState) {
    if (groups.isEmpty()) {
        EmptyList()
        return
    }

    val listState = rememberLazyListState()
    LazyColumn(
        Modifier.fillMaxSize().then(drag.trackScroller("sections", listState)),
        state = listState,
        contentPadding = PaddingValues(vertical = 8.dp),
    ) {
        for (group in groups) {
            sectionItems(model, group, drag)
        }
    }
}

private fun LazyListScope.sectionItems(model: BoardModel, group: BoardGroup, drag: TaskDragState) {
    // Titre du groupe : déposer ici = en tête du groupe
    item(key = "group-" + group.id) {
        Box(
            Modifier
                .fillMaxWidth()
                .then(drag.track("head-" + group.id, DropKind.HEAD, group.id))
                .padding(start = 16.dp, end = 16.dp, top = 16.dp, bottom = 6.dp),
        ) {
            GroupTitle(group, selected = true)
        }
    }

    val tasks = model.visibleTasks(group)
    items(tasks, key = { it.id }) { task ->
        Column(
            Modifier
                .animateItem()
                .then(drag.track("task-" + task.id, DropKind.TASK, group.id, task.id))
                .alpha(if (task.id in drag.draggedIds) 0.35f else 1f), // Tâches glissées : estompées
        ) {
            TaskItem(
                task,
                urgency = task.urgencyId?.let { model.urgencies[it] },
                card = false,
                onToggleDone = { model.toggleDone(task) },
                onOpen = { model.tap(task) },
                onLongPress = null, // Géré par le glisser (appui long sans bouger = sélection)
                selected = task.id in model.selectedIds,
            )
            HorizontalDivider(color = Palette.border)
        }
    }
    // Bas du groupe : déposer ici = à la fin du groupe
    item(key = "add-" + group.id) {
        Box(drag.track("end-" + group.id, DropKind.END, group.id)) {
            if (model.selecting) {
                Spacer(Modifier.fillMaxWidth().height(24.dp)) // Pas d'ajout pendant une sélection
            } else {
                AddTaskRow(onAdd = { title -> model.createTask(group.id, title) }, modifier = Modifier.padding(horizontal = 4.dp))
            }
        }
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
