package jp.co.soramitsu.app.root.domain

import android.content.SharedPreferences
import java.io.File
import jp.co.soramitsu.common.data.storage.Preferences
import jp.co.soramitsu.common.data.storage.PreferencesImpl
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeFalse
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.mockito.kotlin.doThrow
import org.mockito.kotlin.mock

class SoraCardLeftoverCleanupTest {

    @get:Rule
    val temporaryFolder = TemporaryFolder()

    private lateinit var appDataDir: File
    private lateinit var filesDir: File
    private lateinit var datastoreDir: File
    private lateinit var sharedPreferences: MapSharedPreferences
    private lateinit var cleanup: SoraCardLeftoverCleanup

    @Before
    fun setUp() {
        appDataDir = temporaryFolder.newFolder("jp.co.soramitsu.fearless")
        filesDir = File(appDataDir, "files").apply { mkdirs() }
        datastoreDir = File(filesDir, "datastore")
        sharedPreferences = MapSharedPreferences()
        cleanup = SoraCardLeftoverCleanup({ filesDir }, PreferencesImpl(sharedPreferences))
    }

    @After
    fun tearDown() {
        datastoreDir.setWritable(true)
    }

    @Test
    fun `upgrade from a SORA Card build removes exactly the listed leftovers`() {
        val untouchedPreferences = walletPreferences() + mapOf(
            "prefs_sora_card_hidden_sessions_count" to 2,
            "prefs_sora_card_progress_v2" to 1
        )
        sharedPreferences.seed(
            untouchedPreferences + mapOf(
                "prefs_sora_card_buy_xor_visibility" to false,
                "prefs_sora_card_progress" to 3
            )
        )
        writeFile(datastoreDir, "sora_card_data_store.preferences_pb", "sora card session")
        writeFile(datastoreDir, "sora_card_data_store.preferences_pb.tmp", "scratch")
        val untouchedFiles = listOf(
            writeFile(datastoreDir, "backup_preferences.preferences_pb", "drive file ids"),
            writeFile(datastoreDir, "sora_card_data_store.preferences_pb.bak", "not the SDK file"),
            writeFile(filesDir, "sora_card_data_store.preferences_pb", "outside the datastore directory"),
            writeFile(File(appDataDir, "shared_prefs"), "key_alias.xml", "wrapped AES key")
        ).associateWith(File::readBytes)

        assertTrue(cleanup.runOnce())

        assertFalse(File(datastoreDir, "sora_card_data_store.preferences_pb").exists())
        assertFalse(File(datastoreDir, "sora_card_data_store.preferences_pb.tmp").exists())
        assertTrue(datastoreDir.isDirectory)
        untouchedFiles.forEach { (file, bytes) -> assertArrayEquals(file.path, bytes, file.readBytes()) }
        assertEquals(untouchedPreferences + completionMarker(), sharedPreferences.all)
    }

    @Test
    fun `upgrade from 4_2_0 without leftovers only records completion`() {
        sharedPreferences.seed(walletPreferences())
        val backup = writeFile(datastoreDir, "backup_preferences.preferences_pb", "drive file ids")

        assertTrue(cleanup.runOnce())

        assertArrayEquals("drive file ids".toByteArray(), backup.readBytes())
        assertEquals(walletPreferences() + completionMarker(), sharedPreferences.all)
        assertEquals(1, sharedPreferences.commitCount)
    }

    @Test
    fun `fresh install without a datastore directory completes without creating it`() {
        assertTrue(cleanup.runOnce())

        assertFalse(datastoreDir.exists())
        assertEquals(completionMarker(), sharedPreferences.all)
    }

    @Test
    fun `cleanup runs only once`() {
        assertTrue(cleanup.runOnce())
        val commitsAfterFirstRun = sharedPreferences.commitCount
        sharedPreferences.seed(mapOf("prefs_sora_card_progress" to 1))
        val sameName = writeFile(datastoreDir, "sora_card_data_store.preferences_pb", "written later")

        assertTrue(cleanup.runOnce())

        assertTrue(sameName.exists())
        assertEquals(1, sharedPreferences.all["prefs_sora_card_progress"])
        assertEquals(commitsAfterFirstRun, sharedPreferences.commitCount)
    }

