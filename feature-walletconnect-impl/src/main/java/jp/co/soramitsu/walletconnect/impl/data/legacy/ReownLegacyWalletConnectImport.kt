package jp.co.soramitsu.walletconnect.impl.data.legacy

import android.content.Context
import android.util.Log
import jp.co.soramitsu.common.BuildConfig
import jp.co.soramitsu.walletconnect.impl.data.EncryptedWalletConnectStateStorage
import jp.co.soramitsu.walletconnect.impl.data.LegacyWalletConnectImport
import jp.co.soramitsu.walletconnect.impl.data.LegacyWalletConnectImport.Outcome
import jp.co.soramitsu.walletconnect.impl.protocol.WalletConnectStateStorage

/**
 * The starter's legacy import for Fearless 4.3.1 and earlier: carries the Reown SDK's sessions, pairings and
 * relay identity over to the in-repo client ([ReownSessionImporter]). Only a result that lets the SDK's files
 * go reports [Outcome.IMPORTED] or [Outcome.NOTHING_TO_IMPORT]; every other one, an incomplete import
 * included, reports [Outcome.FAILED], so the cleanup runs only once everything usable is saved.
 */
internal class ReownLegacyWalletConnectImport(private val importer: ReownSessionImporter) : LegacyWalletConnectImport {

    override suspend fun importInto(storage: WalletConnectStateStorage): Outcome =
        importer.importInto(storage).toOutcome()

    companion object {
        private const val TAG = "WalletConnect"
        private const val MILLIS_PER_SECOND = 1_000L

        /** The import over this device's Reown files, counting attempts per app version beside the client's state. */
        fun create(context: Context): ReownLegacyWalletConnectImport = ReownLegacyWalletConnectImport(
            ReownSessionImporter(
                source = AndroidReownLegacySource(context),
                attempts = PreferencesReownImportAttempts(
                    context.getSharedPreferences(EncryptedWalletConnectStateStorage.FILE_NAME, Context.MODE_PRIVATE),
                    BuildConfig.VERSION_NAME
                ),
                nowSeconds = { System.currentTimeMillis() / MILLIS_PER_SECOND },
                log = { line -> Log.i(TAG, line) }
            )
        )
    }
}

/** What the starter does with a result: only one that lets the SDK's files go may say so. */
internal fun ReownImportResult.toOutcome(): Outcome = when {
    this == ReownImportResult.NothingToImport -> Outcome.NOTHING_TO_IMPORT
    allowsCleanup -> Outcome.IMPORTED
    else -> Outcome.FAILED
}
