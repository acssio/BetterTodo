package io.github.acssio.bettertodo.local

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase

/**
 * La base locale du téléphone (fichier SQLite « bettertodo.db », privé à l'app).
 *
 * version : à augmenter à chaque changement de table, avec une migration (comme api/migrations).
 * Jamais de destruction automatique : ce sont les données de l'utilisateur.
 */
@Database(
    entities = [
        FolderEntity::class,
        ListEntity::class,
        GroupEntity::class,
        UrgencyEntity::class,
        TaskEntity::class,
        TagEntity::class,
        TaskTagEntity::class,
        ProfileEntity::class,
    ],
    version = 1,
    exportSchema = false,
)
abstract class LocalDatabase : RoomDatabase() {
    abstract fun dao(): LocalDao

    companion object {
        @Volatile
        private var instance: LocalDatabase? = null

        /** Une seule base pour toute l'app (Room garde ses connexions ouvertes). */
        fun get(context: Context): LocalDatabase {
            return instance ?: synchronized(this) {
                instance ?: Room.databaseBuilder(context.applicationContext, LocalDatabase::class.java, "bettertodo.db")
                    .build()
                    .also { instance = it }
            }
        }
    }
}
