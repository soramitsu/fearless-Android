package jp.co.soramitsu.common.data.network.ton

import jp.co.soramitsu.common.model.UniversalWalletRegistry

interface TonIndexerClient {
    suspend fun health(baseUrl: String? = null): TonHealthStatus
    suspend fun contracts(baseUrl: String? = null): TonContractsResponse
    suspend fun serviceInfo(baseUrl: String? = null): TonIndexerServiceInfo
    suspend fun verifyServiceInfo(baseUrl: String? = null): TonIndexerServiceInfo
    suspend fun balance(address: String, baseUrl: String? = null): TonBalanceResponse
    suspend fun balances(address: String, baseUrl: String? = null): TonBalancesResponse
    suspend fun assets(address: String, baseUrl: String? = null): TonBalancesResponse
    suspend fun state(address: String, baseUrl: String? = null): TonAccountStateResponse

    suspend fun transactions(
        address: String,
        baseUrl: String? = null,
        page: Int = TonIndexerRoutes.DEFAULT_TX_PAGE,
        cursorLt: String? = null,
        cursorHash: String? = null
    ): TonTransactionsResponse

    suspend fun swaps(
        address: String,
        baseUrl: String? = null,
        limit: Int = TonIndexerRoutes.DEFAULT_SWAP_LIMIT,
        fromUtime: Long? = null,
        toUtime: Long? = null,
        payToken: String? = null,
        receiveToken: String? = null,
        executionType: TonIndexerRoutes.TonSwapExecutionType? = null,
        status: TonIndexerRoutes.TonSwapStatus? = null,
        includeReverse: Boolean? = null
    ): TonSwapsResponse

    suspend fun jettonTransferPayload(
        jetton: String,
        owner: String,
        baseUrl: String? = null
    ): TonJettonTransferPayloadResponse

    suspend fun runGetMethod(
        address: String,
        method: String,
        stack: List<List<Any?>> = emptyList(),
        baseUrl: String? = null
    ): TonRunGetMethodResponse

    suspend fun runGetMethods(
        calls: List<TonRunGetMethodRequest>,
        baseUrl: String? = null
    ): TonRunGetMethodsResponse
}

class TonIndexerIdentityException(
    val serviceInfo: TonIndexerServiceInfo
) : IllegalStateException("unexpected_ton_indexer_service_info")

class RetrofitTonIndexerClient(
    private val api: TonIndexerApi,
    private val defaultBaseUrl: String = UniversalWalletRegistry.TON_INDEXER_BASE_URL
) : TonIndexerClient {

    override suspend fun health(baseUrl: String?): TonHealthStatus {
        return api.getHealth(TonIndexerRoutes.healthUrl(resolveBaseUrl(baseUrl)))
    }

    override suspend fun contracts(baseUrl: String?): TonContractsResponse {
        return api.getContracts(TonIndexerRoutes.contractsUrl(resolveBaseUrl(baseUrl)))
    }

    override suspend fun serviceInfo(baseUrl: String?): TonIndexerServiceInfo {
        return api.getServiceInfo(TonIndexerRoutes.serviceInfoUrl(resolveBaseUrl(baseUrl)))
    }

    override suspend fun verifyServiceInfo(baseUrl: String?): TonIndexerServiceInfo {
        val info = serviceInfo(baseUrl)
        if (!info.isExpectedTiServiceInfo()) {
            throw TonIndexerIdentityException(info)
        }
        return info
    }

    override suspend fun balance(address: String, baseUrl: String?): TonBalanceResponse {
        return api.getBalance(TonIndexerRoutes.balanceUrl(address, resolveBaseUrl(baseUrl)))
    }

    override suspend fun balances(address: String, baseUrl: String?): TonBalancesResponse {
        return api.getBalances(TonIndexerRoutes.balancesUrl(address, resolveBaseUrl(baseUrl)))
    }

    override suspend fun assets(address: String, baseUrl: String?): TonBalancesResponse {
        return api.getAssets(TonIndexerRoutes.assetsUrl(address, resolveBaseUrl(baseUrl)))
    }

    override suspend fun state(address: String, baseUrl: String?): TonAccountStateResponse {
        return api.getState(TonIndexerRoutes.stateUrl(address, resolveBaseUrl(baseUrl)))
    }

    override suspend fun transactions(
        address: String,
        baseUrl: String?,
        page: Int,
        cursorLt: String?,
        cursorHash: String?
    ): TonTransactionsResponse {
        return api.getTransactions(
            TonIndexerRoutes.transactionsUrl(
                address = address,
                baseUrl = resolveBaseUrl(baseUrl),
                page = page,
                cursorLt = cursorLt,
                cursorHash = cursorHash
            )
        )
    }

    override suspend fun swaps(
        address: String,
        baseUrl: String?,
        limit: Int,
        fromUtime: Long?,
        toUtime: Long?,
        payToken: String?,
        receiveToken: String?,
        executionType: TonIndexerRoutes.TonSwapExecutionType?,
        status: TonIndexerRoutes.TonSwapStatus?,
        includeReverse: Boolean?
    ): TonSwapsResponse {
        return api.getSwaps(
            TonIndexerRoutes.swapsUrl(
                address = address,
                baseUrl = resolveBaseUrl(baseUrl),
                limit = limit,
                fromUtime = fromUtime,
                toUtime = toUtime,
                payToken = payToken,
                receiveToken = receiveToken,
                executionType = executionType,
                status = status,
                includeReverse = includeReverse
            )
        )
    }

    override suspend fun jettonTransferPayload(
        jetton: String,
        owner: String,
        baseUrl: String?
    ): TonJettonTransferPayloadResponse {
        return api.getJettonTransferPayload(
            TonIndexerRoutes.jettonTransferPayloadUrl(jetton, owner, resolveBaseUrl(baseUrl))
        )
    }

    override suspend fun runGetMethod(
        address: String,
        method: String,
        stack: List<List<Any?>>,
        baseUrl: String?
    ): TonRunGetMethodResponse {
        return api.runGetMethod(
            TonIndexerRoutes.runGetMethodUrl(resolveBaseUrl(baseUrl)),
            TonIndexerRoutes.runGetMethodRequest(address, method, stack)
        )
    }

    override suspend fun runGetMethods(
        calls: List<TonRunGetMethodRequest>,
        baseUrl: String?
    ): TonRunGetMethodsResponse {
        return api.runGetMethods(
            TonIndexerRoutes.runGetMethodsUrl(resolveBaseUrl(baseUrl)),
            TonIndexerRoutes.runGetMethodsRequest(calls)
        )
    }

    private fun resolveBaseUrl(baseUrl: String?): String {
        return baseUrl ?: defaultBaseUrl
    }
}
