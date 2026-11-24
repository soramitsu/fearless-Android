package jp.co.soramitsu.app.root.domain

import android.content.Context
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import java.io.File
import java.nio.file.Files
import jp.co.soramitsu.common.data.FileProviderImpl
import jp.co.soramitsu.common.data.storage.Preferences
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Test

/** The cleanups as the app graph builds them from the application context. */
class CleanupConstructionTest {

    @Test
    fun sharedExportsAreClearedFromTheApplicationCache() {
        val cache = Files.createTempDirectory("cache").toFile()
        val shared = File(cache, FileProviderImpl.INTERNAL_SHARE_DIRECTORY).apply { mkdirs() }
        File(shared, "wallet.json").writeText("{}")
        val context = mockk<Context> { every { cacheDir } returns cache }
        assertEquals(1, SharedExportCleanup(context).runOnce())
        assertFalse(File(shared, "wallet.json").exists())
        cache.deleteRecursively()
    }

    @Test
    fun withoutACacheDirectoryThereIsNothingToDelete() {
        assertEquals(0, SharedExportCleanup { null }.runOnce())
    }

    @Test
    fun soraCardLeftoversUseTheApplicationFilesAndAFailureWaitsForTheNextLaunch(): Unit = runBlocking {
        val files = Files.createTempDirectory("files").toFile()
        val preferences = mockk<Preferences>(relaxed = true) {
            every { getBoolean(SoraCardLeftoverCleanup.PREFS_SORA_CARD_LEFTOVERS_REMOVED, false) } returns false
            every { contains(any()) } returns false
        }
        val context = mockk<Context> { every { filesDir } returns files }
        SoraCardLeftoverCleanup(context, preferences).start(this).join()
        verify { preferences.putBoolean(SoraCardLeftoverCleanup.PREFS_SORA_CARD_LEFTOVERS_REMOVED, true) }

        val broken = mockk<Preferences> { every { getBoolean(any(), any()) } throws IllegalStateException("prefs unavailable") }
        SoraCardLeftoverCleanup({ files }, broken).start(this).join()
        verify(exactly = 0) { broken.putBoolean(any(), any()) }
        files.deleteRecursively()
    }
}
