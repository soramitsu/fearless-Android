package jp.co.soramitsu.wallet.impl.data.repository

import java.math.BigDecimal
import java.math.BigInteger
import jp.co.soramitsu.account.api.domain.model.MetaAccount
import jp.co.soramitsu.account.api.domain.model.accountId
import jp.co.soramitsu.common.data.model.CursorPage
import jp.co.soramitsu.common.data.network.HttpExceptionHandler
import jp.co.soramitsu.common.data.network.coingecko.CoingeckoApi
import jp.co.soramitsu.common.data.network.config.AppConfigRemote
import jp.co.soramitsu.common.data.network.config.RemoteConfigFetcher
import jp.co.soramitsu.common.domain.GetAvailableFiatCurrencies
import jp.co.soramitsu.common.mixin.api.UpdatesMixin
import jp.co.soramitsu.common.mixin.api.UpdatesProviderUi
import jp.co.soramitsu.common.utils.mapList
import jp.co.soramitsu.common.utils.orZero
import jp.co.soramitsu.coredb.dao.OperationDao
import jp.co.soramitsu.coredb.dao.PhishingAddressDao
import jp.co.soramitsu.coredb.dao.emptyAccountIdValue
import jp.co.soramitsu.coredb.model.AssetUpdateItem
import jp.co.soramitsu.coredb.model.AssetWithToken
import jp.co.soramitsu.coredb.model.OperationLocal
import jp.co.soramitsu.coredb.model.PhishingAddressLocal
import jp.co.soramitsu.fearless_utils.extensions.toHexString
import jp.co.soramitsu.fearless_utils.runtime.AccountId
import jp.co.soramitsu.fearless_utils.runtime.extrinsic.ExtrinsicBuilder
import jp.co.soramitsu.fearless_utils.ss58.SS58Encoder.toAccountId
import jp.co.soramitsu.runtime.ext.accountIdOf
import jp.co.soramitsu.runtime.ext.addressOf
import jp.co.soramitsu.runtime.ext.utilityAsset
import jp.co.soramitsu.runtime.multiNetwork.ChainRegistry
import jp.co.soramitsu.runtime.multiNetwork.chain.model.Chain
import jp.co.soramitsu.runtime.multiNetwork.chain.model.Chain.ExternalApi.Section.Type
import jp.co.soramitsu.runtime.multiNetwork.chain.model.ChainId
import jp.co.soramitsu.runtime.multiNetwork.chain.model.isOrml
import jp.co.soramitsu.wallet.api.data.cache.AssetCache
import jp.co.soramitsu.wallet.impl.data.mappers.mapAssetLocalToAsset
import jp.co.soramitsu.wallet.impl.data.mappers.mapFeeRemoteToFee
import jp.co.soramitsu.wallet.impl.data.mappers.mapNodeToOperation
import jp.co.soramitsu.wallet.impl.data.mappers.mapOperationLocalToOperation
import jp.co.soramitsu.wallet.impl.data.mappers.mapOperationToOperationLocalDb
import jp.co.soramitsu.wallet.impl.data.network.blockchain.SubstrateRemoteSource
import jp.co.soramitsu.wallet.impl.data.network.model.request.SubqueryHistoryRequest
import jp.co.soramitsu.wallet.impl.data.network.phishing.PhishingApi
import jp.co.soramitsu.wallet.impl.data.network.subquery.HistoryNotSupportedException
import jp.co.soramitsu.wallet.impl.data.network.subquery.SubQueryOperationsApi
import jp.co.soramitsu.wallet.impl.data.storage.TransferCursorStorage
import jp.co.soramitsu.wallet.impl.domain.interfaces.TransactionFilter
import jp.co.soramitsu.wallet.impl.domain.interfaces.WalletConstants
import jp.co.soramitsu.wallet.impl.domain.interfaces.WalletRepository
import jp.co.soramitsu.wallet.impl.domain.model.Asset
import jp.co.soramitsu.wallet.impl.domain.model.Asset.Companion.createEmpty
import jp.co.soramitsu.wallet.impl.domain.model.AssetWithStatus
import jp.co.soramitsu.wallet.impl.domain.model.Fee
import jp.co.soramitsu.wallet.impl.domain.model.Operation
import jp.co.soramitsu.wallet.impl.domain.model.Transfer
import jp.co.soramitsu.wallet.impl.domain.model.TransferValidityStatus
import jp.co.soramitsu.wallet.impl.domain.model.amountFromPlanks
import jp.co.soramitsu.wallet.impl.domain.model.planksFromAmount
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.flow.mapLatest
import kotlinx.coroutines.flow.mapNotNull
import kotlinx.coroutines.withContext

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
    private val availableFiatCurrencies: GetAvailableFiatCurrencies,
    private val updatesMixin: UpdatesMixin,
    private val remoteConfigFetcher: RemoteConfigFetcher
) : WalletRepository, UpdatesProviderUi by updatesMixin {

    override fun assetsFlow(meta: MetaAccount): Flow<List<AssetWithStatus>> {
        return combine(
            chainRegistry.chainsById,
            assetCache.observeAssets(meta.id)
        ) { chainsById, assetsLocal ->
            val chainAccounts = meta.chainAccounts.values.toList()
            val updatedAssets = assetsLocal.mapNotNull { asset ->
                mapAssetLocalToAsset(chainsById, asset)?.let {
                    val hasChainAccount = asset.asset.chainId in chainAccounts.mapNotNull { it.chain?.id }
                    AssetWithStatus(
                        asset = it,
                        hasAccount = !it.accountId.contentEquals(emptyAccountIdValue),
                        hasChainAccount = hasChainAccount
                    )
                }
            }

            val assetsByChain: List<AssetWithStatus> = chainRegistry.currentChains.firstOrNull().orEmpty()
                .flatMap { chain ->
                    chain.assets.map {
                        AssetWithStatus(
                            asset = createEmpty(
                                chainAsset = it,
                                metaId = meta.id,
                                accountId = meta.accountId(chain) ?: emptyAccountIdValue,
                                minSupportedVersion = chain.minSupportedVersion,
                                enabled = chain.nodes.isNotEmpty()
                            ),
                            hasAccount = !chain.isEthereumBased || meta.ethereumPublicKey != null,
                            hasChainAccount = chain.id in chainAccounts.mapNotNull { it.chain?.id }
                        )
                    }
                }

            val assetsByUniqueAccounts = chainAccounts
                .mapNotNull { chainAccount ->
                    createEmpty(chainAccount)?.let { asset ->
                        AssetWithStatus(
                            asset = asset,
                            hasAccount = true,
                            hasChainAccount = false
                        )
                    }
                }

            val notUpdatedAssetsByUniqueAccounts = assetsByUniqueAccounts.filter { unique ->
                !updatedAssets.any {
                    it.asset.token.configuration.chainToSymbol == unique.asset.token.configuration.chainToSymbol &&
                        it.asset.accountId.contentEquals(unique.asset.accountId)
                }
            }
            val notUpdatedAssets = assetsByChain.filter {
                it.asset.token.configuration.chainToSymbol !in updatedAssets.map { it.asset.token.configuration.chainToSymbol }
            }

            updatedAssets + notUpdatedAssetsByUniqueAccounts + notUpdatedAssets
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
        val (chain, chainAsset) = try {
            val chain = chainsById.getValue(assetLocal.asset.chainId)
            val asset = chain.assetsById.getValue(assetLocal.token.assetId)
            chain to asset
        } catch (e: Exception) {
            return null
        }

        return mapAssetLocalToAsset(assetLocal, chainAsset, chain.minSupportedVersion)
    }

    override suspend fun syncAssetsRates(currencyId: String) {
        val chains = chainRegistry.currentChains.first()
        val priceIds = chains.mapNotNull { it.utilityAsset.priceId }
        val priceStats = getAssetPriceCoingecko(*priceIds.toTypedArray(), currencyId = currencyId)

        val chainsWithAssetPrices = chains.filter { it.utilityAsset.priceId != null }

        val assetIds = chainsWithAssetPrices.map { it.utilityAsset.id }
        updatesMixin.startUpdateTokens(assetIds)

        chainsWithAssetPrices.forEach { chain ->
            val asset = chain.utilityAsset
            val stat = priceStats[chain.utilityAsset.priceId] ?: return@forEach
            val price = stat[currencyId]
            val changeKey = "${currencyId}_24h_change"
            val change = stat[changeKey]
            val fiatCurrency = availableFiatCurrencies[currencyId]
            asset.priceId?.let {
                updateAssetRates(asset.id, fiatCurrency?.symbol, price, change)
            }
        }
        updatesMixin.finishUpdateTokens(assetIds)
    }

    override fun assetFlow(metaId: Long, accountId: AccountId, chainAsset: Chain.Asset, minSupportedVersion: String?): Flow<Asset> {
        return assetCache.observeAsset(metaId, accountId, chainAsset.chainId, chainAsset.id)
            .mapNotNull { it }
            .mapNotNull { mapAssetLocalToAsset(it, chainAsset, minSupportedVersion) }
    }

    override suspend fun getAsset(metaId: Long, accountId: AccountId, chainAsset: Chain.Asset, minSupportedVersion: String?): Asset? {
        val assetLocal = assetCache.getAsset(metaId, accountId, chainAsset.chainId, chainAsset.id)

        return assetLocal?.let { mapAssetLocalToAsset(it, chainAsset, minSupportedVersion) }
    }

    override suspend fun updateAssetHidden(metaId: Long, accountId: AccountId, chainId: ChainId, assetSymbol: String, isHidden: Boolean) {
        val updateItems = assetCache.getAssets(metaId, accountId, chainId, assetSymbol).map { it.toAssetUpdateItem().copy(enabled = !isHidden) }
        assetCache.updateAsset(updateItems)
    }

    private fun AssetWithToken.toAssetUpdateItem() = AssetUpdateItem(
        metaId = asset.metaId,
        chainId = asset.chainId,
        accountId = asset.accountId,
        id = asset.id,
        sortIndex = asset.sortIndex,
        enabled = asset.enabled
    )

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
        chainAsset: Chain.Asset
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
        tip: BigInteger?,
        additional: (suspend ExtrinsicBuilder.() -> Unit)?,
        batchAll: Boolean
    ) {
        val operationHash = substrateSource.performTransfer(accountId, chain, transfer, tip, additional, batchAll)
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
        metaId: Long,
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

        val assetLocal = assetCache.getAsset(metaId, accountId, chainAsset.chainId, chainAsset.id)!!
        val asset = mapAssetLocalToAsset(assetLocal, chainAsset, chain.minSupportedVersion)

        val existentialDepositInPlanks = kotlin.runCatching { walletConstants.existentialDeposit(chain.id) }.getOrDefault(BigInteger.ZERO)
        val existentialDeposit = chainAsset.amountFromPlanks(existentialDepositInPlanks)

        val tipInPlanks = kotlin.runCatching { walletConstants.tip(chain.id) }.getOrNull()
        val tip = tipInPlanks?.let { chainAsset.amountFromPlanks(it) }

        return transfer.validityStatus(asset.transferable, asset.total.orZero(), feeResponse.feeAmount, totalRecipientBalance, existentialDeposit, tip)
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
        assetId: String,
        fiatSymbol: String?,
        price: BigDecimal?,
        change: BigDecimal?
    ) = assetCache.updateToken(assetId) { cached ->
        cached.copy(
            fiatRate = price,
            fiatSymbol = fiatSymbol,
            recentRateChange = change
        )
    }

    private suspend fun getAssetPriceCoingecko(vararg priceId: String, currencyId: String): Map<String, Map<String, BigDecimal>> {
        return apiCall { coingeckoApi.getAssetPrice(priceId.joinToString(","), currencyId, true) }
    }

    private suspend fun <T> apiCall(block: suspend () -> T): T = httpExceptionHandler.wrap(block)

    override suspend fun getRemoteConfig(): Result<AppConfigRemote> {
        return kotlin.runCatching { remoteConfigFetcher.getAppConfig() }
    }

    override fun chainRegistrySyncUp() {
        chainRegistry.syncUp()
    }
}
