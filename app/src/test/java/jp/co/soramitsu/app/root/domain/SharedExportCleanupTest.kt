package jp.co.soramitsu.app.root.domain

import java.io.File
import java.nio.file.Files
import jp.co.soramitsu.common.data.FileProviderImpl
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class SharedExportCleanupTest {
    @get:Rule val temp = TemporaryFolder()

    @Test fun `leftover keystore exports are deleted and nothing else`() {
        val cache = temp.newFolder("cache")
        val share = File(cache, FileProviderImpl.INTERNAL_SHARE_DIRECTORY).apply { mkdirs() }
        val leftover = File(share, "5Grw.json").apply { writeText("{\"encoded\":\"public fixture\"}") }
        val nested = File(share, "nested").apply { mkdirs() }
        val keep = File(cache, "image-cache.bin").apply { writeText("x") }

        assertEquals(1, SharedExportCleanup { cache }.runOnce())

        assertFalse(leftover.exists())
        assertTrue("Directories are not exports", nested.exists())
        assertTrue("Other cache files stay", keep.exists())
    }

    @Test fun `a symlink in the share directory is not followed`() {
        val cache = temp.newFolder("cache")
        val share = File(cache, FileProviderImpl.INTERNAL_SHARE_DIRECTORY).apply { mkdirs() }
        val outside = temp.newFile("outside.json").apply { writeText("keep") }
        Files.createSymbolicLink(File(share, "link.json").toPath(), outside.toPath())

        assertEquals(0, SharedExportCleanup { cache }.runOnce())
        assertTrue(outside.exists())
    }

    @Test fun `a missing cache or share directory is nothing to do`() {
        assertEquals(0, SharedExportCleanup { null }.runOnce())
        assertEquals(0, SharedExportCleanup { temp.newFolder("empty") }.runOnce())
    }
}
