package jp.co.soramitsu.walletconnect.impl.data.legacy

import android.content.SharedPreferences
import jp.co.soramitsu.walletconnect.impl.protocol.WalletConnectStateStorage
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/** What the removed Reown SDK left on the device, read without changing any of it. */
internal interface ReownLegacySource {
    /** Whether any of the SDK's databases or preference files is still there. */
    fun hasLegacyData(): Boolean

    /** The key chain (tag to hex value); empty when it is not there. Throws when it is there but unreadable now. */
    fun readKeyChain(): Map<String, String>

    /** The databases' passphrase, or null when the SDK never stored one. Throws when it is there but unreadable now. */
    fun readDatabasePassphrase(): ByteArray?

    /** A database file with its journal and log, or null when the file is not there. */
    fun readDatabase(name: String): LegacyDatabaseFiles?
}

/** Remembers across starts that the import finished, whether the SDK's files must stay, and failed attempts. */
internal interface ReownImportLedger {
    val isDone: Boolean

    /** The finished import left something usable out (over a limit), so the SDK's files are never deleted. */
    val keepsLegacyFiles: Boolean
    val failedAttempts: Int

    fun markDone(keepLegacyFiles: Boolean)

    fun recordFailedAttempt()
}

/** Why an import did not finish. Codes only: no topic, key or exception text ever goes into a result. */
internal enum class ReownImportFailure {
    /** The client's storage could not be read or written now (for example the wallet key is unavailable); retried. */
    STORAGE_UNAVAILABLE,

    /** The SDK's data could not be read or decrypted; retried on later starts, up to the attempt limit. */
    LEGACY_UNREADABLE,

    /** Attempts are used up; the SDK's files stay where they are and nothing more is tried. */
    GAVE_UP
}

internal sealed interface ReownImportResult {
    /** Whether the SDK's files may now be deleted: only once everything usable is saved in the client's state. */
    val allowsCleanup: Boolean

    /** An earlier start finished the import. */
    data class AlreadyImported(val keepsLegacyFiles: Boolean) : ReownImportResult {
        override val allowsCleanup: Boolean get() = !keepsLegacyFiles
    }

    /** The SDK left nothing on this device (a new install, or cleaned up before). */
    data object NothingToImport : ReownImportResult {
        override val allowsCleanup: Boolean get() = true
    }

    data class Imported(val summary: ReownImportSummary) : ReownImportResult {
        override val allowsCleanup: Boolean get() = summary.isComplete
    }

    /** Nothing was saved and the SDK's files stay, so a later start can try again. */
    data class Failed(val reason: ReownImportFailure) : ReownImportResult {
        override val allowsCleanup: Boolean get() = false
    }
}

/**
 * Carries the WalletConnect sessions and pairings of the removed Reown SDK over to the in-repo client on
 * the first start after the update, before the client loads its state. It only reads what the SDK left:
 * the databases are decrypted in memory, the preferences are opened only when complete, and nothing of
 * the SDK's is written, renamed or deleted here. Cleanup may follow only when everything usable is saved
 * ([ReownImportResult.allowsCleanup]). On any failure the SDK's files stay, the app starts normally, and a
 * later start tries again ([MAX_FAILED_ATTEMPTS] times at most); the worst case is that the user
 * reconnects their dApps.
 */
