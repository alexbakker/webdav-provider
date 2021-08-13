package me.alexbakker.webdav.modules

import android.content.Context
import android.widget.Toast
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import me.alexbakker.webdav.R
import me.alexbakker.webdav.settings.Settings
import java.io.IOException
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
class ApplicationModule {

    @Provides
    @Singleton
    fun provideSettings(@ApplicationContext context: Context): Settings {
        try {
            if (Settings.fileExists(context)) {
                return Settings.readFile(context)
            }
        } catch (e: IOException) {
            e.printStackTrace()
            Toast.makeText(context, R.string.error_loading_settings, Toast.LENGTH_LONG).show()
        }

        return Settings()
    }
}
