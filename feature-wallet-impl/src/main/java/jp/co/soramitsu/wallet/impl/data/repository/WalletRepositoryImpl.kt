package jp.co.soramitsu.wallet.impl.data.repository

import com.opencsv.CSVReaderHeaderAware
import java.math.BigDecimal
import java.math.BigInteger
import jp.co.soramitsu.account.api.domain.model.MetaAccount
import jp.co.soramitsu.account.api.domain.model.accountId
import jp.co.soramitsu.common.data.network.HttpExceptionHandler
import jp.co.soramitsu.common.data.network.coingecko.CoingeckoApi
import jp.co.soramitsu.common.data.network.config.AppConfigRemote
import jp.co.soramitsu.common.data.network.config.RemoteConfigFetcher
import jp.co.soramitsu.common.domain.GetAvailableFiatCurrencies
import jp.co.soramitsu.common.mixin.api.UpdatesMixin
import jp.co.soramitsu.common.mixin.api.UpdatesProviderUi
import jp.co.soramitsu.common.utils.orZero
import jp.co.soramitsu.coredb.dao.OperationDao
import jp.co.soramitsu.coredb.dao.PhishingDao
import jp.co.soramitsu.coredb.dao.emptyAccountIdValue
import jp.co.soramitsu.coredb.model.AssetUpdateItem
import jp.co.soramitsu.coredb.model.AssetWithToken
import jp.co.soramitsu.coredb.model.OperationLocal
import jp.co.soramitsu.coredb.model.PhishingLocal
import jp.co.soramitsu.fearless_utils.runtime.AccountId
import jp.co.soramitsu.fearless_utils.runtime.extrinsic.ExtrinsicBuilder
import jp.co.soramitsu.runtime.ext.accountIdOf
import jp.co.soramitsu.runtime.ext.addressOf
import jp.co.soramitsu.runtime.ext.utilityAsset
import jp.co.soramitsu.runtime.multiNetwork.ChainRegistry
import jp.co.soramitsu.runtime.multiNetwork.chain.model.Chain
import jp.co.soramitsu.runtime.multiNetwork.chain.model.ChainId
import jp.co.soramitsu.wallet.api.data.cache.AssetCache
import jp.co.soramitsu.wallet.impl.data.mappers.mapAssetLocalToAsset
import jp.co.soramitsu.wallet.impl.data.network.blockchain.SubstrateRemoteSource
import jp.co.soramitsu.wallet.impl.data.network.phishing.PhishingApi
import jp.co.soramitsu.wallet.impl.domain.interfaces.WalletConstants
import jp.co.soramitsu.wallet.impl.domain.interfaces.WalletRepository
import jp.co.soramitsu.wallet.impl.domain.model.Asset
import jp.co.soramitsu.wallet.impl.domain.model.Asset.Companion.createEmpty
import jp.co.soramitsu.wallet.impl.domain.model.AssetWithStatus
import jp.co.soramitsu.wallet.impl.domain.model.Fee
import jp.co.soramitsu.wallet.impl.domain.model.Transfer
import jp.co.soramitsu.wallet.impl.domain.model.TransferValidityStatus
import jp.co.soramitsu.wallet.impl.domain.model.amountFromPlanks
import jp.co.soramitsu.wallet.impl.domain.model.planksFromAmount
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.mapNotNull
import kotlinx.coroutines.withContext

