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
    private val dirNames = arrayOf("a")
    private val subDirNames = arrayOf("a", "b", "c")

    @Before
    fun init() {
        hiltRule.inject()
        context = InstrumentationRegistry.getInstrumentation().targetContext

        account = Account(
            name = "Test",
            url = "http://10.0.2.2:8001",
            username = "test",
            password = "test"
        )
        account.id = accountDao.insert(account)
    }

    @Test
    fun createAndReadRandomFile() {
        val dir = getTreeFile(account.rootPath)
        createAndVerifyRandomFile(dir)
    }

    @Test
    fun createDirectoryAndChildFile() {
        val rootDir = getTreeFile(account.rootPath)
        val dirName = "${UUID.randomUUID()}"
        val dir = rootDir.createDirectory(dirName)
        Assert.assertNotNull("createDirectory(dirName=$dirName) failed", dir)

        createAndVerifyRandomFile(dir!!)
    }

    @Test
    fun deleteFile() {
        val path = Paths.get("/${dirNames[0]}/${fileNames[0]}")
        val file = getFile(path)
        Assert.assertTrue("Unable to delete file: ${file.uri}", file.delete())
        Assert.assertFalse("Deleted file still exists: ${file.uri}", file.exists())
    }

    @Test
    fun deleteDirectory() {
        val path = Paths.get("/${dirNames[0]}/${subDirNames[1]}")
        val dir = getTreeFile(path)
        Assert.assertTrue("Unable to delete directory: ${dir.uri}", dir.delete())
        Assert.assertFalse("Deleted directory still exists: ${dir.uri}", dir.exists())
    }

    @Test
    fun listDirectoryContents() {
        listDirectoryContents(getTreeFile(account.rootPath))
    }

    @Test
    fun listChildDirectoryContents() {
        val path = Paths.get("/${dirNames[0]}/${subDirNames[0]}")
        listDirectoryContents(getTreeFile(path), checkDirs = false)
    }

    @Test
    fun readRootFileAndVerifyCache() {
        val path = Paths.get("/${fileNames[0]}")
        readFileAndVerifyCache(path)
    }

    @Test
    fun readChildFileAndVerifyCache() {
        val path = Paths.get("/${dirNames[0]}/${subDirNames[0]}/${fileNames[0]}")
        readFileAndVerifyCache(path)
    }

    @Test
    fun renameFile() {
        val newName = "4.bin"
        val path = Paths.get("/${dirNames[0]}/${subDirNames[2]}/${fileNames[0]}")

        val file = getTreeFile(path)
        Assert.assertTrue("Unable to rename file: ${file.uri}", file.renameTo(newName))
        Assert.assertFalse("Old filename is still valid: ${file.uri}", file.exists())

        val newPath = path.parent.resolve(newName)
        val newFile = getFile(newPath)
        Assert.assertEquals("Unexpected filename for: ${file.uri}", newFile.name, newName)
    }

    private fun listDirectoryContents(rootDir: DocumentFile, checkDirs: Boolean = true) {
        val files = rootDir.listFiles()
        Assert.assertNotNull("listFiles() failed", files)

        verifyFilesPresent(files)
        if (checkDirs) {
            verifyDirectoriesPresent(files)
        }
    }

    private fun verifyFilesPresent(files: Array<DocumentFile>) {
        for (name in fileNames) {
            val file = files.find { it.name == name }
            Assert.assertNotNull("File '$name' not found in listing", file)
            Assert.assertTrue("Not a file: ${file!!.uri}", file.isFile)
        }
    }

    private fun verifyDirectoriesPresent(files: Array<DocumentFile>) {
        for (name in dirNames) {
            val dir = files.find { it.name == name }
            Assert.assertNotNull("Directory '$name' not found in listing", dir)
            Assert.assertTrue("Not a directory: ${dir!!.uri}", dir.isDirectory)
        }
    }

    private fun readFileAndVerifyCache(path: Path) {
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

    private fun createAndVerifyRandomFile(dir: DocumentFile) {
        val fileName = "${UUID.randomUUID()}.bin"
        val file = dir.createFile("application/octet-stream", fileName)
        Assert.assertNotNull("createFile(fileName=$fileName) failed", file)

        val bytes = generateRandomBytes()
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

    private fun getTreeFile(path: Path): DocumentFile {
        val id = WebDavProvider.buildDocumentId(account, path)
        val uri = WebDavProvider.buildTreeDocumentUri(id)
        return DocumentFile.fromTreeUri(context, uri)!!
    }

    private fun getFile(path: Path): DocumentFile {
        val uri = WebDavProvider.buildDocumentUri(account, path)
        return DocumentFile.fromSingleUri(context, uri)!!
    }

    private fun assertCacheEntryStatus(account: Account, path: Path, status: CacheEntry.Status) {
        val cacheEntry = cacheDao.getByPath(account.id, path.toString())!!
        Assert.assertEquals("Unexpected cache entry status", status, cacheEntry.status)
    }

    private fun generateRandomBytes(n: Int = 1_000_000): ByteArray {
        val bytes = ByteArray(n)
        SecureRandom().nextBytes(bytes)
        return bytes
    }
}
