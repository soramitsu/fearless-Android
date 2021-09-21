package jp.co.soramitsu.feature_wallet_impl.data.repository

import jp.co.soramitsu.common.data.model.CursorPage
import jp.co.soramitsu.common.data.network.HttpExceptionHandler
import jp.co.soramitsu.common.data.network.coingecko.PriceInfo
import jp.co.soramitsu.common.utils.mapList
import jp.co.soramitsu.core_db.dao.OperationDao
import jp.co.soramitsu.core_db.dao.PhishingAddressDao
import jp.co.soramitsu.core_db.model.OperationLocal
import jp.co.soramitsu.core_db.model.PhishingAddressLocal
import jp.co.soramitsu.fearless_utils.extensions.toHexString
import jp.co.soramitsu.fearless_utils.runtime.AccountId
import jp.co.soramitsu.fearless_utils.ss58.SS58Encoder.toAccountId
import jp.co.soramitsu.feature_wallet_api.data.cache.AssetCache
import jp.co.soramitsu.feature_wallet_api.domain.interfaces.TransactionFilter
import jp.co.soramitsu.feature_wallet_api.domain.interfaces.WalletConstants
import jp.co.soramitsu.feature_wallet_api.domain.interfaces.WalletRepository
import jp.co.soramitsu.feature_wallet_api.domain.model.Asset
import jp.co.soramitsu.feature_wallet_api.domain.model.Fee
import jp.co.soramitsu.feature_wallet_api.domain.model.Operation
import jp.co.soramitsu.feature_wallet_api.domain.model.Transfer
import jp.co.soramitsu.feature_wallet_api.domain.model.TransferValidityStatus
import jp.co.soramitsu.feature_wallet_api.domain.model.amountFromPlanks
import jp.co.soramitsu.feature_wallet_api.domain.model.planksFromAmount
import jp.co.soramitsu.feature_wallet_impl.data.mappers.mapAssetLocalToAsset
import jp.co.soramitsu.feature_wallet_impl.data.mappers.mapFeeRemoteToFee
import jp.co.soramitsu.feature_wallet_impl.data.mappers.mapNodeToOperation
import jp.co.soramitsu.feature_wallet_impl.data.mappers.mapOperationLocalToOperation
import jp.co.soramitsu.feature_wallet_impl.data.mappers.mapOperationToOperationLocalDb
import jp.co.soramitsu.feature_wallet_impl.data.network.blockchain.SubstrateRemoteSource
import jp.co.soramitsu.feature_wallet_impl.data.network.coingecko.CoingeckoApi
import jp.co.soramitsu.feature_wallet_impl.data.network.model.request.SubqueryHistoryRequest
import jp.co.soramitsu.feature_wallet_impl.data.network.phishing.PhishingApi
import jp.co.soramitsu.feature_wallet_impl.data.network.subquery.SubQueryOperationsApi
import jp.co.soramitsu.feature_wallet_impl.data.storage.TransferCursorStorage
import jp.co.soramitsu.runtime.ext.accountIdOf
import jp.co.soramitsu.runtime.ext.addressOf
import jp.co.soramitsu.runtime.ext.utilityAsset
import jp.co.soramitsu.runtime.multiNetwork.ChainRegistry
import jp.co.soramitsu.runtime.multiNetwork.chain.model.Chain
import jp.co.soramitsu.runtime.multiNetwork.chain.model.ChainId
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.mapLatest
import kotlinx.coroutines.withContext
import java.math.BigDecimal

