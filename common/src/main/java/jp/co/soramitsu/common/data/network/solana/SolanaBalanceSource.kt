package jp.co.soramitsu.common.data.network.solana

import jp.co.soramitsu.common.model.UniversalWalletRegistry

/** An authoritative native and token inventory; failed/partial reads must never become zero balances. */
interface SolanaBalanceSource {
    /** Reads one exact wallet and cluster. For RPC sources [baseUrl] is a JSON-RPC endpoint. */
    suspend fun balances(
        wallet: String,
        network: UniversalWalletRegistry.SolanaNetwork = UniversalWalletRegistry.solanaMainnet,
        baseUrl: String? = null,
        includeTokenMetadata: Boolean = true
    ): SolanaBalanceSyncResult
}
