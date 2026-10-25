package jp.co.soramitsu.app.root.domain

import android.content.Context
import android.util.Log
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.File
import java.io.IOException
import java.nio.file.Files
import java.nio.file.LinkOption
import javax.inject.Inject
import javax.inject.Singleton
import jp.co.soramitsu.common.data.storage.Preferences
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

/**
 * Deletes what the removed SORA Card feature left on devices that ran a build before
 * versionCode 222.
 *
 * Exactly these items are removed, and nothing else:
 * - `files/datastore/sora_card_data_store.preferences_pb`, the only store owned by the
 *   android-sora-card 1.2.1-K2 SDK (`SoraCardDataStore`, which held the SORA Card session),
 *   plus the `.tmp` scratch file DataStore writes next to it;
 * - the app preference keys `prefs_sora_card_buy_xor_visibility` and `prefs_sora_card_progress`.
 *
 * Other `fearless_prefs` keys (wallet secrets live there), other files in `files/datastore`
 * (4.2.0's `backup_preferences.preferences_pb` is one) and Keystore entries are never touched.
 * The cleanup completes once; a file that cannot be deleted is retried on the next launch.
 */
@Singleton
class SoraCardLeftoverCleanup(
    private val filesDir: () -> File,
    private val preferences: Preferences
) {

    @Inject
    constructor(
        @ApplicationContext context: Context,
        preferences: Preferences
    ) : this({ context.filesDir }, preferences)

    fun start(scope: CoroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)): Job =
        scope.launch {
            try {
                runOnce()
            } catch (error: Exception) {
                // Leftovers are inert; a failed attempt is simply repeated on the next launch.
                runCatching { Log.d(TAG, "SORA Card leftover cleanup failed: $error") }
            }
        }

    /** Returns true once no SORA Card leftovers remain. */
    fun runOnce(): Boolean {
        if (preferences.getBoolean(PREFS_SORA_CARD_LEFTOVERS_REMOVED, false)) return true

        LEFTOVER_PREFERENCE_KEYS
            .filter(preferences::contains)
            .forEach(preferences::removeField)

        val datastoreDirectory = File(filesDir(), DATASTORE_DIRECTORY)
        val filesRemoved = LEFTOVER_DATASTORE_FILES
            .map { name -> deleteRegularFile(File(datastoreDirectory, name)) }
            .all { it }

        if (filesRemoved) {
            preferences.putBoolean(PREFS_SORA_CARD_LEFTOVERS_REMOVED, true)
        }
        return filesRemoved
    }

    private fun deleteRegularFile(file: File): Boolean {
        val path = file.toPath()
        // Only the SDK's own regular file is deleted; a directory or link at that path is left alone.
        if (!Files.isRegularFile(path, LinkOption.NOFOLLOW_LINKS)) return true
        return try {
            Files.deleteIfExists(path)
            true
        } catch (_: IOException) {
            false
        }
    }

    companion object {
        private const val TAG = "SoraCardLeftoverCleanup"

        internal const val PREFS_SORA_CARD_LEFTOVERS_REMOVED = "prefs_sora_card_leftovers_removed"

        internal val LEFTOVER_PREFERENCE_KEYS = listOf(
            "prefs_sora_card_buy_xor_visibility",
            "prefs_sora_card_progress"
        )

        internal const val DATASTORE_DIRECTORY = "datastore"

        internal val LEFTOVER_DATASTORE_FILES = listOf(
            "sora_card_data_store.preferences_pb",
            "sora_card_data_store.preferences_pb.tmp"
        )
    }
}
