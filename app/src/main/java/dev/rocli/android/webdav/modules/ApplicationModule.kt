package dev.rocli.android.webdav.modules

import android.content.Context
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import dev.rocli.android.webdav.data.CacheDao
import dev.rocli.android.webdav.provider.WebDavCache
import dev.rocli.android.webdav.provider.WebDavClientManager
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
