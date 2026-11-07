package jp.co.soramitsu.walletconnect.impl.data.legacy

import android.content.SharedPreferences
import jp.co.soramitsu.walletconnect.impl.protocol.WalletConnectStateStorage
import jp.co.soramitsu.walletconnect.impl.protocol.WcStoredState
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

/**
 * Counts import attempts for this app version, outside the client's state so that it can be read even
 * when that state cannot. An attempt is counted before the SDK's data is read: an import that crashes the
 * app still uses one up, so it cannot become a crash loop. An app update starts the count again.
 */
internal interface ReownImportAttempts {
    fun count(): Int

    fun record()

    /** Gives back the attempt just recorded, when what failed was the client's storage, not the SDK's data. */
    fun refund()
}

/** Why an import did not finish. Codes only: no topic, key or exception text ever goes into a result. */
internal enum class ReownImportFailure {
    /** The client's storage could not be read or written now (for example the wallet key is unavailable); retried. */
    STORAGE_UNAVAILABLE,

    /** The SDK's data could not be read or decrypted; retried on later starts, up to the attempt limit. */
    LEGACY_UNREADABLE,

    /** Attempts are used up for this version; the SDK's files stay where they are. */
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
 * The import's mark in the client's own state. It is saved in the same single write as what it imported,
 * so the state never holds imported records without the mark, or the mark without them: an app killed
 * before that write imports again from nothing, and one killed after it never imports twice. It lives
 * among the state's `handled` entries, whose value is when an entry may be forgotten; the store drops
 * only entries whose time has passed, so these stay.
 */
internal object ReownImportMarker {
    const val IMPORTED = "legacy-import:reown"
    const val KEEP_LEGACY_FILES = "legacy-import:reown:keep-files"
    private const val NEVER = Long.MAX_VALUE

    /** Null when the import has not happened; otherwise whether the SDK's files must stay. */
    fun read(state: WcStoredState): Boolean? = if (IMPORTED in state.handled) KEEP_LEGACY_FILES in state.handled else null

    fun mark(state: WcStoredState, keepLegacyFiles: Boolean): WcStoredState {
        val marks = mapOf(IMPORTED to NEVER) + if (keepLegacyFiles) mapOf(KEEP_LEGACY_FILES to NEVER) else emptyMap()
        return state.copy(handled = state.handled + marks)
    }
}

/**
 * Carries the WalletConnect sessions and pairings of the removed Reown SDK over to the in-repo client on
 * the first start after the update, before the client loads its state. It only reads what the SDK left:
 * the databases are decrypted in memory, the preferences are opened only when complete, and nothing of
 * the SDK's is written, renamed or deleted here.
 * - The import and its [ReownImportMarker] are one atomic write of the client's state, and merging never
 *   replaces a topic the state has, so it is idempotent and safe against the app being killed at any point.
 * - Cleanup may follow only when everything usable is saved ([ReownImportResult.allowsCleanup]).
 * - It never throws for what it reads: on any failure the SDK's files stay, WalletConnect starts without
 *   the old sessions, the outcome is logged as codes, and a later start tries again, [MAX_ATTEMPTS] times
 *   per app version. The worst case is that the user reconnects their dApps.
 */
internal class ReownSessionImporter(
    private val source: ReownLegacySource,
    private val attempts: ReownImportAttempts,
    private val nowSeconds: () -> Long,
    private val log: (String) -> Unit = {},
    private val decoder: SqlCipherDatabaseDecoder = SqlCipherDatabaseDecoder(),
    private val reader: ReownLegacySnapshotReader = ReownLegacySnapshotReader()
) {

    /** Imports into [storage], the client's own storage; call before the client starts. */
    suspend fun importInto(storage: WalletConnectStateStorage): ReownImportResult = withContext(Dispatchers.IO) {
        importBlocking(storage).also { log(describe(it)) }
    }

    internal fun importBlocking(storage: WalletConnectStateStorage): ReownImportResult {
        val current = readState(storage)
        val marker = current?.let(ReownImportMarker::read)
        return when {
            current == null -> ReownImportResult.Failed(ReownImportFailure.STORAGE_UNAVAILABLE)
            marker != null -> ReownImportResult.AlreadyImported(keepsLegacyFiles = marker)
            !source.hasLegacyData() -> ReownImportResult.NothingToImport
            attempts.count() >= MAX_ATTEMPTS -> ReownImportResult.Failed(ReownImportFailure.GAVE_UP)
            else -> attempt(storage, current)
        }
    }

    private fun attempt(storage: WalletConnectStateStorage, current: WcStoredState): ReownImportResult {
        attempts.record()
        val snapshot = readSnapshotOrNull() ?: return ReownImportResult.Failed(ReownImportFailure.LEGACY_UNREADABLE)
        val (merged, summary) = ReownLegacyStateImport.merge(current, snapshot, nowSeconds())
        val saved = write(storage, ReownImportMarker.mark(merged, keepLegacyFiles = !summary.isComplete))
        if (!saved) attempts.refund()
        return if (saved) ReownImportResult.Imported(summary) else ReownImportResult.Failed(ReownImportFailure.STORAGE_UNAVAILABLE)
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

    // Null when the client's storage cannot be read now (for example its key is unavailable).
    private fun readState(storage: WalletConnectStateStorage): WcStoredState? = try {
        WcStoredStateJson.decodeOrEmpty(storage.read())
    } catch (ignored: Exception) {
        null
    }

    private fun write(storage: WalletConnectStateStorage, state: WcStoredState): Boolean = try {
        storage.write(WcStoredStateJson.encode(state))
        true
    } catch (ignored: Exception) {
        false
    }

    // Counts and reason codes only: never a topic, key, payload or exception text.
    private fun describe(result: ReownImportResult): String = when (result) {
        is ReownImportResult.AlreadyImported -> "Reown import: done earlier, cleanup ${allowed(result)}"
        ReownImportResult.NothingToImport -> "Reown import: nothing to import"
        is ReownImportResult.Imported -> with(result.summary) {
            "Reown import: $sessions sessions ($expiredSessions expired), $pairings pairings, identity $identity, " +
                "skipped $skipped, cleanup ${allowed(result)}"
        }
        is ReownImportResult.Failed ->
            "Reown import failed: ${result.reason}, attempt ${attempts.count()} of $MAX_ATTEMPTS; " +
                "the SDK's data stays and WalletConnect starts without it"
    }

    private fun allowed(result: ReownImportResult) = if (result.allowsCleanup) "allowed" else "withheld"

    companion object {
        const val MAX_ATTEMPTS = 3
    }
}

/** The attempt count in the client's own preferences file, beside the cleanup's marker, per app version. */
internal class PreferencesReownImportAttempts(
    private val preferences: SharedPreferences,
    appVersion: String
) : ReownImportAttempts {
    private val key = "$ATTEMPTS_PREFIX$appVersion"

    override fun count(): Int = preferences.getInt(key, 0)

    override fun record() {
        preferences.edit().putInt(key, count() + 1).commit()
    }

    override fun refund() {
        preferences.edit().putInt(key, (count() - 1).coerceAtLeast(0)).commit()
    }

    companion object {
        const val ATTEMPTS_PREFIX = "reown_import.attempts."
    }
}
