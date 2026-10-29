package jp.co.soramitsu.wallet.impl.presentation.send

import jp.co.soramitsu.common.data.network.bitcoin.BitcoinIndexerRoutes
import jp.co.soramitsu.common.data.network.solana.SolanaPublicKeyCodec
import jp.co.soramitsu.common.model.UniversalWalletRegistry
import jp.co.soramitsu.runtime.ext.isUniversalWalletBitcoin
import jp.co.soramitsu.runtime.ext.isUniversalWalletSolana
import jp.co.soramitsu.runtime.ext.universalWalletBitcoinIndexerNetwork
import jp.co.soramitsu.runtime.multiNetwork.chain.model.Chain

/** Known network explorers remain usable when an optional transaction indexer is unavailable. */
object NativeSendExplorer {
    fun url(chain: Chain, value: String, transaction: Boolean): String? = when {
        chain.isUniversalWalletBitcoin() -> {
            if (transaction) require(Regex("^[0-9a-f]{64}$").matches(value)) else BitcoinIndexerRoutes.normalizeAddress(value,
                requireNotNull(chain.universalWalletBitcoinIndexerNetwork()))
            val network = if (chain.universalWalletBitcoinIndexerNetwork() == BitcoinIndexerRoutes.Network.Testnet) "testnet/" else ""
            "https://mempool.space/${network}${if (transaction) "tx" else "address"}/$value"
        }
        chain.isUniversalWalletSolana() -> {
            if (transaction) require(Regex("^[1-9A-HJ-NP-Za-km-z]{64,88}$").matches(value)) else SolanaPublicKeyCodec.decode(value)
            val cluster = when (chain.id) {
                UniversalWalletRegistry.solanaDevnet.id -> "?cluster=devnet"
                else -> ""
            }
            // Solscan, as the browser extension links it, so every app opens the same full history.
            "https://solscan.io/${if (transaction) "tx" else "account"}/$value$cluster"
        }
        else -> null
    }
}
