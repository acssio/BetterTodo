package io.github.acssio.bettertodo.local

/**
 * Le filtre d'une liste intelligente, comme sqlSmartMatch du serveur (smartlists.go) :
 *  - par urgence : le niveau de la tâche est un de ceux choisis ;
 *  - par tags, « au moins un » : la tâche porte au moins un des tags ;
 *  - par tags, « tous » : la tâche porte tous les tags choisis (et il en reste au moins un).
 * Les tags et niveaux supprimés depuis ne sont plus dans tagIds / urgencyIds (on ne garde que ceux qui existent).
 * Fonction pure : testée par SmartMatchTest.
 */
object SmartMatch {
    fun matches(kind: String, tagMatch: String, tagIds: Set<String>, urgencyIds: Set<String>, taskTagIds: Set<String>, taskUrgencyId: String?): Boolean {
        return when (kind) {
            "urgency" -> taskUrgencyId != null && taskUrgencyId in urgencyIds
            "tags" -> if (tagMatch == "all") tagIds.isNotEmpty() && taskTagIds.containsAll(tagIds) else taskTagIds.any { it in tagIds }
            else -> false
        }
    }

    /** "a,b" → [a, b] (une chaîne vide donne une liste vide). */
    fun split(ids: String): List<String> = ids.split(',').filter { it.isNotEmpty() }
}
