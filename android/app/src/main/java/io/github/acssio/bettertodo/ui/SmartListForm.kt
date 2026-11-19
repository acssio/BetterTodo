package io.github.acssio.bettertodo.ui

import io.github.acssio.bettertodo.api.SmartList
import io.github.acssio.bettertodo.api.SmartListChanges

/**
 * Le formulaire « Nouvelle liste intelligente » / « Modifier » (comme SmartListDialog du site), 8-6.
 * Gardé par SettingsModel : il survit à la rotation. Classe simple, testée par SmartListFormTest.
 *
 * @param id null = nouvelle liste
 */
data class SmartListForm(
    val id: String? = null,
    val name: String = "",
    val color: String = ListIcons.colors.first(),
    val icon: String = "sparkles",
    val kind: String = SmartList.KIND_TAGS,
    val tagMatch: String = SmartList.MATCH_ANY,
    val tagIds: List<String> = emptyList(),
    val urgencyIds: List<String> = emptyList(),
) {
    /** Mêmes règles que le serveur : un nom, et au moins un tag (par tags) ou un niveau (par urgence). */
    val canSave: Boolean
        get() = name.isNotBlank() && if (kind == SmartList.KIND_TAGS) tagIds.isNotEmpty() else urgencyIds.isNotEmpty()

    /** Ajoute ou retire un tag (toucher une puce). */
    fun toggleTag(id: String) = copy(tagIds = if (id in tagIds) tagIds - id else tagIds + id)

    /** Ajoute ou retire un niveau d'urgence. */
    fun toggleUrgency(id: String) = copy(urgencyIds = if (id in urgencyIds) urgencyIds - id else urgencyIds + id)

    /** Ce qu'on envoie : tout le formulaire (le type et ses ids vont toujours ensemble). */
    fun toChanges(): SmartListChanges {
        val changes = SmartListChanges().name(name.trim()).color(color).icon(icon).kind(kind)
        return if (kind == SmartList.KIND_TAGS) changes.tagMatch(tagMatch).tagIds(tagIds) else changes.urgencyIds(urgencyIds)
    }

    companion object {
        /** Le formulaire d'une liste existante. */
        fun of(list: SmartList) = SmartListForm(list.id, list.name, list.color, list.icon, list.kind, list.tagMatch, list.tagIds, list.urgencyIds)
    }
}
