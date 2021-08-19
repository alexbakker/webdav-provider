package me.alexbakker.webdav.data

import androidx.room.Database
import androidx.room.RoomDatabase

@Database(entities = [Account::class, CacheEntry::class], version = 1)
abstract class AppDatabase : RoomDatabase() {
    abstract fun accountDao(): AccountDao
    abstract fun cacheDao(): CacheDao
}
