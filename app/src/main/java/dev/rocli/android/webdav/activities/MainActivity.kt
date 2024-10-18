package dev.rocli.android.webdav.activities

import android.graphics.drawable.Drawable
import android.os.Bundle
import android.view.Menu
import android.view.MenuItem
import android.view.View.GONE
import android.view.View.VISIBLE
import android.view.ViewGroup
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.view.ActionMode
import androidx.core.view.ViewPropertyAnimatorCompat
import androidx.navigation.NavController
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.findNavController
import androidx.navigation.ui.NavigationUI.setupActionBarWithNavController
import com.google.android.material.appbar.AppBarLayout
import com.google.android.material.color.DynamicColors
import com.google.android.material.color.MaterialColors
import dagger.hilt.android.AndroidEntryPoint
import dev.rocli.android.webdav.BuildConfig
import dev.rocli.android.webdav.R
import dev.rocli.android.webdav.databinding.ActivityMainBinding
import dev.rocli.android.webdav.fragments.MainFragmentDirections
import java.lang.reflect.Field

@AndroidEntryPoint
class MainActivity : AppCompatActivity() {
    private lateinit var binding: ActivityMainBinding
    private lateinit var navController: NavController
    private lateinit var statusGuardHack: ActionModeStatusGuardHack

    override fun onCreate(savedInstanceState: Bundle?) {
        if (BuildConfig.DYNAMIC_COLORS.get()) {
            DynamicColors.applyToActivityIfAvailable(this)
        }
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)
        setSupportActionBar(findViewById(R.id.toolbar))
        statusGuardHack = ActionModeStatusGuardHack()

        binding.fab.setOnClickListener {
            val action = MainFragmentDirections.actionMainFragmentToAccountFragment(getString(R.string.add_account), -1)
            navController.navigate(action)
        }

        navController = findNavController(R.id.nav_host_fragment)
        navController.addOnDestinationChangedListener { _, dest, _ ->
            binding.fab.visibility = if (dest.id == R.id.MainFragment) VISIBLE else GONE
        }

        // Override the main fragment label during instrumented tests so that
        // we don't see "(Debug)" in the automatically generated screenshots.
        if (BuildConfig.TEST.get()) {
            navController.graph.findStartDestination().label = getString(R.string.app_name_release)
        }

        setupActionBarWithNavController(this, navController)
    }

    override fun onSupportNavigateUp(): Boolean {
        return navController.navigateUp() || super.onSupportNavigateUp()
    }

    override fun onCreateOptionsMenu(menu: Menu): Boolean {
        menuInflater.inflate(R.menu.menu_main, menu)
        return true
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        return when (item.itemId) {
            R.id.action_about -> {
                val action = MainFragmentDirections.actionMainFragmentToAboutFragment()
                navController.navigate(action)
                true
            }
            else -> super.onOptionsItemSelected(item)
        }
    }

    override fun onSupportActionModeStarted(mode: ActionMode) {
        super.onSupportActionModeStarted(mode)
        statusGuardHack.apply(VISIBLE)
    }

    override fun onSupportActionModeFinished(mode: ActionMode) {
        super.onSupportActionModeFinished(mode)
        statusGuardHack.apply(GONE)
    }

    /**
     * When starting/finishing an action mode, forcefully cancel the fade in/out animation and
     * set the status bar color. This requires the abc_decor_view_status_guard colors to be set
     * to transparent.
     *
     * This should fix any inconsistencies between the color of the action bar and the status bar
     * when an action mode is active.
     */
    private inner class ActionModeStatusGuardHack {
        private var fadeAnimField: Field? = null
        private var actionModeViewField: Field? = null
        private var appBarBackground: Drawable? = null

        init {
            try {
                fadeAnimField = delegate.javaClass.getDeclaredField("mFadeAnim").apply {
                    isAccessible = true
                }
                actionModeViewField = delegate.javaClass.getDeclaredField("mActionModeView").apply {
                    isAccessible = true
                }
            } catch (ignored: NoSuchFieldException) {
            }
        }

        fun apply(visibility: Int) {
            if (fadeAnimField == null || actionModeViewField == null) {
                return
            }

            val fadeAnim: ViewPropertyAnimatorCompat?
            val actionModeView: ViewGroup?
            try {
                fadeAnim = fadeAnimField?.get(delegate) as ViewPropertyAnimatorCompat?
                actionModeView = actionModeViewField?.get(delegate) as ViewGroup?
            } catch (e: IllegalAccessException) {
                return
            }

            val appBarLayout = findViewById<AppBarLayout>(R.id.app_bar_layout)
            if (appBarLayout != null && appBarBackground == null) {
                appBarBackground = appBarLayout.background
            }

            if (fadeAnim == null || actionModeView == null || appBarLayout == null || appBarBackground == null) {
                return
            }
            fadeAnim.cancel()

            if (visibility == VISIBLE) {
                actionModeView.visibility = VISIBLE
                actionModeView.alpha = 1f
                val color = MaterialColors.getColor(
                    appBarLayout,
                    com.google.android.material.R.attr.colorSurfaceContainer
                )
                appBarLayout.setBackgroundColor(color)
            } else {
                actionModeView.visibility = GONE
                actionModeView.alpha = 0f
                appBarLayout.background = appBarBackground
            }
        }
    }
}
