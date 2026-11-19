package io.github.acssio.bettertodo.local

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

/*
 * Tables de la base locale (Room, donc SQLite sur le téléphone).
 * Le même schéma que PostgreSQL (api/migrations/001_schema.sql), sans user_id :
 * la base du téléphone n'a qu'un seul compte.
 *
 * Les id sont des UUID générés par l'app (UUID.randomUUID()) : le jour où l'app se synchronise
 * avec un serveur (8-5), ils restent les mêmes des deux côtés.
 * createdAt (millisecondes) départage deux éléments à la même position, comme sur le serveur.
 *
 * Suppressions en cascade, comme sur le serveur :
 *   groupe de listes supprimé → ses listes restent, hors groupe (SET_NULL)
 *   liste → ses groupes → leurs tâches → leurs tags de tâche (CASCADE)
 *   niveau d'urgence supprimé → ses tâches passent sans urgence (SET_NULL)
 */

/** Un groupe de listes (list_folders). */
@Entity(tableName = "folders")
data class FolderEntity(
    @PrimaryKey val id: String,
    val name: String,
    val position: Int,
    val collapsed: Boolean = false,
    val createdAt: Long,
)

/** Une liste (lists). */
@Entity(
    tableName = "lists",
    foreignKeys = [ForeignKey(FolderEntity::class, ["id"], ["folderId"], onDelete = ForeignKey.SET_NULL)],
    indices = [Index("folderId")],
)
data class ListEntity(
    @PrimaryKey val id: String,
    val folderId: String?,   // null = hors groupe de listes
    val name: String,
    val color: String,       // "#RRGGBB"
    val icon: String,        // Nom d'icône Lucide
    val defaultView: String, // "columns" ou "sections"
    val showDone: Boolean,
    val position: Int,       // Parmi les listes du même groupe de listes
    val createdAt: Long,
)

/** Un groupe de tâches (task_groups). */
@Entity(
    tableName = "task_groups",
    foreignKeys = [ForeignKey(ListEntity::class, ["id"], ["listId"], onDelete = ForeignKey.CASCADE)],
    indices = [Index("listId")],
)
data class GroupEntity(
    @PrimaryKey val id: String,
    val listId: String,
    val name: String,
    val position: Int,
    val createdAt: Long,
)

/** Un niveau d'urgence (urgency_levels). rank 0 = le moins urgent. */
@Entity(tableName = "urgency_levels")
data class UrgencyEntity(
    @PrimaryKey val id: String,
    val name: String,
    val color: String,
    val rank: Int,
    val createdAt: Long,
)

/** Une tâche (tasks). Jamais d'heure sans date (vérifié par LocalApi, comme la contrainte du serveur). */
@Entity(
    tableName = "tasks",
    foreignKeys = [
        ForeignKey(GroupEntity::class, ["id"], ["groupId"], onDelete = ForeignKey.CASCADE),
        ForeignKey(UrgencyEntity::class, ["id"], ["urgencyId"], onDelete = ForeignKey.SET_NULL),
    ],
    indices = [Index("groupId"), Index("urgencyId")],
)
data class TaskEntity(
    @PrimaryKey val id: String,
    val groupId: String,
    val title: String,
    val notes: String = "",
    val dueDate: String? = null,   // "2026-11-18"
    val dueTime: String? = null,   // "14:30"
    val urgencyId: String? = null,
    val done: Boolean = false,
    val doneAt: Long? = null,
    val position: Int,             // Dans son groupe
    val createdAt: Long,
)

/** Un tag (tags). Le nom est unique. */
@Entity(tableName = "tags", indices = [Index(value = ["name"], unique = true)])
data class TagEntity(
    @PrimaryKey val id: String,
    val name: String,
)

/** Lien tâche ↔ tag (task_tags). */
@Entity(
    tableName = "task_tags",
    primaryKeys = ["taskId", "tagId"],
    foreignKeys = [
        ForeignKey(TaskEntity::class, ["id"], ["taskId"], onDelete = ForeignKey.CASCADE),
        ForeignKey(TagEntity::class, ["id"], ["tagId"], onDelete = ForeignKey.CASCADE),
    ],
    indices = [Index("tagId")],
)
data class TaskTagEntity(
    val taskId: String,
    val tagId: String,
)

/** Les réglages du compte local (la ligne users du serveur, une seule ligne : id = 1). */
@Entity(tableName = "profile")
data class ProfileEntity(
    @PrimaryKey val id: Int = 1,
    val displayName: String = "Moi",
    val language: String = "fr",
    val defaultView: String = "columns",
    val quickTaskDue: String = "today",
    @ColumnInfo(defaultValue = "") val email: String = "", // Celui du compte du serveur relié (8-5), "" sans serveur
)

/**
 * File d'attente de la synchronisation (8-5) : chaque modification faite sur le téléphone,
 * sous la forme de la requête REST qu'il faudra envoyer au serveur, dans l'ordre (seq).
 * Vide sans serveur : LocalApi n'y écrit que lorsque la synchronisation est active.
 */
@Entity(tableName = "outbox")
data class OutboxEntity(
    @PrimaryKey(autoGenerate = true) val seq: Long = 0,
    val method: String,  // "POST", "PATCH", "DELETE"
    val path: String,    // "/tasks/<id>" (après /api/v1)
    val body: String?,   // JSON, ou null
)

/** Où en est la synchronisation (une seule ligne, id = 1). */
@Entity(tableName = "sync_state")
data class SyncStateEntity(
    @PrimaryKey val id: Int = 1,
    val userId: String? = null,  // Le compte du serveur relié (un autre compte = tout recommencer)
    val linked: Boolean = false, // Les données du téléphone ont déjà été envoyées à ce serveur (premier lien fait)
    val cursor: String? = null,  // Renvoyé par GET /sync, à redonner la fois suivante
    val lastSync: Long? = null,  // Dernière synchronisation réussie (millisecondes)
)
