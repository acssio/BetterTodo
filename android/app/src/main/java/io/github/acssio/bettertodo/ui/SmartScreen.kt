package io.github.acssio.bettertodo.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.composables.icons.lucide.R // Icônes de la bibliothèque icons-lucide-android
import io.github.acssio.bettertodo.api.TodoList
import io.github.acssio.bettertodo.ui.theme.Palette

/**
 * Écran « Aujourd'hui » ou « Planifié » (comme SmartView en mode mobile sur le site) :
 * les tâches à faire de toutes les listes, rangées en sections par date, avec leur liste d'origine.
 * On peut cocher et ouvrir le panneau ; pas d'ajout ni de réordonnancement dans ces vues.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SmartScreen(model: SmartModel, lists: List<TodoList>, onBack: () -> Unit) {
    // Pendant une sélection, le bouton retour d'Android l'annule (au lieu de revenir à l'accueil)
    BackHandler(enabled = model.selecting, onBack = model::clearSelection)

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
                        SmartIconBox(model.icon, model.color)
                        Column {
                            Text(model.title, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                            // Liste perso : son filtre en petit (« urgence Haute ou Moyenne », « #boulot et #réunion »)
                            model.custom?.let {
                                Text(it.summary, fontSize = 12.sp, color = Palette.muted, maxLines = 1, overflow = TextOverflow.Ellipsis)
                            }
                        }
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = Palette.header), // Grise, comme une liste
            )
        },
        bottomBar = {
            if (model.selecting) SelectionBottomBar(model, lists)
        },
    ) { padding ->
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
                        SmartSections(model)
                    }
                }
            }
        }
    }

    // Tâche touchée : même panneau que dans une liste (groupe en lecture seule)
    model.openedTask?.let { task ->
        TaskSheet(model, task, onClose = model::closeTask)
    }
}

@Composable
private fun SmartSections(model: SmartModel) {
    val sections = model.sections

    LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(vertical = 8.dp)) {
        if (sections.isEmpty()) {
            item { Text(model.empty, color = Palette.muted, modifier = Modifier.padding(24.dp)) }
        }
        for (section in sections) {
            item(key = "section-" + section.key) {
                Text(
                    section.title,
                    fontWeight = FontWeight.SemiBold,
                    fontSize = 14.sp,
                    color = when {
                        section.late -> Palette.danger
                        section.color != null -> ListIcons.color(section.color) // Section d'un niveau d'urgence
                        else -> Palette.text
                    },
                    modifier = Modifier.fillMaxWidth().padding(start = 16.dp, end = 16.dp, top = 16.dp, bottom = 6.dp).animateItem(),
                )
            }
            items(section.tasks, key = { it.task.id }) { due ->
                Column(Modifier.animateItem()) { // Fondu quand une tâche cochée ou repoussée quitte la vue
                    TaskItem(
                        due.task,
                        urgency = due.task.urgencyId?.let { model.urgencies[it] },
                        card = false,
                        onToggleDone = { model.toggleDone(due.task) },
                        onOpen = { model.tap(due.task) },
                        onLongPress = { model.toggleSelection(due.task) },
                        origin = due.list,
                        selected = due.task.id in model.selectedIds,
                    )
                    HorizontalDivider(color = Palette.border)
                }
            }
        }
    }
}

/** Icône d'une liste intelligente : blanche sur un carré de sa couleur (comme les tuiles du site). */
@Composable
fun SmartIconBox(icon: Int, color: String, size: Int = 30) {
    Box(
        Modifier.size(size.dp).background(ListIcons.color(color), RoundedCornerShape(8.dp)),
        contentAlignment = Alignment.Center,
    ) {
        Icon(painterResource(icon), contentDescription = null, tint = Color.White, modifier = Modifier.size((size * 0.6).dp))
    }
}
