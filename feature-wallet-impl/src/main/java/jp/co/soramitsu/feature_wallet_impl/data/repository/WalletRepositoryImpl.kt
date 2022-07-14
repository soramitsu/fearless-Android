package jp.co.soramitsu.feature_wallet_impl.data.repository

import jp.co.soramitsu.common.data.model.CursorPage
import jp.co.soramitsu.common.data.network.HttpExceptionHandler
import jp.co.soramitsu.common.data.network.coingecko.CoingeckoApi
import jp.co.soramitsu.common.data.network.config.AppConfigRemote
import jp.co.soramitsu.common.data.network.config.RemoteConfigFetcher
import jp.co.soramitsu.common.domain.GetAvailableFiatCurrencies
import jp.co.soramitsu.common.mixin.api.UpdatesMixin
import jp.co.soramitsu.common.mixin.api.UpdatesProviderUi
import jp.co.soramitsu.common.utils.orZero
import jp.co.soramitsu.core_db.dao.PhishingAddressDao
import jp.co.soramitsu.core_db.dao.emptyAccountIdValue
import jp.co.soramitsu.core_db.model.AssetUpdateItem
import jp.co.soramitsu.core_db.model.AssetWithToken
import jp.co.soramitsu.core_db.model.PhishingAddressLocal
import jp.co.soramitsu.fearless_utils.extensions.toHexString
import jp.co.soramitsu.fearless_utils.runtime.AccountId
import jp.co.soramitsu.fearless_utils.runtime.extrinsic.ExtrinsicBuilder
import jp.co.soramitsu.fearless_utils.ss58.SS58Encoder.toAccountId
import jp.co.soramitsu.feature_account_api.domain.model.MetaAccount
import jp.co.soramitsu.feature_account_api.domain.model.accountId
import jp.co.soramitsu.feature_wallet_api.data.cache.AssetCache
import jp.co.soramitsu.feature_wallet_api.domain.interfaces.TransactionFilter
import jp.co.soramitsu.feature_wallet_api.domain.interfaces.WalletConstants
import jp.co.soramitsu.feature_wallet_api.domain.interfaces.WalletRepository
import jp.co.soramitsu.feature_wallet_api.domain.interfaces.allFiltersIncluded
import jp.co.soramitsu.feature_wallet_api.domain.model.Asset
import jp.co.soramitsu.feature_wallet_api.domain.model.Asset.Companion.createEmpty
import jp.co.soramitsu.feature_wallet_api.domain.model.AssetWithStatus
import jp.co.soramitsu.feature_wallet_api.domain.model.Fee
import jp.co.soramitsu.feature_wallet_api.domain.model.Operation
import jp.co.soramitsu.feature_wallet_api.domain.model.Transfer
import jp.co.soramitsu.feature_wallet_api.domain.model.TransferValidityStatus
import jp.co.soramitsu.feature_wallet_api.domain.model.amountFromPlanks
import jp.co.soramitsu.feature_wallet_api.domain.model.planksFromAmount
import jp.co.soramitsu.feature_wallet_impl.data.mappers.mapAssetLocalToAsset
import jp.co.soramitsu.feature_wallet_impl.data.mappers.mapFeeRemoteToFee
import jp.co.soramitsu.feature_wallet_impl.data.mappers.mapNodeToOperation
import jp.co.soramitsu.feature_wallet_impl.data.network.blockchain.SubstrateRemoteSource
import jp.co.soramitsu.feature_wallet_impl.data.network.phishing.PhishingApi
import jp.co.soramitsu.feature_wallet_impl.data.network.subquery.HistoryNotSupportedException
import jp.co.soramitsu.feature_wallet_impl.data.storage.TransferCursorStorage
import jp.co.soramitsu.runtime.blockexplorer.SubQueryHistoryHandler
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
import kotlinx.coroutines.flow.mapNotNull
import kotlinx.coroutines.withContext
import java.math.BigDecimal
import java.math.BigInteger

