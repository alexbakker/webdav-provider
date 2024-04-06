package me.alexbakker.webdav.modules

import android.content.Context
import androidx.room.Room
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import me.alexbakker.webdav.data.AccountDao
import me.alexbakker.webdav.data.AppDatabase
import me.alexbakker.webdav.data.CacheDao
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
class DatabaseModule {
    @Provides
    @Singleton
    fun provideAppDatabase(@ApplicationContext context: Context): AppDatabase {
        return Room.databaseBuilder(context, AppDatabase::class.java, "webdav")
            .allowMainThreadQueries()
            .build()
    }

    @Provides
    fun provideAccountDao(appDatabase: AppDatabase): AccountDao {
        return appDatabase.accountDao()
    }

    @Provides
    fun provideCacheDao(appDatabase: AppDatabase): CacheDao {
        return appDatabase.cacheDao()
    }
}