internal class ReownSessionImporter(
    private val source: ReownLegacySource,
    private val ledger: ReownImportLedger,
    private val nowSeconds: () -> Long,
    private val decoder: SqlCipherDatabaseDecoder = SqlCipherDatabaseDecoder(),
    private val reader: ReownLegacySnapshotReader = ReownLegacySnapshotReader()
) {

    /** Imports into [storage], the client's own storage; call before the client starts. */
    suspend fun importInto(storage: WalletConnectStateStorage): ReownImportResult = withContext(Dispatchers.IO) {
        importBlocking(storage)
    }

    internal fun importBlocking(storage: WalletConnectStateStorage): ReownImportResult {
        val precondition = when {
            ledger.isDone -> ReownImportResult.AlreadyImported(ledger.keepsLegacyFiles)
            !source.hasLegacyData() -> ReownImportResult.NothingToImport.also { ledger.markDone(keepLegacyFiles = false) }
            ledger.failedAttempts >= MAX_FAILED_ATTEMPTS -> ReownImportResult.Failed(ReownImportFailure.GAVE_UP)
            else -> null
        }
        if (precondition != null) return precondition
        val snapshot = readSnapshotOrNull()
        if (snapshot == null) {
            ledger.recordFailedAttempt()
            return ReownImportResult.Failed(ReownImportFailure.LEGACY_UNREADABLE)
        }
        val summary = mergeInto(storage, snapshot) ?: return ReownImportResult.Failed(ReownImportFailure.STORAGE_UNAVAILABLE)
        ledger.markDone(keepLegacyFiles = !summary.isComplete)
        return ReownImportResult.Imported(summary)
    }

    // Any failure here leaves the SDK's data as it was; the exception itself is dropped because Keystore,
    // Tink and parser messages may carry details of what was being read.
    private fun readSnapshotOrNull(): ReownLegacySnapshot? {
        var passphrase: ByteArray? = null
        return try {
            val keyChain = source.readKeyChain()
            passphrase = source.readDatabasePassphrase()
            val sign = source.readDatabase(ReownLegacySnapshotReader.SIGN_DATABASE)
            val core = source.readDatabase(ReownLegacySnapshotReader.CORE_DATABASE)
            val key = passphrase
            if (key == null && (sign != null || core != null)) {
                null // databases without their key cannot be read; they stay for a later attempt
            } else {
                val sources = ReownLegacySources(
                    keyChain = keyChain,
                    signDatabase = sign?.let { image(it, requireNotNull(key)) },
                    coreDatabase = core?.let { image(it, requireNotNull(key)) }
                )
                reader.read(sources)
            }
        } catch (ignored: Exception) {
            null
        } finally {
            passphrase?.fill(0)
        }
    }

    // A database rolled back to nothing holds nothing to import.
    private fun image(files: LegacyDatabaseFiles, passphrase: ByteArray): SqliteDatabaseImage? =
        decoder.decode(files, passphrase).takeIf { it.isNotEmpty() }?.let(::SqliteDatabaseImage)

    // Null when the client's storage cannot be read or written right now; nothing is then counted as an attempt.
    private fun mergeInto(
        storage: WalletConnectStateStorage,
        snapshot: ReownLegacySnapshot
    ): ReownImportSummary? = try {
        val current = WcStoredStateJson.decodeOrEmpty(storage.read())
        val (merged, summary) = ReownLegacyStateImport.merge(current, snapshot, nowSeconds())
        if (merged != current) storage.write(WcStoredStateJson.encode(merged))
        summary
    } catch (ignored: Exception) {
        null
    }

    companion object {
        const val MAX_FAILED_ATTEMPTS = 3
    }
}

/** The ledger in the client's own preferences file, beside the cleanup's marker. */
internal class PreferencesReownImportLedger(private val preferences: SharedPreferences) : ReownImportLedger {
    override val isDone: Boolean get() = preferences.getBoolean(DONE, false)

    override val keepsLegacyFiles: Boolean get() = preferences.getBoolean(KEEP_LEGACY_FILES, false)

    override val failedAttempts: Int get() = preferences.getInt(FAILED_ATTEMPTS, 0)

    override fun markDone(keepLegacyFiles: Boolean) {
        preferences.edit().putBoolean(KEEP_LEGACY_FILES, keepLegacyFiles).putBoolean(DONE, true).commit()
    }

    override fun recordFailedAttempt() {
        preferences.edit().putInt(FAILED_ATTEMPTS, failedAttempts + 1).commit()
    }

    companion object {
        const val DONE = "reown_import.done.v1"
        const val KEEP_LEGACY_FILES = "reown_import.keep_legacy_files.v1"
        const val FAILED_ATTEMPTS = "reown_import.failed_attempts.v1"
    }
}
