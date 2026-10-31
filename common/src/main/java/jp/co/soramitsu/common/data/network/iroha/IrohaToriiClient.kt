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
        cursor: String? = null
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
        cursor: String? = null,
        network: UniversalWalletRegistry.IrohaNetwork = UniversalWalletRegistry.taira
    ): IrohaAccountAssetListResponse

    /**
     * Account movements, newest first. A filtered page may be empty and still carry a
     * `next_cursor`; callers follow it until it is null.
     */
    suspend fun accountHistory(
        accountId: String,
        baseUrl: String? = null,
        limit: Int? = null,
        cursor: String? = null,
        filter: String? = null,
        network: UniversalWalletRegistry.IrohaNetwork = UniversalWalletRegistry.taira
    ): IrohaAccountHistoryPage

    suspend fun assetDefinitions(baseUrl: String? = null): IrohaAssetDefinitionListResponse

    /**
     * Paged asset-definition catalog. The default preserves source compatibility for custom
     * clients, while production clients override it so discovery can exhaust catalogs larger
     * than Torii's 500-item page limit.
     */
    suspend fun assetDefinitionsPage(
        baseUrl: String? = null,
        limit: Int? = null,
        cursor: String? = null
    ): IrohaAssetDefinitionListResponse = assetDefinitions(baseUrl)

    suspend fun submitTransaction(
        noritoBytes: ByteArray,
        baseUrl: String? = null
    ): IrohaTransactionSubmissionReceipt

    suspend fun transactionStatus(
        hash: String,
        baseUrl: String? = null,
        scope: IrohaToriiRoutes.TransactionStatusScope = IrohaToriiRoutes.TransactionStatusScope.Auto
    ): IrohaPipelineTransactionStatusResponse
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
        cursor: String?
    ): IrohaAccountListResponse {
        return api.accounts(
            IrohaToriiRoutes.accountsUrl(
                baseUrl = resolveBaseUrl(baseUrl),
                limit = limit,
                cursor = cursor
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
        cursor: String?,
        network: UniversalWalletRegistry.IrohaNetwork
    ): IrohaAccountAssetListResponse {
        return api.accountAssets(
            IrohaToriiRoutes.accountAssetsUrl(
                accountId = normalizeWalletAccountId(accountId, network),
                baseUrl = resolveBaseUrl(baseUrl, network),
                limit = limit,
                cursor = cursor
            )
        )
    }

    override suspend fun accountHistory(
        accountId: String,
        baseUrl: String?,
        limit: Int?,
        cursor: String?,
        filter: String?,
        network: UniversalWalletRegistry.IrohaNetwork
    ): IrohaAccountHistoryPage {
        return api.accountHistory(
            IrohaToriiRoutes.accountHistoryUrl(
                accountId = normalizeWalletAccountId(accountId, network),
                baseUrl = resolveBaseUrl(baseUrl, network),
                limit = limit,
                cursor = cursor,
                filter = filter
            )
        )
    }

    override suspend fun assetDefinitions(baseUrl: String?): IrohaAssetDefinitionListResponse {
        return api.assetDefinitions(IrohaToriiRoutes.assetDefinitionsUrl(resolveBaseUrl(baseUrl)))
    }

    override suspend fun assetDefinitionsPage(
        baseUrl: String?,
        limit: Int?,
        cursor: String?
    ): IrohaAssetDefinitionListResponse {
        return api.assetDefinitions(
            IrohaToriiRoutes.assetDefinitionsUrl(
                baseUrl = resolveBaseUrl(baseUrl),
                limit = limit,
                cursor = cursor
            )
        )
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
