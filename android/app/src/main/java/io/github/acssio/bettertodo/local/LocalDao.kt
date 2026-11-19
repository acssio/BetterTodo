package io.github.acssio.bettertodo.local

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Update

/** Nom d'un tag d'une tâche (résultat d'une jointure task_tags × tags). */
data class TaskTagName(val taskId: String, val name: String)

/** Nombre de tâches qui portent un tag. */
data class TagCount(val id: String, val name: String, val taskCount: Int)

/**
 * Les requêtes SQL de la base locale. Room écrit le code à la compilation (KSP) ;
 * une requête fausse (table ou colonne inconnue) est une erreur de compilation.
 * Toute la logique (validation, ordre, groupe par défaut…) est dans LocalApi, pas ici.
 */
@Dao
interface LocalDao {

    // --- Compte local --------------------------------------------------------

    @Query("SELECT * FROM profile WHERE id = 1")
    suspend fun profile(): ProfileEntity?

    @Insert
    suspend fun insertProfile(profile: ProfileEntity)

    @Update
    suspend fun updateProfile(profile: ProfileEntity)

    // --- Groupes de listes ---------------------------------------------------

    @Query("SELECT * FROM folders ORDER BY position, createdAt")
    suspend fun folders(): List<FolderEntity>

    @Query("SELECT * FROM folders WHERE id = :id")
    suspend fun folder(id: String): FolderEntity?

    @Insert
    suspend fun insertFolder(folder: FolderEntity)

    @Update
    suspend fun updateFolder(folder: FolderEntity)

    @Query("UPDATE folders SET position = :position WHERE id = :id")
    suspend fun setFolderPosition(id: String, position: Int)

    @Query("DELETE FROM folders WHERE id = :id")
    suspend fun deleteFolder(id: String)

    // --- Listes --------------------------------------------------------------

    @Query("SELECT * FROM lists ORDER BY position, createdAt")
    suspend fun lists(): List<ListEntity>

    @Query("SELECT * FROM lists WHERE id = :id")
    suspend fun list(id: String): ListEntity?

    /** Les listes d'un même groupe de listes (ou hors groupe si folderId est null), dans l'ordre. */
    @Query("SELECT * FROM lists WHERE folderId IS :folderId ORDER BY position, createdAt")
    suspend fun listsIn(folderId: String?): List<ListEntity>

    /** Tâches pas encore faites de chaque liste (compteur de l'accueil). */
    @Query(
        """SELECT COUNT(*) FROM tasks t JOIN task_groups g ON g.id = t.groupId
           WHERE g.listId = :listId AND NOT t.done"""
    )
    suspend fun openTasks(listId: String): Int

    @Insert
    suspend fun insertList(list: ListEntity)

    @Update
    suspend fun updateList(list: ListEntity)

    @Query("UPDATE lists SET position = :position WHERE id = :id")
    suspend fun setListPosition(id: String, position: Int)

    @Query("DELETE FROM lists WHERE id = :id")
    suspend fun deleteList(id: String)

    // --- Groupes de tâches ---------------------------------------------------

    @Query("SELECT * FROM task_groups WHERE listId = :listId ORDER BY position, createdAt")
    suspend fun groupsOf(listId: String): List<GroupEntity>

    @Query("SELECT * FROM task_groups WHERE id = :id")
    suspend fun group(id: String): GroupEntity?

    @Insert
    suspend fun insertGroup(group: GroupEntity)

    @Update
    suspend fun updateGroup(group: GroupEntity)

    @Query("DELETE FROM task_groups WHERE id = :id")
    suspend fun deleteGroup(id: String)

    // --- Tâches --------------------------------------------------------------

    @Query("SELECT * FROM tasks WHERE id = :id")
    suspend fun task(id: String): TaskEntity?

    @Query("SELECT * FROM tasks WHERE groupId = :groupId ORDER BY position, createdAt")
    suspend fun tasksOf(groupId: String): List<TaskEntity>

