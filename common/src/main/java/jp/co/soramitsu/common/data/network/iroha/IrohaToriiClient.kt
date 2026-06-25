package jp.co.soramitsu.common.data.network.iroha

import jp.co.soramitsu.common.model.UniversalWalletRegistry
import jp.co.soramitsu.common.utils.IrohaAddressCodec
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.RequestBody.Companion.toRequestBody

interface IrohaToriiClient {
    suspend fun health(baseUrl: String? = null): String

    suspend fun accounts(
        baseUrl: String? = null,
        limit: Int? = null,
        offset: Long? = null,
        countMode: IrohaToriiRoutes.CountMode? = null
    ): IrohaAccountListResponse

    suspend fun account(
        accountId: String,
        baseUrl: String? = null,
        network: UniversalWalletRegistry.IrohaNetwork = UniversalWalletRegistry.taira
    ): IrohaAccountListItem

    suspend fun accountAssets(
        accountId: String,
        baseUrl: String? = null,
        limit: Int? = null,
        offset: Long? = null,
        countMode: IrohaToriiRoutes.CountMode? = null,
        asset: String? = null,
        scope: String? = null,
        network: UniversalWalletRegistry.IrohaNetwork = UniversalWalletRegistry.taira
    ): IrohaAccountAssetListResponse

    suspend fun assetDefinitions(baseUrl: String? = null): IrohaAssetDefinitionListResponse

    suspend fun submitTransaction(
        noritoBytes: ByteArray,
        baseUrl: String? = null
    ): IrohaTransactionSubmissionReceipt

    suspend fun transactionStatus(
        hash: String,
        baseUrl: String? = null,
        scope: IrohaToriiRoutes.TransactionStatusScope = IrohaToriiRoutes.TransactionStatusScope.Auto
    ): IrohaPipelineTransactionStatusResponse

    suspend fun mcpCapabilities(
        network: UniversalWalletRegistry.IrohaNetwork = UniversalWalletRegistry.taira,
        baseUrl: String? = null
    ): Map<String, Any?>

    suspend fun mcpJsonRpc(
        request: IrohaMcpJsonRpcRequest,
        network: UniversalWalletRegistry.IrohaNetwork = UniversalWalletRegistry.taira,
        baseUrl: String? = null
    ): IrohaMcpJsonRpcResponse
}

class RetrofitIrohaToriiClient(
    private val api: IrohaToriiApi,
    private val defaultNetwork: UniversalWalletRegistry.IrohaNetwork = UniversalWalletRegistry.taira
) : IrohaToriiClient {

    override suspend fun health(baseUrl: String?): String {
        return api.health(IrohaToriiRoutes.healthUrl(resolveBaseUrl(baseUrl)))
    }

    override suspend fun accounts(
        baseUrl: String?,
        limit: Int?,
        offset: Long?,
        countMode: IrohaToriiRoutes.CountMode?
    ): IrohaAccountListResponse {
        return api.accounts(
            IrohaToriiRoutes.accountsUrl(
                baseUrl = resolveBaseUrl(baseUrl),
                limit = limit,
                offset = offset,
                countMode = countMode
            )
        )
    }

    override suspend fun account(
        accountId: String,
        baseUrl: String?,
        network: UniversalWalletRegistry.IrohaNetwork
    ): IrohaAccountListItem {
        return api.account(
            IrohaToriiRoutes.accountUrl(
                normalizeWalletAccountId(accountId, network),
                resolveBaseUrl(baseUrl, network)
            )
        )
    }

    override suspend fun accountAssets(
        accountId: String,
        baseUrl: String?,
        limit: Int?,
        offset: Long?,
        countMode: IrohaToriiRoutes.CountMode?,
        asset: String?,
        scope: String?,
        network: UniversalWalletRegistry.IrohaNetwork
    ): IrohaAccountAssetListResponse {
        return api.accountAssets(
            IrohaToriiRoutes.accountAssetsUrl(
                accountId = normalizeWalletAccountId(accountId, network),
                baseUrl = resolveBaseUrl(baseUrl, network),
                limit = limit,
                offset = offset,
                countMode = countMode,
                asset = asset,
                scope = scope
            )
        )
    }

    override suspend fun assetDefinitions(baseUrl: String?): IrohaAssetDefinitionListResponse {
        return api.assetDefinitions(IrohaToriiRoutes.assetDefinitionsUrl(resolveBaseUrl(baseUrl)))
    }

    override suspend fun submitTransaction(
        noritoBytes: ByteArray,
        baseUrl: String?
    ): IrohaTransactionSubmissionReceipt {
        return api.submitTransaction(
            IrohaToriiRoutes.submitTransactionUrl(resolveBaseUrl(baseUrl)),
            noritoBytes.toRequestBody(NORITO_MEDIA_TYPE)
        )
    }

    override suspend fun transactionStatus(
        hash: String,
        baseUrl: String?,
        scope: IrohaToriiRoutes.TransactionStatusScope
    ): IrohaPipelineTransactionStatusResponse {
        return api.transactionStatus(
            IrohaToriiRoutes.transactionStatusUrl(
                hash = hash,
                baseUrl = resolveBaseUrl(baseUrl),
                scope = scope
            )
        )
    }

    override suspend fun mcpCapabilities(
        network: UniversalWalletRegistry.IrohaNetwork,
        baseUrl: String?
    ): Map<String, Any?> {
        return api.mcpCapabilities(IrohaToriiRoutes.mcpUrl(network, resolveBaseUrl(baseUrl, network)))
    }

    override suspend fun mcpJsonRpc(
        request: IrohaMcpJsonRpcRequest,
        network: UniversalWalletRegistry.IrohaNetwork,
        baseUrl: String?
    ): IrohaMcpJsonRpcResponse {
        return api.mcpJsonRpc(IrohaToriiRoutes.mcpUrl(network, resolveBaseUrl(baseUrl, network)), request)
    }

    private fun normalizeWalletAccountId(
        accountId: String,
        network: UniversalWalletRegistry.IrohaNetwork
    ): String {
        return IrohaAddressCodec.parse(accountId, network.chainDiscriminant).i105
    }

    private fun resolveBaseUrl(
        baseUrl: String?,
        network: UniversalWalletRegistry.IrohaNetwork = defaultNetwork
    ): String {
        return baseUrl ?: IrohaToriiRoutes.requireToriiBaseUrl(network)
    }

    private companion object {
        val NORITO_MEDIA_TYPE = "application/x-norito".toMediaType()
    }
}
