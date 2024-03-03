package me.alexbakker.webdav.modules

import android.content.Context
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import me.alexbakker.webdav.data.CacheDao
import me.alexbakker.webdav.provider.WebDavCache
import me.alexbakker.webdav.provider.WebDavClientManager
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
class ApplicationModule {
    @Provides
    @Singleton
    fun provideWebDavCache(@ApplicationContext context: Context, cacheDao: CacheDao): WebDavCache {
        return WebDavCache(context, cacheDao)
    }

    @Provides
    @Singleton
    fun provideWebDavClientManager(@ApplicationContext context: Context): WebDavClientManager {
        return WebDavClientManager(context)
    }
}
