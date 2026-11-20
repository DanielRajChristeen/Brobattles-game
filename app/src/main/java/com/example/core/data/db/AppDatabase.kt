package com.example.core.data.db

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import com.example.core.data.model.MatchRecord
import com.example.core.data.model.SavedGameSession
import com.example.core.data.model.UserSettings

@Database(
    entities = [MatchRecord::class, UserSettings::class, SavedGameSession::class],
    version = 3,
    exportSchema = false
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun matchRecordDao(): MatchRecordDao
    abstract fun userSettingsDao(): UserSettingsDao
    abstract fun savedGameSessionDao(): SavedGameSessionDao

    companion object {
        @Volatile
        private var INSTANCE: AppDatabase? = null

        val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE user_settings ADD COLUMN username TEXT NOT NULL DEFAULT 'Player'")
            }
        }

        val MIGRATION_2_3 = object : Migration(2, 3) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS `saved_bot_sessions` (
                        `gameId` TEXT NOT NULL,
                        `schemaVersion` INTEGER NOT NULL,
                        `payloadJson` TEXT NOT NULL,
                        `checksum` TEXT NOT NULL,
                        `summary` TEXT NOT NULL,
                        `updatedAt` INTEGER NOT NULL,
                        PRIMARY KEY(`gameId`)
                    )
                    """.trimIndent()
                )
            }
        }

        fun getInstance(context: Context): AppDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    AppDatabase::class.java,
                    "chill_games_local.db"
                )
                    .addMigrations(MIGRATION_1_2, MIGRATION_2_3)
                    .fallbackToDestructiveMigration()
                    .build()
                INSTANCE = instance
                instance
            }
        }
    }
}
