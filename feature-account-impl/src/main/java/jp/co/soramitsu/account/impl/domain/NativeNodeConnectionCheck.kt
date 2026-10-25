package jp.co.soramitsu.account.impl.domain

import jp.co.soramitsu.common.data.network.solana.RetrofitSolanaRpcClient
import jp.co.soramitsu.common.data.network.solana.SolanaRpcApi
import jp.co.soramitsu.common.data.network.solana.SolanaRpcRoutes
import javax.inject.Inject

/** Read-only cluster check of exactly the endpoint the user is adding or editing. */
class NativeNodeConnectionCheck @Inject constructor(private val api: SolanaRpcApi) {
    suspend fun checkSolana(endpoint: String, expectedGenesisHash: String) {
        val url = SolanaRpcRoutes.normalizeRpcUrl(endpoint)
        RetrofitSolanaRpcClient(api, url, fallbackRpcUrls = emptyList(), expectedGenesisHash = expectedGenesisHash).verifyCluster()
    }
}
