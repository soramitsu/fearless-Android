package jp.co.soramitsu.common.data.network.solana

import jp.co.soramitsu.common.model.UniversalWalletRegistry

interface SolanaIndexerClient {
    suspend fun serviceInfo(baseUrl: String? = null): SolanaIndexerServiceInfo
    suspend fun verifyServiceInfo(baseUrl: String? = null): SolanaIndexerServiceInfo
    suspend fun balances(wallet: String, baseUrl: String? = null): SolanaWalletBalancesResponse
    suspend fun assets(wallet: String, baseUrl: String? = null): SolanaWalletAssetsResponse
    suspend fun state(wallet: String, baseUrl: String? = null): SolanaWalletStateResponse

    suspend fun transactions(
        wallet: String,
        baseUrl: String? = null,
        before: String? = null,
        limit: Int = SolanaIndexerRoutes.DEFAULT_LIMIT
    ): SolanaWalletTransactionsResponse

    suspend fun tokenMetadata(mint: String, baseUrl: String? = null): SolanaTokenMetadata

    suspend fun tokenMetadataBatch(
        mints: List<String>,
        baseUrl: String? = null
    ): SolanaTokenMetadataBatchResponse
}

class SolanaIndexerIdentityException(
    val serviceInfo: SolanaIndexerServiceInfo
) : IllegalStateException("unexpected_solana_indexer_service_info")

class RetrofitSolanaIndexerClient(
    private val api: SolanaIndexerApi,
    private val defaultBaseUrl: String = UniversalWalletRegistry.SOLANA_INDEXER_BASE_URL
) : SolanaIndexerClient {

    override suspend fun serviceInfo(baseUrl: String?): SolanaIndexerServiceInfo {
        return api.getServiceInfo(SolanaIndexerRoutes.serviceInfoUrl(resolveBaseUrl(baseUrl)))
    }

    override suspend fun verifyServiceInfo(baseUrl: String?): SolanaIndexerServiceInfo {
        val info = serviceInfo(baseUrl)
        if (!info.isExpectedSiServiceInfo()) {
            throw SolanaIndexerIdentityException(info)
        }
        return info
    }

    override suspend fun balances(wallet: String, baseUrl: String?): SolanaWalletBalancesResponse {
        return api.getBalances(SolanaIndexerRoutes.balancesUrl(wallet, resolveBaseUrl(baseUrl)))
    }

    override suspend fun assets(wallet: String, baseUrl: String?): SolanaWalletAssetsResponse {
        return api.getAssets(SolanaIndexerRoutes.assetsUrl(wallet, resolveBaseUrl(baseUrl)))
    }

    override suspend fun state(wallet: String, baseUrl: String?): SolanaWalletStateResponse {
        return api.getState(SolanaIndexerRoutes.stateUrl(wallet, resolveBaseUrl(baseUrl)))
    }

    override suspend fun transactions(
        wallet: String,
        baseUrl: String?,
        before: String?,
        limit: Int
    ): SolanaWalletTransactionsResponse {
        return api.getTransactions(
            SolanaIndexerRoutes.transactionsUrl(
                wallet = wallet,
                baseUrl = resolveBaseUrl(baseUrl),
                before = before,
                limit = limit
            )
        )
    }

    override suspend fun tokenMetadata(mint: String, baseUrl: String?): SolanaTokenMetadata {
        return api.getTokenMetadata(SolanaIndexerRoutes.tokenMetadataUrl(mint, resolveBaseUrl(baseUrl)))
    }

    override suspend fun tokenMetadataBatch(
        mints: List<String>,
        baseUrl: String?
    ): SolanaTokenMetadataBatchResponse {
        return api.getTokenMetadataBatch(
            SolanaIndexerRoutes.tokenMetadataBatchUrl(resolveBaseUrl(baseUrl)),
            SolanaIndexerRoutes.tokenMetadataBatchRequest(mints)
        )
    }

    private fun resolveBaseUrl(baseUrl: String?): String {
        return baseUrl ?: defaultBaseUrl
    }
}
