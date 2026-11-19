package io.github.acssio.bettertodo

import io.github.acssio.bettertodo.api.DueTask
import io.github.acssio.bettertodo.api.ListRef
import io.github.acssio.bettertodo.api.SmartList
import io.github.acssio.bettertodo.api.Task
import io.github.acssio.bettertodo.api.UrgencyLevel
import io.github.acssio.bettertodo.local.SmartMatch
import io.github.acssio.bettertodo.ui.SmartListForm
import io.github.acssio.bettertodo.ui.SmartLists
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** Listes intelligentes perso (8-6) : le filtre (comme sqlSmartMatch du serveur), les sections, le formulaire. */
class SmartMatchTest {
    @Test
    fun parUrgence() {
        assertTrue(SmartMatch.matches("urgency", "any", emptySet(), setOf("haute"), emptySet(), "haute"))
        assertFalse(SmartMatch.matches("urgency", "any", emptySet(), setOf("haute"), emptySet(), "basse"))
        assertFalse(SmartMatch.matches("urgency", "any", emptySet(), setOf("haute"), emptySet(), null))
    }

    @Test
    fun parTagsAuMoinsUn() {
        assertTrue(SmartMatch.matches("tags", "any", setOf("a", "b"), emptySet(), setOf("b", "c"), null))
        assertFalse(SmartMatch.matches("tags", "any", setOf("a", "b"), emptySet(), setOf("c"), null))
    }

    @Test
    fun parTagsTous() {
        assertTrue(SmartMatch.matches("tags", "all", setOf("a", "b"), emptySet(), setOf("a", "b", "c"), null))
        assertFalse(SmartMatch.matches("tags", "all", setOf("a", "b"), emptySet(), setOf("a"), null))
        assertFalse(SmartMatch.matches("tags", "all", emptySet(), emptySet(), setOf("a"), null)) // Plus aucun tag : rien
    }

    @Test
    fun idsSepares() {
        assertEquals(listOf("a", "b"), SmartMatch.split("a,b"))
        assertEquals(emptyList<String>(), SmartMatch.split(""))
    }

    private val list = ListRef("l", "Liste", "#2F6FEB", "list")
    private fun due(id: String, urgency: String?, tags: List<String> = emptyList(), done: Boolean = false) =
        DueTask(Task(id, "g", id, urgencyId = urgency, position = 0, tags = tags, done = done), list, "À faire")

    @Test
    fun sectionsParUrgence() {
        val levels = mapOf("h" to UrgencyLevel("h", "Haute", "#E5484D", 2), "m" to UrgencyLevel("m", "Moyenne", "#E8A33D", 1))
        val urgent = SmartList("s", "Urgent", "#E5484D", "flame", SmartList.KIND_URGENCY, urgencyIds = listOf("h", "m"))
        val sections = SmartLists.customSections(listOf(due("a", "h"), due("b", "h"), due("c", "m")), urgent, levels)
        assertEquals(listOf("Haute" to listOf("a", "b"), "Moyenne" to listOf("c")), sections.map { s -> s.title to s.tasks.map { it.task.id } })
        assertEquals("#E5484D", sections.first().color)
    }

    @Test
    fun sectionParTags() {
        val travail = SmartList("s", "Travail", "#2F6FEB", "briefcase", SmartList.KIND_TAGS, labels = listOf("#boulot"))
        assertEquals(listOf("À faire"), SmartLists.customSections(listOf(due("a", null, listOf("boulot"))), travail, emptyMap()).map { it.title })
        assertEquals(emptyList<String>(), SmartLists.customSections(emptyList(), travail, emptyMap()).map { it.title })
    }

    @Test
    fun uneTacheQuittentLaVue() {
        val travail = SmartList("s", "Travail", "#2F6FEB", "briefcase", SmartList.KIND_TAGS, labels = listOf("#boulot"))
        assertTrue(SmartLists.belongsCustom(due("a", null, listOf("boulot")).task, travail))
        assertFalse(SmartLists.belongsCustom(due("a", null, listOf("boulot"), done = true).task, travail)) // Cochée
        assertFalse(SmartLists.belongsCustom(due("a", null, emptyList()).task, travail))                   // Tag enlevé
    }

    @Test
    fun resume() {
        assertEquals("urgence Haute ou Moyenne", SmartList("s", "U", "#000000", "x", SmartList.KIND_URGENCY, labels = listOf("Haute", "Moyenne")).summary)
        assertEquals("#a et #b", SmartList("s", "T", "#000000", "x", SmartList.KIND_TAGS, SmartList.MATCH_ALL, labels = listOf("#a", "#b")).summary)
        assertEquals("#a ou #b", SmartList("s", "T", "#000000", "x", SmartList.KIND_TAGS, labels = listOf("#a", "#b")).summary)
    }

    @Test
    fun formulaire() {
        val empty = SmartListForm()
        assertFalse(empty.canSave)
        assertFalse(empty.copy(name = "Travail").canSave)            // Par tags : il faut un tag
        val tags = empty.copy(name = "Travail").toggleTag("t1")
        assertTrue(tags.canSave)
        assertFalse(tags.toggleTag("t1").canSave)                   // Retoucher la puce l'enlève
        assertFalse(tags.copy(kind = SmartList.KIND_URGENCY).canSave) // Par urgence : il faut un niveau
        assertEquals(
            """{"name":"Travail","color":"${empty.color}","icon":"sparkles","kind":"tags","tag_match":"any","tag_ids":["t1"]}""",
            tags.toChanges().toJson(),
        )
        assertEquals(
            """{"id":"x","name":"U","color":"${empty.color}","icon":"sparkles","kind":"urgency","urgency_ids":["h"]}""",
            empty.copy(name = " U ", kind = SmartList.KIND_URGENCY).toggleUrgency("h").toChanges().toJson(id = "x"),
        )
    }
}