class WalletRepositoryImpl(
    private val substrateSource: SubstrateRemoteSource,
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
    private val remoteConfigFetcher: RemoteConfigFetcher,
    private val subQueryHistoryHandler: SubQueryHistoryHandler,
) : WalletRepository, UpdatesProviderUi by updatesMixin {

    override fun assetsFlow(meta: MetaAccount, chainAccounts: List<MetaAccount.ChainAccount>): Flow<List<AssetWithStatus>> {
        return combine(
            chainRegistry.chainsById,
            assetCache.observeAssets(meta.id)
        ) { chainsById, assetsLocal ->
            val updatedAssets = assetsLocal.mapNotNull { asset ->
                mapAssetLocalToAsset(chainsById, asset)?.let {
                    val hasChainAccount = asset.asset.chainId in chainAccounts.mapNotNull { it.chain?.id }
                    AssetWithStatus(
                        asset = it,
                        enabled = it.enabled,
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
                            ),
                            enabled = true,
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
                            enabled = true,
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
        assetLocal: AssetWithToken,
    ): Asset? {
        val (chain, chainAsset) = try {
            val chain = chainsById.getValue(assetLocal.asset.chainId)
            val asset = chain.assetsBySymbol.getValue(assetLocal.token.symbol)
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

        val symbols = chainsWithAssetPrices.map { it.utilityAsset.symbol }
        updatesMixin.startUpdateTokens(symbols)

        chainsWithAssetPrices.forEach { chain ->
            val asset = chain.utilityAsset
            val stat = priceStats[chain.utilityAsset.priceId] ?: return@forEach
            val price = stat[currencyId]
            val changeKey = "${currencyId}_24h_change"
            val change = stat[changeKey]
            val fiatCurrency = availableFiatCurrencies[currencyId]
            asset.priceId?.let {
                updateAssetRates(asset.symbol, fiatCurrency?.symbol, price, change)
            }
        }
        updatesMixin.finishUpdateTokens(symbols)
    }

    override fun assetFlow(metaId: Long, accountId: AccountId, chainAsset: Chain.Asset, minSupportedVersion: String?): Flow<Asset> {
        return assetCache.observeAsset(metaId, accountId, chainAsset.chainId, chainAsset.symbol)
            .mapNotNull { it }
            .mapNotNull { mapAssetLocalToAsset(it, chainAsset, minSupportedVersion) }
    }

    override suspend fun getAsset(metaId: Long, accountId: AccountId, chainAsset: Chain.Asset, minSupportedVersion: String?): Asset? {
        val assetLocal = assetCache.getAsset(metaId, accountId, chainAsset.chainId, chainAsset.symbol)

        return assetLocal?.let { mapAssetLocalToAsset(it, chainAsset, minSupportedVersion) }
    }

    override suspend fun getOperations(
        pageNumber: Long,
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
            val mutableFilters = filters.toMutableSet()
            val requestRewards = chainAsset.staking != Chain.Asset.StakingType.UNSUPPORTED
            if (!requestRewards) mutableFilters.remove(TransactionFilter.REWARD)
            val myAddress = chain.addressOf(accountId)
            val response = subQueryHistoryHandler.getHistoryPage(
                address = myAddress,
                networkName = chain.name,
                pageNumber = pageNumber,
                url = historyUrl,
                modulesName = if (mutableFilters.allFiltersIncluded()) null else mutableFilters.map { it.name },
            )

            val operations = response.items.map { mapNodeToOperation(it, chainAsset, myAddress) }

            CursorPage(response.page, response.endReached, operations)
        }
    }

    override suspend fun getContacts(
        chain: Chain,
        query: String
    ): Set<String> {
        return subQueryHistoryHandler.getTransferContacts(query, chain.name).toSet()
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

        val operation = Operation(
            id = operationHash,
            address = accountAddress,
            time = System.currentTimeMillis(),
            chainAsset = transfer.chainAsset,
            type = Operation.Type.Transfer(
                hash = operationHash,
                myAddress = accountAddress,
                amount = transfer.amountInPlanks,
                receiver = transfer.recipient,
                sender = accountAddress,
                status = Operation.Status.PENDING,
                fee = transfer.chainAsset.planksFromAmount(fee),
            )
        )
        cursorStorage.saveOperations(chain.name, chain.addressOf(accountId), listOf(operation))
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

        val assetLocal = assetCache.getAsset(metaId, accountId, chainAsset.chainId, chainAsset.symbol)!!
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

    private suspend fun updateAssetRates(
        symbol: String,
        fiatSymbol: String?,
        price: BigDecimal?,
        change: BigDecimal?,
    ) = assetCache.updateToken(symbol) { cached ->
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
