package io.github.acssio.bettertodo.local

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

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
        OutboxEntity::class,
        SyncStateEntity::class,
    ],
    version = 3, // 2 : file d'attente de la synchronisation ; 3 : email du compte (8-5)
    exportSchema = false,
)
abstract class LocalDatabase : RoomDatabase() {
    abstract fun dao(): LocalDao

    companion object {
        /** 1 → 2 : tables outbox et sync_state (8-5). Le SQL est celui que Room génère pour ces entités. */
        val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    "CREATE TABLE IF NOT EXISTS `outbox` (`seq` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, " +
                        "`method` TEXT NOT NULL, `path` TEXT NOT NULL, `body` TEXT)"
                )
                db.execSQL(
                    "CREATE TABLE IF NOT EXISTS `sync_state` (`id` INTEGER NOT NULL, `userId` TEXT, `linked` INTEGER NOT NULL, " +
                        "`cursor` TEXT, `lastSync` INTEGER, PRIMARY KEY(`id`))"
                )
            }
        }

        /** 2 → 3 : email du compte relié, affiché dans les Paramètres (8-5). */
        val MIGRATION_2_3 = object : Migration(2, 3) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE `profile` ADD COLUMN `email` TEXT NOT NULL DEFAULT ''")
            }
        }

        @Volatile
        private var instance: LocalDatabase? = null

        /** Une seule base pour toute l'app (Room garde ses connexions ouvertes). */
        fun get(context: Context): LocalDatabase {
            return instance ?: synchronized(this) {
                instance ?: Room.databaseBuilder(context.applicationContext, LocalDatabase::class.java, "bettertodo.db")
                    .addMigrations(MIGRATION_1_2, MIGRATION_2_3)
                    .build()
                    .also { instance = it }
            }
        }
    }
}
