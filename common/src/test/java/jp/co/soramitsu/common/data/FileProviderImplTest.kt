package jp.co.soramitsu.common.data

import android.content.Context
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.mockito.kotlin.doReturn
import org.mockito.kotlin.mock
import java.io.File

class FileProviderImplTest {

    @get:Rule
    val folder = TemporaryFolder()

    @Test
    fun `shared export lives in its own internal cache directory`() = runBlocking {
        val cacheDir = folder.newFolder("cache")
        val externalCacheDir = folder.newFolder("external-cache")
        val provider = FileProviderImpl(context(cacheDir, externalCacheDir))

        val file = provider.getFileInInternalShareStorage("5GrwvaEF.json")

        val exportDirectory = File(cacheDir, FileProviderImpl.INTERNAL_SHARE_DIRECTORY)
        assertEquals(File(exportDirectory, "5GrwvaEF.json"), file)
        assertTrue(exportDirectory.isDirectory)
        assertTrue(externalCacheDir.list().orEmpty().isEmpty())
    }

    @Test
    fun `shared export name cannot point outside its directory`() {
        val provider = FileProviderImpl(
            context(folder.newFolder("cache"), folder.newFolder("external-cache"))
        )

        listOf("", ".", "..", "../wallet.json", "nested/wallet.json", "/tmp/wallet.json")
            .forEach { name ->
                assertThrows(name, IllegalArgumentException::class.java) {
                    runBlocking { provider.getFileInInternalShareStorage(name) }
                }
            }
    }

    @Test
    fun `existing cache locations are unchanged`() = runBlocking {
        val cacheDir = folder.newFolder("cache")
        val externalCacheDir = folder.newFolder("external-cache")
        val provider = FileProviderImpl(context(cacheDir, externalCacheDir))

        assertEquals(
            File(cacheDir, "runtime.json"),
            provider.getFileInInternalCacheStorage("runtime.json")
        )
        assertEquals(
            File(externalCacheDir, "address.png"),
            provider.getFileInExternalCacheStorage("address.png")
        )
    }

    private fun context(cacheDir: File, externalCacheDir: File): Context = mock {
        on { this.cacheDir } doReturn cacheDir
        on { this.externalCacheDir } doReturn externalCacheDir
    }
}