class WalletRepositoryImpl(
    private val substrateSource: SubstrateRemoteSource,
    private val operationDao: OperationDao,
    private val httpExceptionHandler: HttpExceptionHandler,
    private val phishingApi: PhishingApi,
    private val assetCache: AssetCache,
    private val walletConstants: WalletConstants,
    private val phishingDao: PhishingDao,
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

            val assetsByChain: List<AssetWithStatus> = chainsById.values
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
            val asset = chain.assetsById.getValue(assetLocal.asset.id)
            chain to asset
        } catch (e: Exception) {
            return null
        }

        return mapAssetLocalToAsset(assetLocal, chainAsset, chain.minSupportedVersion)
    }

    override suspend fun syncAssetsRates(currencyId: String) {
        val chains = chainRegistry.currentChains.first()
        val priceIds = chains.map { it.assets.mapNotNull { it.priceId } }.flatten().toSet()
        val priceStats = getAssetPriceCoingecko(*priceIds.toTypedArray(), currencyId = currencyId)

        updatesMixin.startUpdateTokens(priceIds)

        priceIds.forEach { priceId ->
            val stat = priceStats[priceId] ?: return@forEach
            val price = stat[currencyId]
            val changeKey = "${currencyId}_24h_change"
            val change = stat[changeKey]
            val fiatCurrency = availableFiatCurrencies[currencyId]

            updateAssetRates(priceId, fiatCurrency?.symbol, price, change)
        }
        updatesMixin.finishUpdateTokens(priceIds)
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

    override suspend fun updateAssetHidden(
        metaId: Long,
        accountId: AccountId,
        isHidden: Boolean,
        chainAsset: Chain.Asset
    ) {
        val updateItems = listOf(
            AssetUpdateItem(
                metaId = metaId,
                chainId = chainAsset.chainId,
                accountId = accountId,
                id = chainAsset.id,
                sortIndex = Int.MAX_VALUE, // Int.MAX_VALUE on sorting because we don't use it anymore - just random value
                enabled = !isHidden,
                tokenPriceId = chainAsset.priceId
            )
        )

        assetCache.updateAsset(updateItems)
    }

    override suspend fun getTransferFee(
        chain: Chain,
        transfer: Transfer,
        additional: (suspend ExtrinsicBuilder.() -> Unit)?,
        batchAll: Boolean
    ): Fee {
        val fee = substrateSource.getTransferFee(chain, transfer, additional, batchAll)

        return Fee(
            transferAmount = transfer.amount,
            feeAmount = chain.utilityAsset.amountFromPlanks(fee)
        )
    }

    override suspend fun performTransfer(
        accountId: AccountId,
        chain: Chain,
        transfer: Transfer,
        fee: BigDecimal,
        tip: BigInteger?,
        additional: (suspend ExtrinsicBuilder.() -> Unit)?,
        batchAll: Boolean
    ): String {
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
        return operationHash
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
        val recipientAccountId = chain.accountIdOf(transfer.recipient)

        val totalRecipientBalanceInPlanks = substrateSource.getTotalBalance(chainAsset, recipientAccountId)
        val totalRecipientBalance = chainAsset.amountFromPlanks(totalRecipientBalanceInPlanks)

        val assetLocal = assetCache.getAsset(metaId, accountId, chainAsset.chainId, chainAsset.id)!!
        val asset = mapAssetLocalToAsset(assetLocal, chainAsset, chain.minSupportedVersion)

        val existentialDepositInPlanks = walletConstants.existentialDeposit(chainAsset).orZero()
        val existentialDeposit = chainAsset.amountFromPlanks(existentialDepositInPlanks)

        val utilityAssetLocal = assetCache.getAsset(metaId, accountId, chainAsset.chainId, chain.utilityAsset.id)!!
        val utilityAsset = mapAssetLocalToAsset(utilityAssetLocal, chain.utilityAsset, chain.minSupportedVersion)

        val utilityExistentialDepositInPlanks = walletConstants.existentialDeposit(chain.utilityAsset).orZero()
        val utilityExistentialDeposit = chain.utilityAsset.amountFromPlanks(utilityExistentialDepositInPlanks)

        val tipInPlanks = kotlin.runCatching { walletConstants.tip(chain.id) }.getOrNull()
        val tip = tipInPlanks?.let { chain.utilityAsset.amountFromPlanks(it) }

        return transfer.validityStatus(
            senderTransferable = asset.transferable,
            senderTotal = asset.total.orZero(),
            fee = feeResponse.feeAmount,
            recipientBalance = totalRecipientBalance,
            existentialDeposit = existentialDeposit,
            isUtilityToken = chainAsset.isUtility,
            senderUtilityBalance = utilityAsset.total.orZero(),
            utilityExistentialDeposit = utilityExistentialDeposit,
            tip = tip
        )
    }

    override suspend fun updatePhishingAddresses() = withContext(Dispatchers.Default) {
        val phishingAddresses = phishingApi.getPhishingAddresses()
        val phishingLocal = CSVReaderHeaderAware(phishingAddresses.byteStream().bufferedReader()).mapNotNull {
            try {
                PhishingLocal(
                    name = it[0],
                    address = it[1],
                    type = it[2],
                    subtype = it[3]
                )
            } catch (e: Exception) {
                e.printStackTrace()
                null
            }
        }

        phishingDao.clearTable()
        phishingDao.insert(phishingLocal)
    }

    override suspend fun isAddressFromPhishingList(address: String) = withContext(Dispatchers.Default) {
        val phishingAddresses = phishingDao.getAllAddresses().map { it.lowercase() }

        phishingAddresses.contains(address.lowercase())
    }

    override suspend fun getPhishingInfo(address: String): PhishingLocal? {
        return phishingDao.getPhishingInfo(address)
    }

    override suspend fun getAccountFreeBalance(chainAsset: Chain.Asset, accountId: AccountId) =
        substrateSource.getAccountFreeBalance(chainAsset, accountId)

    override suspend fun getEquilibriumAssetRates(chainAsset: Chain.Asset) =
        substrateSource.getEquilibriumAssetRates(chainAsset)

    override suspend fun getEquilibriumAccountInfo(asset: Chain.Asset, accountId: AccountId) =
        substrateSource.getEquilibriumAccountInfo(asset, accountId)

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
        priceId: String,
        fiatSymbol: String?,
        price: BigDecimal?,
        change: BigDecimal?
    ) = assetCache.updateTokenPrice(priceId) { cached ->
        cached.copy(
            fiatRate = price,
            fiatSymbol = fiatSymbol,
            recentRateChange = change
        )
    }

    override suspend fun getSingleAssetPriceCoingecko(priceId: String, currency: String): BigDecimal? {
        return apiCall {
            coingeckoApi.getSingleAssetPrice(priceIds = priceId, currency = currency)
        }.getOrDefault(priceId, null)?.getOrDefault(currency, null)?.toBigDecimal()
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
