package io.github.acssio.bettertodo.api

import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive

/**
 * Corps de PATCH /lists/{id} : seuls les champs modifiés sont envoyés (comme TaskChanges).
 * "folder_id": null = sortir la liste de son groupe de listes.
 */
class ListChanges {
    private val fields = linkedMapOf<String, JsonElement>()

    fun name(name: String) = apply { fields["name"] = JsonPrimitive(name) }
    fun color(color: String) = apply { fields["color"] = JsonPrimitive(color) }
    fun icon(icon: String) = apply { fields["icon"] = JsonPrimitive(icon) }
    fun defaultView(view: String) = apply { fields["default_view"] = JsonPrimitive(view) } // "columns" ou "sections"
    fun showDone(showDone: Boolean) = apply { fields["show_done"] = JsonPrimitive(showDone) }

    /** @param folderId le groupe de listes, ou null pour hors groupe */
    fun folderId(folderId: String?) = apply { fields["folder_id"] = folderId?.let { JsonPrimitive(it) } ?: JsonNull }

    fun toJson(): String = JsonObject(fields).toString()
}