    @Test
    fun `a file that cannot be deleted is retried on the next launch`() {
        assumeFalse("root ignores directory permissions", System.getProperty("user.name") == "root")
        sharedPreferences.seed(walletPreferences() + ("prefs_sora_card_progress" to 2))
        val leftover = writeFile(datastoreDir, "sora_card_data_store.preferences_pb", "sora card session")
        assertTrue(datastoreDir.setWritable(false))

        assertFalse(cleanup.runOnce())

        assertTrue(leftover.exists())
        assertEquals(walletPreferences(), sharedPreferences.all)

        assertTrue(datastoreDir.setWritable(true))
        assertTrue(cleanup.runOnce())

        assertFalse(leftover.exists())
        assertEquals(walletPreferences() + completionMarker(), sharedPreferences.all)
    }

    @Test
    fun `a directory at the SDK file path is left alone`() {
        val directory = File(datastoreDir, "sora_card_data_store.preferences_pb")
        val child = writeFile(directory, "unexpected", "not ours")

        assertTrue(cleanup.runOnce())

        assertTrue(directory.isDirectory)
        assertArrayEquals("not ours".toByteArray(), child.readBytes())
    }

    @Test
    fun `start never lets a storage failure escape`() = runTest {
        val failingPreferences = mock<Preferences> {
            on { getBoolean("prefs_sora_card_leftovers_removed", false) } doThrow IllegalStateException("disk")
        }

        val job = SoraCardLeftoverCleanup({ filesDir }, failingPreferences).start(this)
        job.join()

        assertFalse(job.isCancelled)
    }

    private fun walletPreferences(): Map<String, Any> = mapOf(
        "SUBSTRATE_SECRETS:1" to "0xciphertext",
        "ACCESS_SECRETS:1" to "0xciphertext",
        "TON_SECRETS:2" to "0xciphertext",
        "selected_language" to "ja"
    )

    private fun completionMarker() = mapOf("prefs_sora_card_leftovers_removed" to true)

    private fun writeFile(directory: File, name: String, content: String): File {
        directory.mkdirs()
        return File(directory, name).apply { writeText(content) }
    }

    /** SharedPreferences held in memory, so the real [PreferencesImpl] can be used. */
    private class MapSharedPreferences : SharedPreferences {
        private val values = linkedMapOf<String, Any?>()
        var commitCount = 0
            private set

        fun seed(entries: Map<String, Any?>) {
            values.putAll(entries)
        }

        override fun getAll(): Map<String, *> = values.toMap()
        override fun getString(key: String, defValue: String?) = values[key] as? String ?: defValue

        @Suppress("UNCHECKED_CAST")
        override fun getStringSet(key: String, defValues: Set<String>?) =
            values[key] as? Set<String> ?: defValues

        override fun getInt(key: String, defValue: Int) = values[key] as? Int ?: defValue
        override fun getLong(key: String, defValue: Long) = values[key] as? Long ?: defValue
        override fun getFloat(key: String, defValue: Float) = values[key] as? Float ?: defValue
        override fun getBoolean(key: String, defValue: Boolean) = values[key] as? Boolean ?: defValue
        override fun contains(key: String) = key in values
        override fun edit(): SharedPreferences.Editor = Editor()
        override fun registerOnSharedPreferenceChangeListener(
            listener: SharedPreferences.OnSharedPreferenceChangeListener
        ) = Unit

        override fun unregisterOnSharedPreferenceChangeListener(
            listener: SharedPreferences.OnSharedPreferenceChangeListener
        ) = Unit

        private inner class Editor : SharedPreferences.Editor {
            private val puts = linkedMapOf<String, Any?>()
            private val removals = mutableSetOf<String>()
            private var clear = false

            override fun putString(key: String, value: String?) = put(key, value)
            override fun putStringSet(key: String, values: Set<String>?) = put(key, values)
            override fun putInt(key: String, value: Int) = put(key, value)
            override fun putLong(key: String, value: Long) = put(key, value)
            override fun putFloat(key: String, value: Float) = put(key, value)
            override fun putBoolean(key: String, value: Boolean) = put(key, value)

            override fun remove(key: String): SharedPreferences.Editor {
                removals += key
                return this
            }

            override fun clear(): SharedPreferences.Editor {
                clear = true
                return this
            }

            private fun put(key: String, value: Any?): SharedPreferences.Editor {
                puts[key] = value
                return this
            }

            override fun commit(): Boolean {
                if (clear) values.clear()
                removals.forEach(values::remove)
                values.putAll(puts)
                commitCount++
                return true
            }

            override fun apply() {
                commit()
            }
        }
    }
}
