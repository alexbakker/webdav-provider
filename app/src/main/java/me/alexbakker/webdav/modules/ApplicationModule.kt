package me.alexbakker.webdav.modules

import android.content.Context
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.components.ApplicationComponent
import dagger.hilt.android.qualifiers.ApplicationContext
import me.alexbakker.webdav.settings.Settings
import javax.inject.Singleton

@Module
@InstallIn(ApplicationComponent::class)
class ApplicationModule {

    @Provides
    @Singleton
    fun provideSettings(@ApplicationContext context: Context): Settings {
        return if (Settings.fileExists(context)) {
            Settings.readFile(context)
        } else {
            Settings()
        }
    }
}
