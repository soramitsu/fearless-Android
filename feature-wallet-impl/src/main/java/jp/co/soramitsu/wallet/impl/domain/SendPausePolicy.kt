package jp.co.soramitsu.wallet.impl.domain

import jp.co.soramitsu.common.base.errors.TitledException
import jp.co.soramitsu.runtime.ext.isUniversalWalletBitcoin
import jp.co.soramitsu.runtime.ext.isUniversalWalletSolana
import jp.co.soramitsu.runtime.multiNetwork.chain.model.Chain

/**
 * Emergency kill switch for app-owned send paths. Bitcoin and Solana sends ship enabled; only an
 * explicit, remembered remote veto pauses them. Receive and balances are never affected.
 */
class SendPausePolicy(
    private val bitcoinSendEnabled: () -> Boolean,
    private val solanaSendEnabled: () -> Boolean,
    private val pausedErrorFactory: (Chain) -> SendPausedException
) {
    fun isPaused(chain: Chain): Boolean = when {
        chain.isUniversalWalletBitcoin() -> !bitcoinSendEnabled()
        chain.isUniversalWalletSolana() -> !solanaSendEnabled()
        else -> false
    }

    /** A localized, user-facing error explaining that sends on [chain] are paused. */
    fun pausedError(chain: Chain): SendPausedException = pausedErrorFactory(chain)

    companion object {
        /** No veto applies; for callers and tests that have no remote config. */
        val Unrestricted = SendPausePolicy(
            bitcoinSendEnabled = { true },
            solanaSendEnabled = { true },
            pausedErrorFactory = { error("Sends are never paused by the unrestricted policy") }
        )
    }
}

class SendPausedException(title: String, message: String) : TitledException(title, message)
