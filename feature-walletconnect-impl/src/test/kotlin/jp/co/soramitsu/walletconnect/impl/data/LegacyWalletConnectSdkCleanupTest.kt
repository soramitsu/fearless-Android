package jp.co.soramitsu.walletconnect.impl.data

import android.content.SharedPreferences
import io.mockk.every
import io.mockk.mockk
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File

class LegacyWalletConnectSdkCleanupTest {
    @get:Rule
    val folder = TemporaryFolder()

    private val markers = mutableMapOf<String, Boolean>()
    private val deletedDatabases = mutableListOf<String>()
    private val deletedPreferences = mutableListOf<String>()
    private val aliases = mutableSetOf("wc_keystore_key", "_wc_db_key_", "key_alias", "key_alias_v2")
    private var failPreferences = false

    private val keyStore = object : LegacyKeyStore {
        override fun containsAlias(alias: String) = alias in aliases

        override fun deleteEntry(alias: String) {
            aliases -= alias
        }
    }

    private val cleanup = LegacyWalletConnectSdkCleanup(
        markers = fakeMarkers(),
        deleteDatabaseFiles = { deletedDatabases += it },
        deleteSharedPreferences = { name ->
            if (failPreferences) error("disk error")
            deletedPreferences += name
        },
        keyStore = keyStore
    )

    @Test
    fun `deletes exactly the old SDK's databases, preferences and keystore keys`() {
        assertTrue(cleanup.run())

        assertEquals(listOf("WalletConnectAndroidCore.db", "WalletConnectV2.db", "WalletConnectV2_notify.db"), deletedDatabases)
        assertEquals(listOf("wc_key_store", "db_key_store"), deletedPreferences)
        assertEquals(setOf("key_alias", "key_alias_v2"), aliases)
    }

    @Test
    fun `never touches wallet storage`() {
        cleanup.run()

        val touched = deletedDatabases + deletedPreferences
        listOf("app.db", "fearless_prefs", "key_alias", "walletconnect_v2").forEach { assertFalse(it, it in touched) }
    }

    @Test
    fun `runs once`() {
        cleanup.run()
        deletedDatabases.clear()

        assertTrue(cleanup.run())
        assertTrue(deletedDatabases.isEmpty())
    }

    @Test
    fun `a failed cleanup is retried on the next start`() {
        failPreferences = true
        assertFalse(cleanup.run())
        assertFalse(markers.getOrDefault(LegacyWalletConnectSdkCleanup.MARKER, false))

        failPreferences = false
        assertTrue(cleanup.run())
        assertTrue(markers.getValue(LegacyWalletConnectSdkCleanup.MARKER))
    }

    @Test
    fun `a database is deleted with its journal, wal and shm files and nothing next to it`() {
        val databases = folder.newFolder("databases")
        val sdkFiles = listOf("WalletConnectV2.db", "WalletConnectV2.db-journal", "WalletConnectV2.db-wal", "WalletConnectV2.db-shm")
        val walletFiles = listOf("app.db", "app.db-wal", "app.db-shm", "WalletConnectV2.dbx")
        (sdkFiles + walletFiles).forEach { File(databases, it).writeText("x") }

        LegacyWalletConnectSdkCleanup.deleteSqliteFiles(File(databases, "WalletConnectV2.db"))
        // Files that are already gone are fine: the cleanup can run again after a partial failure.
        LegacyWalletConnectSdkCleanup.deleteSqliteFiles(File(databases, "WalletConnectAndroidCore.db"))

        assertEquals(walletFiles.toSet(), databases.list().orEmpty().toSet())
    }

    @Test
    fun `a database file that cannot be deleted fails the cleanup`() {
        val databases = folder.newFolder("databases")
        // A non-empty directory under the database's name cannot be deleted as a file.
        File(databases, "WalletConnectV2.db").apply { mkdir() }.resolve("inner").writeText("x")

        assertThrows(IllegalStateException::class.java) {
            LegacyWalletConnectSdkCleanup.deleteSqliteFiles(File(databases, "WalletConnectV2.db"))
        }
    }

    @Test
    fun `the wallet database is not among the deleted databases`() {
        assertFalse("app.db" in LegacyWalletConnectSdkCleanup.LEGACY_DATABASES)
        assertTrue(LegacyWalletConnectSdkCleanup.LEGACY_DATABASES.all { it.startsWith("WalletConnect") })
    }

    private fun fakeMarkers(): SharedPreferences {
        val editor = mockk<SharedPreferences.Editor>()
        every { editor.putBoolean(any(), any()) } answers {
            markers[firstArg()] = secondArg()
            editor
        }
        every { editor.commit() } returns true
        return mockk {
            every { getBoolean(any(), any()) } answers { markers[firstArg()] ?: secondArg() }
            every { edit() } returns editor
        }
    }
}
