package me.alexbakker.webdav

import android.content.Context
import androidx.documentfile.provider.DocumentFile
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import dagger.hilt.android.testing.HiltAndroidRule
import dagger.hilt.android.testing.HiltAndroidTest
import me.alexbakker.webdav.data.Account
import me.alexbakker.webdav.data.AccountDao
import me.alexbakker.webdav.data.CacheDao
import me.alexbakker.webdav.data.CacheEntry
import me.alexbakker.webdav.provider.WebDavProvider
import org.junit.Assert
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import java.nio.file.Path
import java.nio.file.Paths
import java.security.SecureRandom
import java.util.*
import javax.inject.Inject

@RunWith(AndroidJUnit4::class)
@HiltAndroidTest
class WebDavTest {
    @get:Rule
    var hiltRule = HiltAndroidRule(this)

    @Inject
    lateinit var accountDao: AccountDao

    @Inject
    lateinit var cacheDao: CacheDao

    private lateinit var account: Account
    private lateinit var context: Context

    private val fileNames = arrayOf("1.bin", "2.bin", "3.bin")

    @Before
    fun init() {
        hiltRule.inject()
        context = InstrumentationRegistry.getInstrumentation().targetContext

        account = Account(
            name = "Test",
            url = "http://10.0.2.2:8000",
            username = "test",
            password = "test"
        )
        account.id = accountDao.insert(account)
    }

    @Test
    fun createAndReadRandomFile() {
        val dir = getRootDir()
        val fileName = "${UUID.randomUUID()}.bin"
        val file = dir.createFile("application/octet-stream", fileName)
        Assert.assertNotNull("createFile(file=$fileName) failed", file)

        val bytes = ByteArray(1_000_000)
        SecureRandom().nextBytes(bytes)

        val outStream = context.contentResolver.openOutputStream(file!!.uri)
        Assert.assertNotNull("openOutputStream(uri=${file.uri}) failed", outStream)
        outStream.use { it!!.write(bytes) }

        // TODO: find out why this seems to be race-y
        Thread.sleep(500)

        val inStream = context.contentResolver.openInputStream(file.uri)
        Assert.assertNotNull("openInputStream(uri=${file.uri}) failed", inStream)
        val readBytes = inStream.use { it!!.readBytes() }
        Assert.assertArrayEquals(bytes, readBytes)
    }

    @Test
    fun readFileAndVerifyCache() {
        val path = Paths.get("/${fileNames[0]}")
        val uri = WebDavProvider.buildDocumentUri(account, path)

        val inStream = context.contentResolver.openInputStream(uri)
        Assert.assertNotNull("openInputStream(uri=${uri}) failed", inStream)
        assertCacheEntryStatus(account, path, CacheEntry.Status.PENDING)
        val readBytes = inStream.use { it!!.readBytes() }

        assertCacheEntryStatus(account, path, CacheEntry.Status.DONE)
        val inStreamCache = context.contentResolver.openInputStream(uri)
        Assert.assertNotNull("openInputStream(uri=${uri}) failed", inStreamCache)
        val readCacheBytes = inStreamCache.use { it!!.readBytes() }

        Assert.assertArrayEquals("Read bytes and cached bytes are not equal", readBytes, readCacheBytes)
    }

    @Test
    fun listDirectoryContents() {
        val dir = getRootDir()
        val files = dir.listFiles()
        Assert.assertNotNull("listFiles() failed", files)

        for (name in fileNames) {
            Assert.assertNotNull("File $name not found in listing", files.find { it.name == name })
        }
    }

    private fun getRootDir(): DocumentFile {
        val uri = WebDavProvider.buildTreeDocumentUri(WebDavProvider.buildDocumentId(account, account.rootPath))
        val dir = DocumentFile.fromTreeUri(context, uri)
        return dir!!
    }

    private fun assertCacheEntryStatus(account: Account, path: Path, status: CacheEntry.Status) {
        val cacheEntry = cacheDao.getByPath(account.id, path.toString())!!
        Assert.assertEquals("Unexpected cache entry status", status, cacheEntry.status)
    }
}
