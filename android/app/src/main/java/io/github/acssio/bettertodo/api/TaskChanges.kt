package io.github.acssio.bettertodo.api

import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive

/**
 * Corps de PATCH /tasks/{id} : seuls les champs modifiés sont envoyés.
 *
 * L'API distingue trois cas, comme Optional[T] côté Go :
 *  - champ absent      → on n'y touche pas ;
 *  - champ à null      → on l'efface (ex. "due_date": null = plus d'échéance) ;
 *  - champ avec valeur → on le remplace.
 *
 * Exemple : TaskChanges().dueDate(null).urgencyId("…").toJson()
 *         → {"due_date":null,"urgency_id":"…"}
 */
class TaskChanges {
    private val fields = linkedMapOf<String, JsonElement>() // Garde l'ordre d'ajout (plus lisible dans les logs)

    fun title(title: String) = apply { fields["title"] = JsonPrimitive(title) }
    fun notes(notes: String) = apply { fields["notes"] = JsonPrimitive(notes) }
    fun done(done: Boolean) = apply { fields["done"] = JsonPrimitive(done) }
    fun groupId(groupId: String) = apply { fields["group_id"] = JsonPrimitive(groupId) } // À la fin du groupe

    /** @param dueDate "2026-11-18", ou null pour enlever l'échéance */
    fun dueDate(dueDate: String?) = apply { fields["due_date"] = dueDate?.let { JsonPrimitive(it) } ?: JsonNull }

    /** @param urgencyId id du niveau, ou null pour enlever l'urgence */
    fun urgencyId(urgencyId: String?) = apply { fields["urgency_id"] = urgencyId?.let { JsonPrimitive(it) } ?: JsonNull }

    /** Remplace TOUS les tags de la tâche (noms). */
    fun tags(tags: List<String>) = apply { fields["tags"] = JsonArray(tags.map { JsonPrimitive(it) }) }

    fun toJson(): String = JsonObject(fields).toString()
}
