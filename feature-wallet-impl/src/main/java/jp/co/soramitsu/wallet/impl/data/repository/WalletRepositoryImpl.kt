package jp.co.soramitsu.wallet.impl.data.repository

import com.opencsv.CSVReaderHeaderAware
import java.math.BigDecimal
import java.math.BigInteger
import jp.co.soramitsu.account.api.domain.interfaces.AccountRepository
import jp.co.soramitsu.account.api.domain.model.MetaAccount
import jp.co.soramitsu.account.api.domain.model.accountId
import jp.co.soramitsu.common.compose.component.NetworkIssueItemState
import jp.co.soramitsu.common.compose.component.NetworkIssueType
import jp.co.soramitsu.common.data.network.HttpExceptionHandler
import jp.co.soramitsu.common.data.network.coingecko.CoingeckoApi
import jp.co.soramitsu.common.data.network.config.AppConfigRemote
import jp.co.soramitsu.common.data.network.config.RemoteConfigFetcher
import jp.co.soramitsu.common.data.network.runtime.binding.UseCaseBinding
import jp.co.soramitsu.common.data.network.runtime.binding.bindNumber
import jp.co.soramitsu.common.data.network.runtime.binding.bindString
import jp.co.soramitsu.common.data.network.runtime.binding.cast
import jp.co.soramitsu.common.data.secrets.v2.KeyPairSchema
import jp.co.soramitsu.common.data.secrets.v2.MetaAccountSecrets
import jp.co.soramitsu.common.data.storage.Preferences
import jp.co.soramitsu.common.domain.GetAvailableFiatCurrencies
import jp.co.soramitsu.common.domain.SelectedFiat
import jp.co.soramitsu.common.mixin.api.UpdatesMixin
import jp.co.soramitsu.common.mixin.api.UpdatesProviderUi
import jp.co.soramitsu.common.utils.Modules
import jp.co.soramitsu.common.utils.balances
import jp.co.soramitsu.common.utils.orZero
import jp.co.soramitsu.common.utils.requireValue
import jp.co.soramitsu.common.utils.tokens
import jp.co.soramitsu.core.extrinsic.ExtrinsicService
import jp.co.soramitsu.core.models.Asset.PriceProvider
import jp.co.soramitsu.core.models.Asset.PriceProviderType.Chainlink
import jp.co.soramitsu.core.models.IChain
import jp.co.soramitsu.core.runtime.storage.returnType
import jp.co.soramitsu.core.utils.utilityAsset
import jp.co.soramitsu.coredb.dao.OperationDao
import jp.co.soramitsu.coredb.dao.PhishingDao
import jp.co.soramitsu.coredb.dao.TokenPriceDao
import jp.co.soramitsu.coredb.dao.emptyAccountIdValue
import jp.co.soramitsu.coredb.model.AssetUpdateItem
import jp.co.soramitsu.coredb.model.AssetWithToken
import jp.co.soramitsu.coredb.model.OperationLocal
import jp.co.soramitsu.coredb.model.PhishingLocal
import jp.co.soramitsu.coredb.model.TokenPriceLocal
import jp.co.soramitsu.runtime.ext.accountIdOf
import jp.co.soramitsu.runtime.ext.addressOf
import jp.co.soramitsu.runtime.multiNetwork.ChainRegistry
import jp.co.soramitsu.runtime.multiNetwork.chain.ChainsRepository
import jp.co.soramitsu.runtime.multiNetwork.chain.mapChainLocalToChain
import jp.co.soramitsu.runtime.multiNetwork.chain.model.Chain
import jp.co.soramitsu.runtime.multiNetwork.chain.model.ChainId
import jp.co.soramitsu.runtime.storage.source.StorageDataSource
import jp.co.soramitsu.shared_utils.extensions.toHexString
import jp.co.soramitsu.shared_utils.runtime.AccountId
import jp.co.soramitsu.shared_utils.runtime.RuntimeSnapshot
import jp.co.soramitsu.shared_utils.runtime.definitions.types.Type
import jp.co.soramitsu.shared_utils.runtime.definitions.types.composite.Struct
import jp.co.soramitsu.shared_utils.runtime.definitions.types.fromHexOrNull
import jp.co.soramitsu.shared_utils.runtime.extrinsic.ExtrinsicBuilder
import jp.co.soramitsu.shared_utils.runtime.metadata.moduleOrNull
import jp.co.soramitsu.shared_utils.runtime.metadata.storage
import jp.co.soramitsu.shared_utils.runtime.metadata.storageKey
import jp.co.soramitsu.wallet.api.data.cache.AssetCache
import jp.co.soramitsu.wallet.impl.data.mappers.mapAssetLocalToAsset
import jp.co.soramitsu.wallet.impl.data.network.blockchain.EthereumRemoteSource
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
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.mapNotNull
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import jp.co.soramitsu.core.models.Asset as CoreAsset