    /** Toutes les tâches d'une liste, dans l'ordre de leur groupe. */
    @Query(
        """SELECT t.* FROM tasks t JOIN task_groups g ON g.id = t.groupId
           WHERE g.listId = :listId ORDER BY t.position, t.createdAt"""
    )
    suspend fun tasksOfList(listId: String): List<TaskEntity>

    /**
     * Tâches à faire avec une échéance (Aujourd'hui / Planifié), jusqu'à until si elle n'est pas null.
     * Même tri que le serveur : date, heure (sans heure en dernier), liste, groupe, tâche.
     * Les dates "AAAA-MM-JJ" et heures "HH:MM" se comparent bien comme du texte.
     */
    @Query(
        """SELECT t.* FROM tasks t
           JOIN task_groups g ON g.id = t.groupId
           JOIN lists l ON l.id = g.listId
           WHERE NOT t.done AND t.dueDate IS NOT NULL AND (:until IS NULL OR t.dueDate <= :until)
           ORDER BY t.dueDate, t.dueTime IS NULL, t.dueTime, l.position, g.position, t.position"""
    )
    suspend fun dueTasks(until: String?): List<TaskEntity>

    @Query("SELECT COUNT(*) FROM tasks WHERE NOT done AND dueDate IS NOT NULL AND dueDate <= :today")
    suspend fun countDueToday(today: String): Int

    @Query("SELECT COUNT(*) FROM tasks WHERE NOT done AND dueDate IS NOT NULL")
    suspend fun countScheduled(): Int

    @Insert
    suspend fun insertTask(task: TaskEntity)

    @Update
    suspend fun updateTask(task: TaskEntity)

    @Query("UPDATE tasks SET position = :position WHERE id = :id")
    suspend fun setTaskPosition(id: String, position: Int)

    @Query("DELETE FROM tasks WHERE id = :id")
    suspend fun deleteTask(id: String)

    @Query("DELETE FROM tasks WHERE done AND groupId IN (SELECT id FROM task_groups WHERE listId = :listId)")
    suspend fun deleteDoneTasks(listId: String)

    // --- Tags ----------------------------------------------------------------

    @Query(
        """SELECT tt.taskId AS taskId, tg.name AS name FROM task_tags tt
           JOIN tags tg ON tg.id = tt.tagId WHERE tt.taskId IN (:taskIds) ORDER BY tg.name"""
    )
    suspend fun tagNamesOf(taskIds: List<String>): List<TaskTagName>

    @Query(
        """SELECT t.id AS id, t.name AS name, (SELECT COUNT(*) FROM task_tags tt WHERE tt.tagId = t.id) AS taskCount
           FROM tags t ORDER BY t.name"""
    )
    suspend fun tagsWithCount(): List<TagCount>

    @Query("SELECT * FROM tags WHERE id = :id")
    suspend fun tag(id: String): TagEntity?

    @Query("SELECT * FROM tags WHERE name = :name")
    suspend fun tagNamed(name: String): TagEntity?

    @Insert
    suspend fun insertTag(tag: TagEntity)

    @Update
    suspend fun updateTag(tag: TagEntity)

    @Query("DELETE FROM tags WHERE id = :id")
    suspend fun deleteTag(id: String)

    @Query("DELETE FROM task_tags WHERE taskId = :taskId")
    suspend fun clearTaskTags(taskId: String)

    @Insert
    suspend fun insertTaskTag(link: TaskTagEntity)

    // --- Niveaux d'urgence ---------------------------------------------------

    @Query("SELECT * FROM urgency_levels ORDER BY rank, createdAt")
    suspend fun urgencyLevels(): List<UrgencyEntity>

    @Query("SELECT * FROM urgency_levels WHERE id = :id")
    suspend fun urgencyLevel(id: String): UrgencyEntity?

    @Insert
    suspend fun insertUrgencyLevel(level: UrgencyEntity)

    @Update
    suspend fun updateUrgencyLevel(level: UrgencyEntity)

    @Query("UPDATE urgency_levels SET rank = :rank WHERE id = :id")
    suspend fun setUrgencyRank(id: String, rank: Int)

    @Query("DELETE FROM urgency_levels WHERE id = :id")
    suspend fun deleteUrgencyLevel(id: String)
}