class WalletRepositoryImpl(
    private val substrateSource: SubstrateRemoteSource,
    private val operationDao: OperationDao,
    private val walletOperationsApi: SubQueryOperationsApi,
    private val httpExceptionHandler: HttpExceptionHandler,
    private val phishingApi: PhishingApi,
    private val assetCache: AssetCache,
    private val walletConstants: WalletConstants,
    private val phishingAddressDao: PhishingAddressDao,
    private val cursorStorage: TransferCursorStorage,
    private val coingeckoApi: CoingeckoApi,
    private val chainRegistry: ChainRegistry,
) : WalletRepository {

    override fun assetsFlow(accountId: AccountId): Flow<List<Asset>> {
        return combine(
            chainRegistry.chainsById,
            assetCache.observeAssets(accountId)
        ) { chainsById, assetsLocal ->
            assetsLocal.map { asset ->
                val chainAsset = chainsById.getValue(asset.asset.chainId).assetsBySymbol.getValue(asset.token.symbol)

                mapAssetLocalToAsset(asset, chainAsset = chainAsset)
            }
        }
    }

    override suspend fun syncAssetsRates() {
        val firstChain = chainRegistry.currentChains.first().first()
        val asset = firstChain.utilityAsset

        asset.priceId?.let {
            val priceStats = getAssetPriceCoingecko(it)

            updateAssetRates(asset.symbol, priceStats)
        }
    }

    override fun assetFlow(accountId: AccountId, chainAsset: Chain.Asset): Flow<Asset> {
        return assetCache.observeAsset(accountId, chainAsset.chainId, chainAsset.symbol)
            .map { mapAssetLocalToAsset(it, chainAsset) }
    }

    override suspend fun getAsset(accountId: AccountId, chainAsset: Chain.Asset): Asset? {
        val assetLocal = assetCache.getAsset(accountId, chainAsset.chainId, chainAsset.symbol)

        return assetLocal?.let { mapAssetLocalToAsset(it, chainAsset) }
    }

    override suspend fun syncOperationsFirstPage(
        pageSize: Int,
        filters: Set<TransactionFilter>,
        accountId: AccountId,
        chain: Chain,
        chainAsset: Chain.Asset
    ) {
        val accountAddress = chain.addressOf(accountId)
        val page = getOperations(pageSize, cursor = null, filters, accountId, chain, chainAsset)

        val elements = page.map { mapOperationToOperationLocalDb(it, chainAsset, OperationLocal.Source.SUBQUERY) }

        operationDao.insertFromSubquery(accountAddress, chain.id, chainAsset.id, elements)
        cursorStorage.saveCursor(chain.id, chainAsset.id, accountId, page.nextCursor)
    }

    override suspend fun getOperations(
        pageSize: Int,
        cursor: String?,
        filters: Set<TransactionFilter>,
        accountId: AccountId,
        chain: Chain,
        chainAsset: Chain.Asset,
    ): CursorPage<Operation> {
        return withContext(Dispatchers.Default) {
            val response = walletOperationsApi.getOperationsHistory(
                url = chain.externalApi!!.history!!.url, // TODO external api is optional
                SubqueryHistoryRequest(
                    accountAddress = chain.addressOf(accountId),
                    pageSize,
                    cursor,
                    filters
                )
            ).data.query

            val pageInfo = response.historyElements.pageInfo

            val operations = response.historyElements.nodes.map { mapNodeToOperation(it, chainAsset) }

            CursorPage(pageInfo.endCursor, operations)
        }
    }

    override fun operationsFirstPageFlow(
        accountId: AccountId,
        chain: Chain,
        chainAsset: Chain.Asset
    ): Flow<CursorPage<Operation>> {
        val accountAddress = chain.addressOf(accountId)

        return operationDao.observe(accountAddress, chain.id, chainAsset.id)
            .mapList {
                mapOperationLocalToOperation(it, chainAsset)
            }
            .mapLatest { operations ->
                val cursor = cursorStorage.awaitCursor(chain.id, chainAsset.id, accountId)

                CursorPage(cursor, operations)
            }
    }

    override suspend fun getContacts(
        accountId: AccountId,
        chain: Chain,
        query: String
    ): Set<String> {
        return operationDao.getContacts(query, chain.addressOf(accountId), chain.id).toSet()
    }

    override suspend fun getTransferFee(chain: Chain, transfer: Transfer): Fee {
        val fee = substrateSource.getTransferFee(chain, transfer)

        return mapFeeRemoteToFee(fee, transfer)
    }

    override suspend fun performTransfer(
        accountId: AccountId,
        chain: Chain,
        transfer: Transfer,
        fee: BigDecimal
    ) {
        val operationHash = substrateSource.performTransfer(accountId, chain, transfer)
        val accountAddress = chain.addressOf(accountId)

        val operation = createOperation(
            operationHash,
            transfer,
            accountAddress,
            fee,
            OperationLocal.Source.APP
        )

        operationDao.insert(operation)
    }

    override suspend fun checkTransferValidity(
        accountId: AccountId,
        chain: Chain,
        transfer: Transfer
    ): TransferValidityStatus {
        val feeResponse = getTransferFee(chain, transfer)

        val chainAsset = transfer.chainAsset

        val recipientInfo = substrateSource.getAccountInfo(chain.id, chain.accountIdOf(transfer.recipient))
        val totalRecipientBalanceInPlanks = recipientInfo.totalBalance
        val totalRecipientBalance = chainAsset.amountFromPlanks(totalRecipientBalanceInPlanks)

        val assetLocal = assetCache.getAsset(accountId, chainAsset.chainId, chainAsset.symbol)!!
        val asset = mapAssetLocalToAsset(assetLocal, chainAsset)

        val existentialDepositInPlanks = walletConstants.existentialDeposit(chain.id)
        val existentialDeposit = chainAsset.amountFromPlanks(existentialDepositInPlanks)

        return transfer.validityStatus(asset.transferable, asset.total, feeResponse.feeAmount, totalRecipientBalance, existentialDeposit)
    }

    override suspend fun updatePhishingAddresses() = withContext(Dispatchers.Default) {
        val accountIds = phishingApi.getPhishingAddresses().values.flatten()
            .map { it.toAccountId().toHexString(withPrefix = true) }

        val phishingAddressesLocal = accountIds.map(::PhishingAddressLocal)

        phishingAddressDao.clearTable()
        phishingAddressDao.insert(phishingAddressesLocal)
    }

    override suspend fun isAccountIdFromPhishingList(accountId: AccountId) = withContext(Dispatchers.Default) {
        val phishingAddresses = phishingAddressDao.getAllAddresses()

        phishingAddresses.contains(accountId.toHexString(withPrefix = true))
    }

    override suspend fun getAccountFreeBalance(chainId: ChainId, accountId: AccountId) =
        substrateSource.getAccountInfo(chainId, accountId).data.free

    private fun createOperation(
        hash: String,
        transfer: Transfer,
        senderAddress: String,
        fee: BigDecimal,
        source: OperationLocal.Source
    ) =
        OperationLocal.manualTransfer(
            hash = hash,
            address = senderAddress,
            chainAssetId = transfer.chainAsset.id,
            chainId = transfer.chainAsset.chainId,
            amount = transfer.amountInPlanks,
            senderAddress = senderAddress,
            receiverAddress = transfer.recipient,
            fee = transfer.chainAsset.planksFromAmount(fee),
            status = OperationLocal.Status.PENDING,
            source = source
        )

    private suspend fun updateAssetRates(
        symbol: String,
        priceStats: Map<String, PriceInfo>?,
    ) = assetCache.updateToken(symbol) { cached ->
        val priceStat = priceStats?.values?.first()

        val price = priceStat?.price
        val change = priceStat?.rateChange

        cached.copy(
            dollarRate = price,
            recentRateChange = change
        )
    }

    private suspend fun getAssetPriceCoingecko(priceId: String): Map<String, PriceInfo> {
        return apiCall { coingeckoApi.getAssetPrice(priceId, "usd", true) }
    }

    private suspend fun <T> apiCall(block: suspend () -> T): T = httpExceptionHandler.wrap(block)
}