class WalletRepositoryImpl(
    private val substrateSource: SubstrateRemoteSource,
    private val ethereumSource: EthereumRemoteSource,
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
    private val remoteConfigFetcher: RemoteConfigFetcher,
    private val preferences: Preferences,
    private val accountRepository: AccountRepository,
    private val chainsRepository: ChainsRepository,
    private val selectedFiat: SelectedFiat,
    private val tokenPriceDao: TokenPriceDao,
    private val extrinsicService: ExtrinsicService,
    private val remoteStorageSource: StorageDataSource
) : WalletRepository, UpdatesProviderUi by updatesMixin {

    companion object {
        private const val COINGECKO_REQUEST_DELAY_MILLIS = 60 * 1000
    }

    private val coingeckoCache = mutableMapOf<String, MutableMap<String, Pair<Long, BigDecimal>>>()

    override fun assetsFlow(meta: MetaAccount): Flow<List<AssetWithStatus>> {
        return combine(
            chainsRepository.chainsByIdFlow(),
            assetCache.observeAssets(meta.id)
        ) { chainsById, assetsLocal ->
            val chainAccounts = meta.chainAccounts.values.toList()
            val updatedAssets = assetsLocal.mapNotNull { asset ->
                mapAssetLocalToAsset(chainsById, asset)?.let {
                    val hasChainAccount =
                        asset.asset.chainId in chainAccounts.mapNotNull { it.chain?.id }
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
                                minSupportedVersion = chain.minSupportedVersion
                            ),
                            hasAccount = !chain.isEthereumBased || meta.ethereumPublicKey != null,
                            hasChainAccount = chain.id in chainAccounts.mapNotNull { it.chain?.id }
                        )
                    }
                }

            val assetsByUniqueAccounts = chainAccounts.mapNotNull { chainAccount ->
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

    private fun buildNetworkIssues(items: List<AssetWithStatus>): Set<NetworkIssueItemState> {
        return items.map {
            val configuration = it.asset.token.configuration
            NetworkIssueItemState(
                iconUrl = configuration.iconUrl,
                title = "${configuration.chainName} ${configuration.name}",
                type = NetworkIssueType.Node,
                chainId = configuration.chainId,
                chainName = configuration.chainName,
                assetId = configuration.id
            )
        }.toSet()
    }

    override suspend fun getAssets(metaId: Long): List<Asset> = withContext(Dispatchers.Default) {
        val chainsById = chainsRepository.getChainsById()
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
        val chains = chainsRepository.getChains()
        syncAllRates(chains, currencyId)
    }

    private suspend fun syncAllRates(chains: List<Chain>, currencyId: String)  {
        val priceIdsWithChainlinkId = chains.map {
            it.assets.mapNotNull { asset ->
                asset.priceId?.let { priceId ->
                    priceId to if (asset.priceProvider?.type == Chainlink) {
                        asset.priceProvider?.id
                    } else {
                        null
                    }
                }
            }
        }.flatten().toSet()

        val priceIds = priceIdsWithChainlinkId.map { it.first }
        val chainlinkProviderIds = priceIdsWithChainlinkId.mapNotNull { it.second }
        val allPriceIds = (priceIds + chainlinkProviderIds).toSet()

        updatesMixin.startUpdateTokens(allPriceIds)

        var coingeckoPriceStats: Map<String, Map<String, BigDecimal>> = emptyMap()
        var chainlinkPrices: Map<String, BigDecimal> =  emptyMap()

        coroutineScope {
            launch {
                coingeckoPriceStats =
                    getAssetPriceCoingecko(*priceIds.toTypedArray(), currencyId = currencyId)
            }
            launch {
                chainlinkPrices = if (currencyId == "usd") {
                    getChainlinkPrices(chains)
                } else {
                    emptyMap()
                }
            }
        }.join()

        val newPrices = priceIdsWithChainlinkId.mapNotNull { (priceId, chainlinkId) ->
            val stat = coingeckoPriceStats[priceId] ?: return@mapNotNull null

            val changeKey = "${currencyId}_24h_change"
            val change = stat[changeKey]
            val fiatCurrency = availableFiatCurrencies[currencyId]

            listOf(
                TokenPriceLocal(priceId, stat[currencyId], fiatCurrency?.symbol, change),
                chainlinkId?.let {
                    TokenPriceLocal(chainlinkId, chainlinkPrices[chainlinkId], fiatCurrency?.symbol, change)
                }
            )
        }.flatten().filterNotNull()
        assetCache.updateTokensPrice(newPrices)
        updatesMixin.finishUpdateTokens(allPriceIds)
    }

    private suspend fun getChainlinkPrices(chains: List<Chain>): Map<String, BigDecimal> {
        val chainlinkProvider = chains.firstOrNull { it.chainlinkProvider } ?: return emptyMap()

        val allAssets =
            chains.map { it.assets }.flatten().asSequence().filter { it.priceProvider != null }
                .toList()

        return allAssets.mapNotNull {
            val priceProvider = it.priceProvider ?: return@mapNotNull null
            val price =
                getChainlinkPrices(priceProvider = priceProvider, chainId = chainlinkProvider.id)
                    ?: return@mapNotNull null
            priceProvider.id to price
        }.toMap()
    }

    private suspend fun getChainlinkPrices(
        priceProvider: PriceProvider,
        chainId: ChainId
    ): BigDecimal? {
        return runCatching {
            ethereumSource.fetchPriceFeed(
                chainId = chainId,
                receiverAddress = priceProvider.id
            )?.let { price ->
                BigDecimal(price, priceProvider.precision)
            }
        }.getOrNull()
    }

    override fun assetFlow(
        metaId: Long,
        accountId: AccountId,
        chainAsset: CoreAsset,
        minSupportedVersion: String?
    ): Flow<Asset> {
        return assetCache.observeAsset(metaId, accountId, chainAsset.chainId, chainAsset.id)
            .mapNotNull { it }
            .mapNotNull { mapAssetLocalToAsset(it, chainAsset, minSupportedVersion) }
            .distinctUntilChanged()
    }

    override suspend fun getAsset(
        metaId: Long,
        accountId: AccountId,
        chainAsset: CoreAsset,
        minSupportedVersion: String?
    ): Asset? {
        val assetLocal = assetCache.getAsset(metaId, accountId, chainAsset.chainId, chainAsset.id)

        return assetLocal?.let { mapAssetLocalToAsset(it, chainAsset, minSupportedVersion) }
    }

    override suspend fun updateAssetHidden(
        metaId: Long,
        accountId: AccountId,
        isHidden: Boolean,
        chainAsset: CoreAsset
    ) {
        val tokenPriceId =
            chainAsset.priceProvider?.id?.takeIf { selectedFiat.isUsd() } ?: chainAsset.priceId
        val updateItems = listOf(
            AssetUpdateItem(
                metaId = metaId,
                chainId = chainAsset.chainId,
                accountId = accountId,
                id = chainAsset.id,
                sortIndex = Int.MAX_VALUE, // Int.MAX_VALUE on sorting because we don't use it anymore - just random value
                enabled = !isHidden,
                tokenPriceId = tokenPriceId
            )
        )

        assetCache.updateAsset(updateItems)
    }

    override suspend fun observeTransferFee(
        chain: Chain,
        transfer: Transfer,
        additional: (suspend ExtrinsicBuilder.() -> Unit)?,
        batchAll: Boolean
    ): Flow<Fee> {
        return if (chain.isEthereumChain) {
            subscribeOnEthereumTransferFee(transfer, chain)
        } else {
            flowOf(substrateSource.getTransferFee(chain, transfer, additional, batchAll))
        }
            .flowOn(Dispatchers.IO)
            .map {
                Fee(
                    transferAmount = transfer.amount,
                    feeAmount = chain.utilityAsset?.amountFromPlanks(it).orZero()
                )
            }
    }

    override suspend fun getTransferFee(
        chain: Chain,
        transfer: Transfer,
        additional: (suspend ExtrinsicBuilder.() -> Unit)?,
        batchAll: Boolean
    ): Fee {
        val fee = if (chain.isEthereumChain) {
            subscribeOnEthereumTransferFee(transfer, chain).first()
        } else {
            substrateSource.getTransferFee(chain, transfer, additional, batchAll)
        }

        return Fee(
            transferAmount = transfer.amount,
            feeAmount = chain.utilityAsset?.amountFromPlanks(fee).orZero()
        )
    }

    private fun subscribeOnEthereumTransferFee(transfer: Transfer, chain: Chain): Flow<BigInteger> {
        return ethereumSource.listenGas(transfer, chain)
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
        val operationHash = if (chain.isEthereumChain) {
            val currentMetaAccount = accountRepository.getSelectedMetaAccount()
            val secrets = accountRepository.getMetaAccountSecrets(currentMetaAccount.id)
                ?: error("There are no secrets for metaId: ${currentMetaAccount.id}")
            val keypairSchema = secrets[MetaAccountSecrets.EthereumKeypair] ?: error("")
            val privateKey = keypairSchema[KeyPairSchema.PrivateKey]

            ethereumSource.performTransfer(chain, transfer, privateKey.toHexString(true))
                .requireValue() // handle error
        } else {
            substrateSource.performTransfer(accountId, chain, transfer, tip, additional, batchAll)
        }

        val accountAddress = chain.addressOf(accountId)
        val utilityAsset = chain.assets.firstOrNull { it.isUtility }

        val operation = createOperation(
            operationHash,
            transfer,
            accountAddress,
            fee,
            OperationLocal.Source.APP,
            utilityAsset
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

        val totalRecipientBalanceInPlanks = getTotalBalance(chainAsset, chain, recipientAccountId)
        val totalRecipientBalance = chainAsset.amountFromPlanks(totalRecipientBalanceInPlanks)

        val assetLocal = assetCache.getAsset(metaId, accountId, chainAsset.chainId, chainAsset.id)!!
        val asset = mapAssetLocalToAsset(assetLocal, chainAsset, chain.minSupportedVersion)

        val existentialDepositInPlanks = walletConstants.existentialDeposit(chainAsset).orZero()
        val existentialDeposit = chainAsset.amountFromPlanks(existentialDepositInPlanks)

        val utilityAssetLocal = assetCache.getAsset(
            metaId,
            accountId,
            chainAsset.chainId,
            chain.utilityAsset?.id.orEmpty()
        )!!
        val utilityAsset = chain.utilityAsset?.let {
            mapAssetLocalToAsset(
                utilityAssetLocal,
                it,
                chain.minSupportedVersion
            )
        }

        val utilityExistentialDepositInPlanks =
            chain.utilityAsset?.let { walletConstants.existentialDeposit(it) }.orZero()
        val utilityExistentialDeposit =
            chain.utilityAsset?.amountFromPlanks(utilityExistentialDepositInPlanks).orZero()

        val tipInPlanks = kotlin.runCatching { walletConstants.tip(chain.id) }.getOrNull()
        val tip = tipInPlanks?.let { chain.utilityAsset?.amountFromPlanks(it) }

        return transfer.validityStatus(
            senderTransferable = asset.transferable,
            senderTotal = asset.total.orZero(),
            fee = feeResponse.feeAmount,
            recipientBalance = totalRecipientBalance,
            existentialDeposit = existentialDeposit,
            isUtilityToken = chainAsset.isUtility,
            senderUtilityBalance = utilityAsset?.total.orZero(),
            utilityExistentialDeposit = utilityExistentialDeposit,
            tip = tip
        )
    }

    override suspend fun getTotalBalance(
        chainAsset: jp.co.soramitsu.core.models.Asset,
        chain: Chain,
        accountId: ByteArray
    ): BigInteger {
        return if (chain.isEthereumChain) {
            ethereumSource.getTotalBalance(chainAsset, chain, accountId)
                .requireValue() // handle errors
        } else {
            substrateSource.getTotalBalance(chainAsset, accountId)
        }
    }

    override suspend fun updatePhishingAddresses() = withContext(Dispatchers.Default) {
        val phishingAddresses = phishingApi.getPhishingAddresses()
        val phishingLocal =
            CSVReaderHeaderAware(phishingAddresses.byteStream().bufferedReader()).mapNotNull {
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

    override suspend fun isAddressFromPhishingList(address: String) =
        withContext(Dispatchers.Default) {
            val phishingAddresses = phishingDao.getAllAddresses().map { it.lowercase() }

            phishingAddresses.contains(address.lowercase())
        }

    override suspend fun getPhishingInfo(address: String): PhishingLocal? {
        return phishingDao.getPhishingInfo(address)
    }

    override suspend fun getAccountFreeBalance(chainAsset: CoreAsset, accountId: AccountId) =
        substrateSource.getAccountFreeBalance(chainAsset, accountId)

    override suspend fun getEquilibriumAssetRates(chainAsset: CoreAsset) =
        substrateSource.getEquilibriumAssetRates(chainAsset)

    override suspend fun getEquilibriumAccountInfo(asset: CoreAsset, accountId: AccountId) =
        substrateSource.getEquilibriumAccountInfo(asset, accountId)

    private fun createOperation(
        hash: String,
        transfer: Transfer,
        senderAddress: String,
        fee: BigDecimal,
        source: OperationLocal.Source,
        utilityAsset: jp.co.soramitsu.core.models.Asset?
    ) =
        OperationLocal.manualTransfer(
            hash = hash,
            address = senderAddress,
            chainAssetId = transfer.chainAsset.id,
            chainId = transfer.chainAsset.chainId,
            amount = transfer.amountInPlanks,
            senderAddress = senderAddress,
            receiverAddress = transfer.recipient,
            fee = utilityAsset?.planksFromAmount(fee),
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

    private suspend fun updateAssetsRates(
        items: List<TokenPriceLocal>
    ) = assetCache.updateTokensPrice(items)

    override suspend fun getSingleAssetPriceCoingecko(
        priceId: String,
        currency: String
    ): BigDecimal? {
        coingeckoCache[priceId]?.get(currency)?.let { (cacheUntilMillis, cachedValue) ->
            if (System.currentTimeMillis() <= cacheUntilMillis) {
                return cachedValue
            }
        }
        val apiValue = apiCall {
            coingeckoApi.getSingleAssetPrice(priceIds = priceId, currency = currency)
        }.getOrDefault(priceId, null)?.getOrDefault(currency, null)?.toBigDecimal()

        apiValue?.let {
            val currencyMap = coingeckoCache[priceId] ?: mutableMapOf()
            val cacheUntilMillis = System.currentTimeMillis() + COINGECKO_REQUEST_DELAY_MILLIS
            currencyMap[currency] = cacheUntilMillis to apiValue
            coingeckoCache[priceId] = currencyMap
        }
        return apiValue
    }

    private suspend fun getAssetPriceCoingecko(
        vararg priceId: String,
        currencyId: String
    ): Map<String, Map<String, BigDecimal>> {
        return apiCall { coingeckoApi.getAssetPrice(priceId.joinToString(","), currencyId, true) }
    }

    private suspend fun <T> apiCall(block: suspend () -> T): T = httpExceptionHandler.wrap(block)

    override suspend fun getRemoteConfig(): Result<AppConfigRemote> {
        return kotlin.runCatching { remoteConfigFetcher.getAppConfig() }
    }

    override fun chainRegistrySyncUp() {
        chainRegistry.syncUp()
    }

    override suspend fun getControllerAccount(chainId: ChainId, accountId: AccountId): AccountId? {
        return substrateSource.getControllerAccount(chainId, accountId)
    }

    override suspend fun getStashAccount(chainId: ChainId, accountId: AccountId): AccountId? {
        return substrateSource.getStashAccount(chainId, accountId)
    }

    override fun observeChainsPerAsset(
        accountMetaId: Long,
        assetId: String
    ): Flow<Map<Chain, Asset?>> {
        return chainsRepository.observeChainsPerAssetFlow(accountMetaId, assetId).map {
            val chains = it.keys.map { mapChainLocalToChain(it) }
            val chainsById = chains.associateBy { it.id }
            val assets = it.values.map { mapAssetLocalToAsset(chainsById, it) }
            chains.zip(assets).toMap()
        }
    }

    override suspend fun getVestingLockedAmount(chainId: ChainId): BigInteger? {
        val currentMetaAccount = accountRepository.getSelectedMetaAccount()
        val chain = chainsRepository.getChain(chainId)
        val accountId = currentMetaAccount.accountId(chain)

        val locksStorageKeyAndReturnType = chainRegistry.getRuntimeOrNull(chainId)?.let { runtime ->
            if (runtime.metadata.moduleOrNull(Modules.BALANCES)?.storage?.get("Locks") != null) {
                runtime.metadata.balances().storage("Locks").storageKey(runtime, accountId) to
                        runtime.metadata.balances().storage("Locks").returnType()
            } else if (runtime.metadata.moduleOrNull(Modules.TOKENS)?.storage?.get("Locks") != null) {
                val currency = chain.utilityAsset?.currency
                runtime.metadata.tokens().storage("Locks")
                    .storageKey(runtime, accountId, currency) to
                        runtime.metadata.tokens().storage("Locks").returnType()
            } else {
                null
            }
        }

        return locksStorageKeyAndReturnType?.let { (locksStorageKey, returnType) ->
            remoteStorageSource.query(
                chainId = chainId,
                keyBuilder = { locksStorageKey },
                binding = { scale, runtime ->
                    bindVestingLockedAmount(scale, runtime, returnType)
                }
            )
        }
    }

    @UseCaseBinding
    fun bindVestingLockedAmount(
        scale: String?,
        runtime: RuntimeSnapshot,
        returnType: Type<*>
    ): BigInteger? {
        scale ?: return null

        val locksDynamicInstance =
            returnType.fromHexOrNull(runtime, scale).cast<List<Struct.Instance>>()

        return locksDynamicInstance.firstOrNull {
            bindString(it["id"]) == "ormlvest"
        }?.let {
            bindNumber(it["amount"])
        }
    }

    override suspend fun estimateClaimRewardsFee(chainId: ChainId): BigInteger {
        return withContext(Dispatchers.IO) {
            val chain = chainsRepository.getChain(chainId)

            extrinsicService.estimateFee(chain) {
                claimRewards()
            }
        }
    }

    override suspend fun claimRewards(chain: IChain, accountId: AccountId): Result<String> {
        return withContext(Dispatchers.IO) {
            extrinsicService.submitExtrinsic(chain, accountId) {
                claimRewards()
            }
        }
    }

}

fun ExtrinsicBuilder.claimRewards(): ExtrinsicBuilder {
    return if (runtime.metadata.moduleOrNull(Modules.VESTING)?.calls?.get("claim") != null) {
        call(Modules.VESTING, "claim", emptyMap())
    } else if (runtime.metadata.moduleOrNull(Modules.VESTING)?.calls?.get("vest") != null) {
        call(Modules.VESTING, "vest", emptyMap())
    } else if (runtime.metadata.moduleOrNull(Modules.VESTED_REWARDS)?.calls?.get("claim_rewards") != null) {
        call(Modules.VESTED_REWARDS, "claim_rewards", emptyMap())
    } else {
        this
    }
}

