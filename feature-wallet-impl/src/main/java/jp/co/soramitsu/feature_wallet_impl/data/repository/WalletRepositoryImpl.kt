package jp.co.soramitsu.feature_wallet_impl.data.repository

import jp.co.soramitsu.common.data.model.CursorPage
import jp.co.soramitsu.common.data.network.HttpExceptionHandler
import jp.co.soramitsu.common.data.network.coingecko.PriceInfo
import jp.co.soramitsu.common.utils.mapList
import jp.co.soramitsu.core_db.dao.OperationDao
import jp.co.soramitsu.core_db.dao.PhishingAddressDao
import jp.co.soramitsu.core_db.model.AssetUpdateItem
import jp.co.soramitsu.core_db.model.AssetWithToken
import jp.co.soramitsu.core_db.model.OperationLocal
import jp.co.soramitsu.core_db.model.PhishingAddressLocal
import jp.co.soramitsu.fearless_utils.extensions.toHexString
import jp.co.soramitsu.fearless_utils.runtime.AccountId
import jp.co.soramitsu.fearless_utils.runtime.extrinsic.ExtrinsicBuilder
import jp.co.soramitsu.fearless_utils.ss58.SS58Encoder.toAccountId
import jp.co.soramitsu.feature_account_api.domain.model.MetaAccount
import jp.co.soramitsu.feature_wallet_api.data.cache.AssetCache
import jp.co.soramitsu.feature_wallet_api.domain.interfaces.TransactionFilter
import jp.co.soramitsu.feature_wallet_api.domain.interfaces.WalletConstants
import jp.co.soramitsu.feature_wallet_api.domain.interfaces.WalletRepository
import jp.co.soramitsu.feature_wallet_api.domain.model.Asset
import jp.co.soramitsu.feature_wallet_api.domain.model.Asset.Companion.createEmpty
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
import jp.co.soramitsu.feature_wallet_impl.data.network.subquery.HistoryNotSupportedException
import jp.co.soramitsu.feature_wallet_impl.data.network.subquery.SubQueryOperationsApi
import jp.co.soramitsu.feature_wallet_impl.data.storage.TransferCursorStorage
import jp.co.soramitsu.runtime.ext.accountIdOf
import jp.co.soramitsu.runtime.ext.addressOf
import jp.co.soramitsu.runtime.ext.utilityAsset
import jp.co.soramitsu.runtime.multiNetwork.ChainRegistry
import jp.co.soramitsu.runtime.multiNetwork.chain.model.Chain
import jp.co.soramitsu.runtime.multiNetwork.chain.model.Chain.ExternalApi.Section.Type
import jp.co.soramitsu.runtime.multiNetwork.chain.model.ChainId
import jp.co.soramitsu.runtime.multiNetwork.chain.model.isOrml
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.mapLatest
import kotlinx.coroutines.flow.mapNotNull
import kotlinx.coroutines.withContext
import java.math.BigDecimal
import java.math.BigInteger

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

    override fun assetsFlow(metaId: Long, chainAccounts: List<MetaAccount.ChainAccount>): Flow<List<Asset>> {
        return combine(
            chainRegistry.chainsById,
            assetCache.observeAssets(metaId)
        ) { chainsById, assetsLocal ->

            val chainAssets = chainAccounts.flatMap { chainAccount ->
                chainAccount.chain?.assets?.map { createEmpty(it, chainAccount.metaId, chainAccount.accountName) }.orEmpty()
            }

            val updatedAssets = assetsLocal.mapNotNull { asset ->
                mapAssetLocalToAsset(chainsById, asset)
            }

            val assetsByChain: List<Asset> = chainRegistry.currentChains.firstOrNull().orEmpty()
                .flatMap { chain -> chain.assets.map { createEmpty(it, metaId) } }

            val notUpdatedAssets = assetsByChain.filter {
                it.token.configuration.chainToSymbol !in updatedAssets.map { it.token.configuration.chainToSymbol }
            }

            updatedAssets + notUpdatedAssets
        }
    }

    override suspend fun getAssets(metaId: Long): List<Asset> = withContext(Dispatchers.Default) {
        val chainsById = chainRegistry.chainsById.first()
        val assetsLocal = assetCache.getAssets(metaId)

        assetsLocal.mapNotNull {
            mapAssetLocalToAsset(chainsById, it)
        }
    }

    private fun mapAssetLocalToAsset(
        chainsById: Map<ChainId, Chain>,
        assetLocal: AssetWithToken
    ): Asset? {
        val chainAsset = try {
            chainsById.getValue(assetLocal.asset.chainId).assetsBySymbol.getValue(assetLocal.token.symbol)
        } catch (e: Exception) {
            return null
        }

        return mapAssetLocalToAsset(assetLocal, chainAsset)
    }

    override suspend fun syncAssetsRates() {
        // TODO FLW-1147 - coingecko integration
        val chains = chainRegistry.currentChains.first()
        chains.forEach { chain ->
            val asset = chain.utilityAsset
            asset.priceId?.let {
                val priceStats = getAssetPriceCoingecko(it)

                updateAssetRates(asset.symbol, priceStats)
            }
        }
    }

    override fun assetFlow(accountId: AccountId, chainAsset: Chain.Asset): Flow<Asset> {
        return assetCache.observeAsset(accountId, chainAsset.chainId, chainAsset.symbol)
            .mapNotNull { it }
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
            val historyUrl = chain.externalApi?.history?.url
            if (historyUrl == null || chain.externalApi?.history?.type != Type.SUBQUERY) {
                throw HistoryNotSupportedException()
            }
            val requestRewards = chainAsset.staking != Chain.Asset.StakingType.UNSUPPORTED
            val response = walletOperationsApi.getOperationsHistory(
                url = historyUrl,
                SubqueryHistoryRequest(
                    accountAddress = chain.addressOf(accountId),
                    pageSize,
                    cursor,
                    filters,
                    requestRewards
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

    override suspend fun getTransferFee(
        chain: Chain,
        transfer: Transfer,
        additional: (suspend ExtrinsicBuilder.() -> Unit)?,
        batchAll: Boolean
    ): Fee {
        val fee = substrateSource.getTransferFee(chain, transfer, additional, batchAll)

        return mapFeeRemoteToFee(fee, transfer)
    }

    override suspend fun performTransfer(
        accountId: AccountId,
        chain: Chain,
        transfer: Transfer,
        fee: BigDecimal,
        additional: (suspend ExtrinsicBuilder.() -> Unit)?,
        batchAll: Boolean
    ) {
        val operationHash = substrateSource.performTransfer(accountId, chain, transfer, additional, batchAll)
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
        transfer: Transfer,
        additional: (suspend ExtrinsicBuilder.() -> Unit)?,
        batchAll: Boolean
    ): TransferValidityStatus {
        val feeResponse = getTransferFee(chain, transfer, additional, batchAll)

        val chainAsset = transfer.chainAsset

        val totalRecipientBalanceInPlanks = when {
            chain.id.isOrml() -> {
                val symbol = chain.utilityAsset.symbol
                val ormlTokensAccountData = substrateSource.getOrmlTokensAccountData(chain.id, symbol, chain.accountIdOf(transfer.recipient))
                ormlTokensAccountData.totalBalance
            }
            else -> {
                val recipientInfo = substrateSource.getAccountInfo(chain.id, chain.accountIdOf(transfer.recipient))
                recipientInfo.totalBalance
            }
        }

        val totalRecipientBalance = chainAsset.amountFromPlanks(totalRecipientBalanceInPlanks)

        val assetLocal = assetCache.getAsset(accountId, chainAsset.chainId, chainAsset.symbol)!!
        val asset = mapAssetLocalToAsset(assetLocal, chainAsset)

        val existentialDepositInPlanks = kotlin.runCatching { walletConstants.existentialDeposit(chain.id) }.getOrDefault(BigInteger.ZERO)
        val existentialDeposit = chainAsset.amountFromPlanks(existentialDepositInPlanks)

        return transfer.validityStatus(asset.transferable, asset.total, feeResponse.feeAmount, totalRecipientBalance, existentialDeposit)
    }

    // TODO adapt for ethereum chains
    override suspend fun updatePhishingAddresses() = withContext(Dispatchers.Default) {
        val accountIds = phishingApi.getPhishingAddresses().values.flatten()
            .map { it.toAccountId().toHexString(withPrefix = true) }

        val phishingAddressesLocal = accountIds.map(::PhishingAddressLocal)

        phishingAddressDao.clearTable()
        phishingAddressDao.insert(phishingAddressesLocal)
    }

    // TODO adapt for ethereum chains
    override suspend fun isAccountIdFromPhishingList(accountId: AccountId) = withContext(Dispatchers.Default) {
        val phishingAddresses = phishingAddressDao.getAllAddresses()

        phishingAddresses.contains(accountId.toHexString(withPrefix = true))
    }

    override suspend fun getAccountFreeBalance(chainId: ChainId, accountId: AccountId) = when {
        chainId.isOrml() -> {
            val assetSymbol = chainRegistry.getChain(chainId).utilityAsset.symbol
            substrateSource.getOrmlTokensAccountData(chainId, assetSymbol, accountId).free
        }
        else -> substrateSource.getAccountInfo(chainId, accountId).data.free
    }

    override suspend fun updateAssets(newItems: List<AssetUpdateItem>) {
        assetCache.updateAsset(newItems)
    }

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
