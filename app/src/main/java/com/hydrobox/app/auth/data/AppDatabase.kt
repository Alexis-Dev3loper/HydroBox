package com.hydrobox.app.auth.data

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

internal val AUTH_SCHEMA_V4_STATEMENTS = listOf(
    """
        CREATE TABLE IF NOT EXISTS `users_local_new` (
            `id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
            `name` TEXT NOT NULL,
            `lastName` TEXT NOT NULL,
            `email` TEXT NOT NULL,
            `principalUuid` TEXT,
            `roleKey` TEXT,
            `avatarUri` TEXT,
            `phonePrefix` TEXT,
            `phone` TEXT
        )
    """.trimIndent(),
    """
        INSERT INTO `users_local_new`
            (`id`, `name`, `lastName`, `email`, `principalUuid`, `roleKey`, `avatarUri`, `phonePrefix`, `phone`)
        SELECT
            `id`, `name`, `lastName`, `email`, NULL, NULL, `avatarUri`, `phonePrefix`, `phone`
        FROM `users_local`
    """.trimIndent(),
    "DROP TABLE `users_local`",
    "ALTER TABLE `users_local_new` RENAME TO `users_local`"
)

val MIGRATION_3_4 = object : Migration(3, 4) {
    override fun migrate(db: SupportSQLiteDatabase) {
        AUTH_SCHEMA_V4_STATEMENTS.forEach(db::execSQL)
    }
}

@Database(entities = [UserEntity::class], version = 4, exportSchema = false)
abstract class AppDatabase : RoomDatabase() {
    abstract fun authDao(): AuthDao

    companion object {
        @Volatile private var INSTANCE: AppDatabase? = null

        fun get(context: Context): AppDatabase =
            INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(context, AppDatabase::class.java, "hydro_local.db")
                    .addMigrations(MIGRATION_3_4)
                    .build()
                INSTANCE = instance
                instance
            }
    }
}
