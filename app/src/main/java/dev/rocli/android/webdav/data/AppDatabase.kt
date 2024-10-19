package dev.rocli.android.webdav.data

import androidx.room.AutoMigration
import androidx.room.Database
import androidx.room.RoomDatabase
import androidx.room.TypeConverters
import androidx.room.migration.AutoMigrationSpec
import androidx.sqlite.db.SupportSQLiteDatabase

@Database(
    version = 5,
    exportSchema = true,
    entities = [Account::class, CacheEntry::class],
    autoMigrations = [
        AutoMigration(from = 1, to = 2),
        AutoMigration(from = 2, to = 3),
        AutoMigration(from = 3, to = 4),
        AutoMigration(from = 4, to = 5, spec = AppDatabase.AuthTypeMigration::class)
    ]
)
@TypeConverters(SecretStringConverter::class)
abstract class AppDatabase : RoomDatabase() {
    abstract fun accountDao(): AccountDao
    abstract fun cacheDao(): CacheDao

    class AuthTypeMigration : AutoMigrationSpec {
        override fun onPostMigrate(db: SupportSQLiteDatabase) {
            db.execSQL("UPDATE account SET auth_type = 'BASIC' WHERE username IS NOT NULL OR password IS NOT NULL")
        }
    }
}
