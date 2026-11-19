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
        SmartListEntity::class,
    ],
    version = 4, // 2 : file d'attente de la synchronisation ; 3 : email du compte (8-5) ; 4 : listes intelligentes (8-6)
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

        /** 3 → 4 : listes intelligentes personnalisées, tuiles Aujourd'hui / Planifié affichées ou non (8-6). */
        val MIGRATION_3_4 = object : Migration(3, 4) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    "CREATE TABLE IF NOT EXISTS `smart_lists` (`id` TEXT NOT NULL, `name` TEXT NOT NULL, `color` TEXT NOT NULL, " +
                        "`icon` TEXT NOT NULL, `kind` TEXT NOT NULL, `tagMatch` TEXT NOT NULL, `tagIds` TEXT NOT NULL, " +
                        "`urgencyIds` TEXT NOT NULL, `visible` INTEGER NOT NULL, `position` INTEGER NOT NULL, " +
                        "`createdAt` INTEGER NOT NULL, PRIMARY KEY(`id`))"
                )
                db.execSQL("ALTER TABLE `profile` ADD COLUMN `showToday` INTEGER NOT NULL DEFAULT 1")
                db.execSQL("ALTER TABLE `profile` ADD COLUMN `showScheduled` INTEGER NOT NULL DEFAULT 1")
            }
        }

        @Volatile
        private var instance: LocalDatabase? = null

        /** Une seule base pour toute l'app (Room garde ses connexions ouvertes). */
        fun get(context: Context): LocalDatabase {
            return instance ?: synchronized(this) {
                instance ?: Room.databaseBuilder(context.applicationContext, LocalDatabase::class.java, "bettertodo.db")
                    .addMigrations(MIGRATION_1_2, MIGRATION_2_3, MIGRATION_3_4)
                    .build()
                    .also { instance = it }
            }
        }
    }
}
