package jp.co.soramitsu.wallet.impl.data.repository

import androidx.room.withTransaction
import com.opencsv.CSVReaderHeaderAware
import jp.co.soramitsu.account.api.domain.interfaces.AccountRepository
import jp.co.soramitsu.account.api.domain.model.MetaAccount
import jp.co.soramitsu.account.api.domain.model.accountId
import jp.co.soramitsu.common.data.network.HttpExceptionHandler
import jp.co.soramitsu.common.data.network.coingecko.CoingeckoApi
import jp.co.soramitsu.common.data.network.config.AppConfigRemote
import jp.co.soramitsu.common.data.network.config.RemoteConfigFetcher
import jp.co.soramitsu.common.data.network.okx.OkxApi
import jp.co.soramitsu.common.data.network.okx.OkxCrossChainResponse
import jp.co.soramitsu.common.data.network.okx.OkxResponse
import jp.co.soramitsu.common.data.network.okx.OkxSwapResponse
import jp.co.soramitsu.common.data.network.runtime.binding.UseCaseBinding
import jp.co.soramitsu.common.data.network.runtime.binding.bindNumber
import jp.co.soramitsu.common.data.network.runtime.binding.bindString
import jp.co.soramitsu.common.data.network.runtime.binding.cast
import jp.co.soramitsu.common.data.secrets.v2.KeyPairSchema
import jp.co.soramitsu.common.data.secrets.v2.MetaAccountSecrets
import jp.co.soramitsu.common.mixin.api.UpdatesMixin
import jp.co.soramitsu.common.mixin.api.UpdatesProviderUi
import jp.co.soramitsu.common.utils.Modules
import jp.co.soramitsu.common.utils.balances
import jp.co.soramitsu.common.utils.mapList
import jp.co.soramitsu.common.utils.orZero
import jp.co.soramitsu.common.utils.requireValue
import jp.co.soramitsu.common.utils.tokens
import jp.co.soramitsu.core.extrinsic.ExtrinsicService
import jp.co.soramitsu.core.models.IChain
import jp.co.soramitsu.core.runtime.storage.returnType
import jp.co.soramitsu.core.utils.utilityAsset
import jp.co.soramitsu.coredb.AppDatabase
import jp.co.soramitsu.coredb.dao.OkxDao
import jp.co.soramitsu.coredb.dao.OperationDao
import jp.co.soramitsu.coredb.dao.PhishingDao
import jp.co.soramitsu.coredb.dao.emptyAccountIdValue
import jp.co.soramitsu.coredb.model.AssetUpdateItem
import jp.co.soramitsu.coredb.model.AssetWithToken
import jp.co.soramitsu.coredb.model.OkxChainLocal
import jp.co.soramitsu.coredb.model.OkxTokenLocal
import jp.co.soramitsu.coredb.model.OperationLocal
import jp.co.soramitsu.coredb.model.PhishingLocal
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
import jp.co.soramitsu.wallet.impl.domain.model.AssetWithStatus
import jp.co.soramitsu.wallet.impl.domain.model.Fee
import jp.co.soramitsu.wallet.impl.domain.model.OkxTokenModel
import jp.co.soramitsu.wallet.impl.domain.model.Transfer
import jp.co.soramitsu.wallet.impl.domain.model.TransferValidityStatus
import jp.co.soramitsu.wallet.impl.domain.model.amountFromPlanks
import jp.co.soramitsu.wallet.impl.domain.model.planksFromAmount
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.mapNotNull
import kotlinx.coroutines.withContext
import java.math.BigDecimal
import java.math.BigInteger
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
    private val updatesMixin: UpdatesMixin,
    private val remoteConfigFetcher: RemoteConfigFetcher,
    private val accountRepository: AccountRepository,
    private val chainsRepository: ChainsRepository,
    private val extrinsicService: ExtrinsicService,
    private val remoteStorageSource: StorageDataSource,
    private val pricesSyncService: PricesSyncService,
    private val okxApi: OkxApi,
    private val okxDao: OkxDao,
    private val db: AppDatabase
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
            updatedAssets
        }
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
        pricesSyncService.sync()
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

    override suspend fun updateAssetsHidden(state: List<AssetUpdateItem>) {
        assetCache.updateAsset(state)
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
        appId: BigInteger?,
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
            substrateSource.performTransfer(
                accountId,
                chain,
                transfer,
                tip,
                appId,
                additional,
                batchAll
            )
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
        chainAsset: CoreAsset,
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
        utilityAsset: CoreAsset?
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

    private suspend fun <T> apiCall(block: suspend () -> T): T = httpExceptionHandler.wrap(block)

//    override suspend fun fetchOkxSupportedAssets() {
//        try {
//            val okxResponse = okxApi.getSupportedChains()
//            val supportedByApp = chainsRepository.getChains().map { it.id }
//
//            val chains = okxResponse.data.filter {
//                it.chainId in supportedByApp
//            }.map {
//                OkxChainLocal(
//                    it.chainId,
//                    it.dexTokenApproveAddress
//                )
//            }
//
//            val current = okxDao.getSupportedChains()
//            val removed = current.minus(chains.toSet())
//
//            okxDao.deleteOkxChains(removed)
//            okxDao.insertOkxChains(chains)
//
//            fetchOkxTokens(chains)
//
//        } catch (e: Exception) {
//            println("!!! error ${e.message}")
//            e.printStackTrace()
//        }
//    }

    override fun observeOkxChains(): Flow<List<Chain>> {
        return okxDao.observeSupportedChains().mapList {
            chainsRepository.getChain(it.id)
        }
    }

    override suspend fun getOkxChains(): List<Chain> {
        return okxDao.getSupportedChains().map {
            chainsRepository.getChain(it.id)
        }
    }

    private val okxCrossChainInfoCache = mutableMapOf<ChainId?, List<Chain>>()

    override suspend fun getOkxCrossChains(chainId: ChainId?): List<Chain> {
        val cached = okxCrossChainInfoCache[chainId]

        if (cached.isNullOrEmpty()) {
            val response = okxApi.getCrossChainSupportedChains(chainId)
            val responseChainIds = response.data.map { it.chainId }

            val list = chainsRepository.getChainsById().filter {
                it.key in responseChainIds
            }.map { item ->
                item.value
            }
            okxCrossChainInfoCache[chainId] = list
            return list
        } else {
            return cached
        }
    }

    override suspend fun crossChainBuildTx(
        fromChainId: String,
        toChainId: String,
        fromTokenAddress: String,
        toTokenAddress: String,
        amount: String,
        sort: Int?, // 0 - default
        slippage: String,  // 0.002 - 0.5
        userWalletAddress: String,
    ): OkxResponse<OkxCrossChainResponse> {
        return okxApi.crossChainBuildTx(
            fromChainId = fromChainId,
            toChainId = toChainId,
            fromTokenAddress = fromTokenAddress,
            toTokenAddress = toTokenAddress,
            amount = amount,
            sort = sort,
            slippage = slippage,
            userWalletAddress = userWalletAddress,
        )
    }

    override suspend fun getOkxSwap(
        chainId: String,
        amount: String,
        fromTokenAddress: String,
        toTokenAddress: String,
        slippage: String,
        userWalletAddress: String
    ): OkxResponse<OkxSwapResponse> {
        return okxApi.getOkxSwap(
            chainId = chainId,
            amount = amount,
            fromTokenAddress = fromTokenAddress,
            toTokenAddress = toTokenAddress,
            slippage = slippage,
            userWalletAddress = userWalletAddress
        )
    }

//    private suspend fun fetchOkxTokens(chains: List<OkxChainLocal>) {
//        val okxTokens = chains.map { chain ->
//            val response = okxApi.getAllTokens(chain.id)
//            response.data.map { item ->
//                OkxTokenLocal(
//                    chainId = chain.id,
//                    symbol = item.tokenSymbol,
//                    tokenLogoUrl = item.tokenLogoUrl,
//                    tokenName = item.tokenName,
//                    tokenContractAddress = item.tokenContractAddress,
//                    decimals = item.decimals
//                )
//            }
//        }.flatten()
//
//        val current = okxDao.getSupportedTokens()
//        val removed = current.minus(okxTokens.toSet())
//        db.withTransaction {
//            okxDao.deleteOkxTokens(removed)
//            okxDao.insertOkxTokens(okxTokens)
//        }
//    }

    override suspend fun getOkxTokens(chainId: ChainId?): List<OkxTokenModel> {
        return okxDao.getSupportedTokens(chainId)
            .map { token ->
                val assetId = chainsRepository.getChain(token.chainId).assets.firstOrNull { asset ->
                    asset.symbol.equals(token.symbol, ignoreCase = true)
                }?.id

                OkxTokenModel(
                    chainId = token.chainId,
                    assetId = assetId,
                    symbol = token.symbol,
                    logoUrl = token.tokenLogoUrl,
                    tokenName = token.tokenName,
                    address = token.tokenContractAddress,
                    precision = token.decimals
                )
            }
    }

    override suspend fun getOkxAssets(chainId: ChainId?): List<CoreAsset> {
        return okxDao.getSupportedTokens(chainId).mapNotNull { token ->
            chainsRepository.getChain(token.chainId).assets.firstOrNull { asset ->
                asset.symbol.equals(token.symbol, ignoreCase = true)
            }
        }
    }

    override suspend fun getRemoteConfig(): Result<AppConfigRemote> {
        return kotlin.runCatching { remoteConfigFetcher.getAppConfig() }
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
        return chainsRepository.observeChainsPerAssetFlow(accountMetaId, assetId).map { chainsPerAsset ->
            val chains = chainsPerAsset.keys.map { mapChainLocalToChain(it) }
            val chainsById = chains.associateBy { it.id }
            val assets = chainsPerAsset.values.map { mapAssetLocalToAsset(chainsById, it) }
            val chainToAssetMap = chains.zip(assets).toMap()
            chainToAssetMap.filter { pair -> pair.value != null}
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

