package io.github.acssio.bettertodo.api

import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive

/**
 * Corps de POST /smart-lists et PATCH /smart-lists/{id} : seuls les champs donnés sont envoyés
 * (comme TaskChanges et ListChanges). Changer kind demande d'envoyer aussi ses ids.
 */
class SmartListChanges {
    private val fields = linkedMapOf<String, JsonElement>()

    fun name(name: String) = apply { fields["name"] = JsonPrimitive(name) }
    fun color(color: String) = apply { fields["color"] = JsonPrimitive(color) }
    fun icon(icon: String) = apply { fields["icon"] = JsonPrimitive(icon) }
    fun kind(kind: String) = apply { fields["kind"] = JsonPrimitive(kind) }          // "tags" ou "urgency"
    fun tagMatch(match: String) = apply { fields["tag_match"] = JsonPrimitive(match) } // "any" ou "all"
    fun tagIds(ids: List<String>) = apply { fields["tag_ids"] = JsonArray(ids.map { JsonPrimitive(it) }) }
    fun urgencyIds(ids: List<String>) = apply { fields["urgency_ids"] = JsonArray(ids.map { JsonPrimitive(it) }) }
    fun visible(visible: Boolean) = apply { fields["visible"] = JsonPrimitive(visible) }

    /** Le JSON, avec en plus "id" à la création hors ligne (8-5). */
    fun toJson(id: String? = null): String {
        val all = if (id != null) linkedMapOf<String, JsonElement>("id" to JsonPrimitive(id)).apply { putAll(fields) } else fields
        return JsonObject(all).toString()
    }
}
