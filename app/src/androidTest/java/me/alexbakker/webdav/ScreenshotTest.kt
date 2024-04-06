package me.alexbakker.webdav

import androidx.test.core.app.ActivityScenario
import androidx.test.core.app.takeScreenshot
import androidx.test.core.graphics.writeToTestStorage
import androidx.test.espresso.Espresso.onView
import androidx.test.espresso.action.ViewActions.click
import androidx.test.espresso.action.ViewActions.longClick
import androidx.test.espresso.action.ViewActions.pressBack
import androidx.test.espresso.assertion.ViewAssertions.matches
import androidx.test.espresso.contrib.RecyclerViewActions
import androidx.test.espresso.matcher.ViewMatchers.hasDescendant
import androidx.test.espresso.matcher.ViewMatchers.isRoot
import androidx.test.espresso.matcher.ViewMatchers.withId
import androidx.test.espresso.matcher.ViewMatchers.withText
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.uiautomator.By
import androidx.test.uiautomator.UiDevice
import androidx.test.uiautomator.Until
import dagger.hilt.android.testing.HiltAndroidRule
import dagger.hilt.android.testing.HiltAndroidTest
import me.alexbakker.webdav.activities.MainActivity
import me.alexbakker.webdav.adapters.AccountAdapter
import me.alexbakker.webdav.data.Account
import me.alexbakker.webdav.data.AccountDao
import me.alexbakker.webdav.data.SecretString
import org.junit.After
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import javax.inject.Inject

@HiltAndroidTest
class ScreenshotTest {
    @get:Rule
    var hiltRule = HiltAndroidRule(this)

    private lateinit var scenario: ActivityScenario<MainActivity>

    @Inject
    lateinit var accountDao: AccountDao

    @Before
    fun before() {
        hiltRule.inject()

        val host = "10.0.2.2"
        for (account in listOf(
            Account(
                name = "Nextcloud",
                url = "http://${host}:8003/remote.php/dav/files/admin",
                username = SecretString("admin"),
                password = SecretString("admin")
            ),
            Account(
                name = "Nginx",
                url = "http://${host}:8002"
            ),
            Account(
                name = "Apache",
                url = "http://${host}:8004"
            )
        )) {
            accountDao.insert(account)
        }

        scenario = ActivityScenario.launch(MainActivity::class.java)
    }

    @After
    fun after() {
        scenario.close()
    }

    @Test
    fun takeShowcaseScreenshots() {
        onView(withId(R.id.rvAccounts))
            .check(matches(hasDescendant(withText("Nextcloud"))))

        takeScreenshot().writeToTestStorage("screenshot1")

        onView(withId(R.id.rvAccounts)).perform(
            RecyclerViewActions.actionOnItem<AccountAdapter.Holder>(
                hasDescendant(withText("Nextcloud")),
                longClick()
            )
        )
        onView(withId(R.id.action_edit)).perform(click())

        takeScreenshot().writeToTestStorage("screenshot2")

        onView(isRoot()).perform(pressBack())

        onView(withId(R.id.rvAccounts)).perform(
            RecyclerViewActions.actionOnItem<AccountAdapter.Holder>(
                hasDescendant(withText("Nextcloud")),
                click()
            )
        )

        val dev = UiDevice.getInstance(InstrumentationRegistry.getInstrumentation())
        dev.wait(Until.findObject(By.descContains("List view")), 1000)?.click()

        takeScreenshot().writeToTestStorage("screenshot3")

        dev.wait(Until.findObject(By.descContains("Show roots")), 1000).click()
        dev.wait(Until.findObject(By.res("com.google.android.documentsui", "drawer_roots")), 1000)

        takeScreenshot().writeToTestStorage("screenshot4")
    }
}
