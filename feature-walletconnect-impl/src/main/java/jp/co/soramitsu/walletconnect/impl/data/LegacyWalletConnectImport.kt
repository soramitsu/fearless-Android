package jp.co.soramitsu.walletconnect.impl.data

import jp.co.soramitsu.walletconnect.impl.protocol.WalletConnectStateStorage

/**
 * Moves what the removed Reown SDK (4.3.1 and earlier) stored, such as sessions, pairings, the relay
 * identity and pending requests, into the client's storage before the client loads it.
 *
 * The contract [WalletConnectStarter] keeps:
 * - [importInto] runs once per app start on `Dispatchers.IO`, before the client starts, and the starter
 *   waits for it; nothing else writes [WalletConnectStateStorage] meanwhile. It receives the storage
 *   instance the client then loads.
 * - It merges into the stored state and never overwrites a topic that already exists.
 * - Its [Outcome] decides whether the old SDK's files may be deleted: only after [Outcome.IMPORTED]
 *   or [Outcome.NOTHING_TO_IMPORT]. A thrown exception counts as [Outcome.FAILED]; the client starts
 *   either way.
 *
 * Bind an implementation with `@Binds` in any `SingletonComponent` module (see
 * `LegacyWalletConnectImportModule`). Without one the starter uses [NotAttempted], which imports
 * nothing and keeps every legacy file.
 */
interface LegacyWalletConnectImport {
    suspend fun importInto(storage: WalletConnectStateStorage): Outcome

    enum class Outcome {
        /** The old SDK's state is now in the storage: its files may be deleted. */
        IMPORTED,

        /** The old SDK left nothing to import: its leftovers may be deleted. */
        NOTHING_TO_IMPORT,

        /**
         * The import did not complete or left something out (for example sessions over the client's
         * limits): every legacy file is kept.
         */
        FAILED,

        /** No import was tried: every legacy file is kept. */
        NOT_ATTEMPTED
    }

    /** The default binding: imports nothing, so the old SDK's files are kept. */
    object NotAttempted : LegacyWalletConnectImport {
        override suspend fun importInto(storage: WalletConnectStateStorage): Outcome = Outcome.NOT_ATTEMPTED
    }
}
