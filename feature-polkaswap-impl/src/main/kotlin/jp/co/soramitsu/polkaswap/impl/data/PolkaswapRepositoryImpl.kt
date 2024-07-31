package jp.co.soramitsu.polkaswap.impl.data

import androidx.room.Transaction
import androidx.room.withTransaction
import java.math.BigDecimal
import java.math.BigInteger
import javax.inject.Inject
import jp.co.soramitsu.account.api.domain.interfaces.AccountRepository
import jp.co.soramitsu.account.api.domain.model.accountId
import jp.co.soramitsu.androidfoundation.format.addHexPrefix
import jp.co.soramitsu.androidfoundation.format.mapBalance
import jp.co.soramitsu.androidfoundation.format.safeCast
import jp.co.soramitsu.common.data.network.config.PolkaswapRemoteConfig
import jp.co.soramitsu.common.data.network.config.RemoteConfigFetcher
import jp.co.soramitsu.common.utils.Modules
import jp.co.soramitsu.common.utils.dexManager
import jp.co.soramitsu.common.utils.flowOf
import jp.co.soramitsu.common.utils.fromHex
import jp.co.soramitsu.common.utils.mapList
import jp.co.soramitsu.common.utils.poolTBC
import jp.co.soramitsu.common.utils.poolXYK
import jp.co.soramitsu.common.utils.u32ArgumentFromStorageKey
import jp.co.soramitsu.core.extrinsic.ExtrinsicService
import jp.co.soramitsu.core.models.Asset
import jp.co.soramitsu.core.rpc.RpcCalls
import jp.co.soramitsu.core.runtime.models.responses.QuoteResponse
import jp.co.soramitsu.core.utils.utilityAsset
import jp.co.soramitsu.coredb.AppDatabase
import jp.co.soramitsu.coredb.dao.PoolDao
import jp.co.soramitsu.coredb.model.BasicPoolLocal
import jp.co.soramitsu.coredb.model.UserPoolJoinedLocal
import jp.co.soramitsu.coredb.model.UserPoolJoinedLocalNullable
import jp.co.soramitsu.coredb.model.UserPoolLocal
import jp.co.soramitsu.polkaswap.api.data.PolkaswapRepository
import jp.co.soramitsu.polkaswap.api.data.PoolDataDto
import jp.co.soramitsu.polkaswap.api.domain.models.BasicPoolData
import jp.co.soramitsu.polkaswap.api.domain.models.CommonPoolData
import jp.co.soramitsu.polkaswap.api.domain.models.UserPoolData
import jp.co.soramitsu.polkaswap.api.models.Market
import jp.co.soramitsu.polkaswap.api.models.WithDesired
import jp.co.soramitsu.polkaswap.api.models.backStrings
import jp.co.soramitsu.polkaswap.api.models.toFilters
import jp.co.soramitsu.polkaswap.api.models.toMarkets
import jp.co.soramitsu.polkaswap.api.sorablockexplorer.BlockExplorerManager
import jp.co.soramitsu.polkaswap.impl.data.network.blockchain.bindings.bindDexInfos
import jp.co.soramitsu.polkaswap.impl.data.network.blockchain.depositLiquidity
import jp.co.soramitsu.polkaswap.impl.data.network.blockchain.initializePool
import jp.co.soramitsu.polkaswap.impl.data.network.blockchain.liquidityAdd
import jp.co.soramitsu.polkaswap.impl.data.network.blockchain.register
import jp.co.soramitsu.polkaswap.impl.data.network.blockchain.removeLiquidity
import jp.co.soramitsu.polkaswap.impl.data.network.blockchain.swap
import jp.co.soramitsu.polkaswap.impl.util.PolkaswapFormulas
import jp.co.soramitsu.runtime.ext.addressOf
import jp.co.soramitsu.runtime.multiNetwork.ChainRegistry
import jp.co.soramitsu.runtime.multiNetwork.chain.model.Chain
import jp.co.soramitsu.runtime.multiNetwork.chain.model.ChainId
import jp.co.soramitsu.runtime.network.subscriptionFlowCatching
import jp.co.soramitsu.runtime.storage.source.StorageDataSource
import jp.co.soramitsu.shared_utils.encrypt.keypair.Keypair
import jp.co.soramitsu.shared_utils.extensions.fromHex
import jp.co.soramitsu.shared_utils.extensions.toHexString
import jp.co.soramitsu.shared_utils.runtime.RuntimeSnapshot
import jp.co.soramitsu.shared_utils.runtime.definitions.types.composite.Struct
import jp.co.soramitsu.shared_utils.runtime.definitions.types.fromHex
import jp.co.soramitsu.shared_utils.runtime.metadata.module
import jp.co.soramitsu.shared_utils.runtime.metadata.storage
import jp.co.soramitsu.shared_utils.runtime.metadata.storageKey
import jp.co.soramitsu.shared_utils.scale.Schema
import jp.co.soramitsu.shared_utils.scale.dataType.uint32
import jp.co.soramitsu.shared_utils.scale.sizedByteArray
import jp.co.soramitsu.shared_utils.scale.uint128
import jp.co.soramitsu.shared_utils.ss58.SS58Encoder.toAccountId
import jp.co.soramitsu.shared_utils.wsrpc.exception.RpcException
import jp.co.soramitsu.shared_utils.wsrpc.executeAsync
import jp.co.soramitsu.shared_utils.wsrpc.mappers.nonNull
import jp.co.soramitsu.shared_utils.wsrpc.mappers.pojo
import jp.co.soramitsu.shared_utils.wsrpc.mappers.pojoList
import jp.co.soramitsu.shared_utils.wsrpc.mappers.scale
import jp.co.soramitsu.shared_utils.wsrpc.request.runtime.RuntimeRequest
import jp.co.soramitsu.shared_utils.wsrpc.request.runtime.storage.GetStorageRequest
import jp.co.soramitsu.shared_utils.wsrpc.request.runtime.storage.SubscribeStorageRequest
import jp.co.soramitsu.shared_utils.wsrpc.subscription.response.SubscriptionChange
import jp.co.soramitsu.wallet.impl.domain.interfaces.WalletRepository
import jp.co.soramitsu.wallet.impl.domain.model.amountFromPlanks
import jp.co.soramitsu.wallet.impl.domain.model.planksFromAmount
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.emitAll
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.mapNotNull
import kotlinx.coroutines.flow.onEach

class PolkaswapRepositoryImpl @Inject constructor(
    private val remoteConfigFetcher: RemoteConfigFetcher,
    private val remoteStorage: StorageDataSource,
    private val extrinsicService: ExtrinsicService,
    private val chainRegistry: ChainRegistry,
    private val rpcCalls: RpcCalls,
    private val accountRepository: AccountRepository,
    private val walletRepository: WalletRepository,
    private val blockExplorerManager: BlockExplorerManager,
    private val poolDao: PoolDao,
    private val db: AppDatabase,
) : PolkaswapRepository {

    override suspend fun getAvailableDexes(chainId: ChainId): List<BigInteger> {
        val remoteDexes = runCatching { dexInfos(chainId).keys }.getOrNull() ?: emptySet()
        val config = getPolkaswapConfig().availableDexIds.map { it.code }
        return remoteDexes.filter { it in config }
    }

    private suspend fun dexInfos(chainId: ChainId): Map<BigInteger, String?> {
        waitForChain(chainId)
        return remoteStorage.queryByPrefix(
            prefixKeyBuilder = { it.metadata.dexManager()?.storage("DEXInfos")?.storageKey() },
            keyExtractor = { it.u32ArgumentFromStorageKey() },
            chainId = chainId
        ) { scale, runtime, _ ->
            scale?.let { bindDexInfos(it, runtime) }
        }
    }

    private suspend fun getPolkaswapConfig(): PolkaswapRemoteConfig {
        return remoteConfigFetcher.getPolkaswapConfig()
    }

    override fun observePoolXYKReserves(chainId: ChainId, fromTokenId: String, toTokenId: String): Flow<String> {
        return flow { emit(waitForChain(chainId)) }.flatMapLatest {
            remoteStorage.observe(
                chainId = chainId,
                keyBuilder = {
                    val from = Struct.Instance(
                        mapOf("code" to fromTokenId.fromHex().toList().map { it.toInt().toBigInteger() })
                    )
                    val to = Struct.Instance(
                        mapOf("code" to toTokenId.fromHex().toList().map { it.toInt().toBigInteger() })
                    )
                    it.metadata.poolXYK()?.storage("Reserves")?.storageKey(it, from, to)
                }
            ) { scale, _ ->
                scale.orEmpty()
            }
        }
    }

    override fun observePoolTBCReserves(chainId: ChainId, tokenId: String): Flow<String> {
        return flow { emit(waitForChain(chainId)) }.flatMapLatest {
            remoteStorage.observe(
                chainId = chainId,
                keyBuilder = {
                    val token = Struct.Instance(
                        mapOf("code" to tokenId.fromHex().toList().map { it.toInt().toBigInteger() })
                    )
                    it.metadata.poolTBC()?.storage("CollateralReserves")?.storageKey(it, token)
                }
            ) { scale, _ ->
                scale.orEmpty()
            }
        }
    }

    // Because if we get chain from the ChainRegistry, it will emit a chain
    // only after runtime for this chain will be ready
    private suspend fun waitForChain(chainId: String): Chain {
        return chainRegistry.getChain(chainId)
    }

    override suspend fun isPairAvailable(
        chainId: ChainId,
        tokenFromId: String,
        tokenToId: String,
        dexId: Int
    ): Boolean {
        val request = RuntimeRequest(
            method = "liquidityProxy_isPathAvailable",
            params = listOf(
                dexId,
                tokenFromId,
                tokenToId
            )
        )

        return chainRegistry.awaitConnection(chainId).socketService.executeAsync(request, mapper = pojo<Boolean>().nonNull())
    }

    override suspend fun getSwapQuote(
        chainId: ChainId,
        tokenFromId: String,
        tokenToId: String,
        amount: BigInteger,
        desired: WithDesired,
        curMarkets: List<Market>,
        dexId: Int
    ): QuoteResponse? {
        return try {
            val request = RuntimeRequest(
                method = "liquidityProxy_quote",
                params = listOf(
                    dexId,
                    tokenFromId,
                    tokenToId,
                    amount.toString(),
                    desired.backString,
                    curMarkets.backStrings(),
                    curMarkets.toFilters()
                )
            )

            chainRegistry.awaitConnection(chainId).socketService.executeAsync(request, mapper = pojo<QuoteResponse>()).result
        } catch (e: Exception) {
            null
        }
    }

    override suspend fun estimateSwapFee(
        chainId: ChainId,
        dexId: Int,
        inputAssetId: String,
        outputAssetId: String,
        amount: BigInteger,
        limit: BigInteger,
        filter: String,
        markets: List<String>,
        desired: WithDesired
    ): BigInteger {

        val chain = chainRegistry.getChain(chainId)
        return extrinsicService.estimateFee(chain) {
            swap(dexId, inputAssetId, outputAssetId, amount, limit, filter, markets, desired)
        }
    }

    override suspend fun swap(
        chainId: ChainId,
        dexId: Int,
        inputAssetId: String,
        outputAssetId: String,
        amount: BigInteger,
        limit: BigInteger,
        filter: String,
        markets: List<String>,
        desired: WithDesired
    ): Result<String> {
        val chain = chainRegistry.getChain(chainId)
        val accountId = accountRepository.getSelectedMetaAccount().substrateAccountId
        return extrinsicService.submitExtrinsic(chain, accountId) {
            swap(dexId, inputAssetId, outputAssetId, amount, limit, filter, markets, desired)
        }
    }

    override suspend fun getAvailableSources(chainId: ChainId, tokenId1: String, tokenId2: String, dexes: List<Int>): Map<Int, List<Market>> {
        return dexes.associateWith { dexId ->
            getEnabledMarkets(chainId, dexId, tokenId1, tokenId2)
        }
    }

    private suspend fun getEnabledMarkets(chainId: ChainId, dexId: Int, tokenId1: String, tokenId2: String): List<Market> {
        return try {
            val request = RuntimeRequest(
                "liquidityProxy_listEnabledSourcesForPath",
                listOf(dexId, tokenId1, tokenId2)
            )
            return chainRegistry.awaitConnection(chainId).socketService.executeAsync(request, mapper = pojoList<String>().nonNull()).toMarkets()
        } catch (e: RpcException) {
            listOf()
        }
    }

    fun ByteArray.mapAssetId() = this.toList().map { it.toInt().toBigInteger() }
    fun String.mapAssetId() = this.fromHex().mapAssetId()
    fun String.mapCodeToken() = Struct.Instance(
        mapOf("code" to this.mapAssetId())
    )

    fun RuntimeSnapshot.reservesKeyToken(baseTokenId: String): String =
        this.metadata.module(Modules.POOL_XYK)
            .storage("Reserves")
            .storageKey(
                this,
                baseTokenId.mapCodeToken(),
            )

    suspend fun getStorageHex(chainId: ChainId, storageKey: String): String? =
        chainRegistry.awaitConnection(chainId).socketService.executeAsync(
            request = GetStorageRequest(listOf(storageKey)),
            mapper = pojo<String>(),
        ).result

    suspend fun getStateKeys(chainId: ChainId, partialKey: String): List<String> =
        chainRegistry.awaitConnection(chainId).socketService.executeAsync(
            request = StateKeys(listOf(partialKey)),
            mapper = pojoList<String>(),
        ).result ?: emptyList()

    class StateKeys(params: List<Any>) : RuntimeRequest("state_getKeys", params)

    fun ByteArray.mapCodeToken() = Struct.Instance(
        mapOf("code" to this.mapAssetId())
    )

    object PoolPropertiesResponse : Schema<PoolPropertiesResponse>() {
        val first by sizedByteArray(32)
        val second by sizedByteArray(32)
    }

    suspend fun getPoolReserveAccount(
        chainId: ChainId,
        baseTokenId: String,
        tokenId: ByteArray
    ): ByteArray? {
        val runtimeOrNull = chainRegistry.getRuntimeOrNull(chainId)
        val storageKey = runtimeOrNull?.metadata
            ?.module(Modules.POOL_XYK)
            ?.storage("Properties")?.storageKey(
                runtimeOrNull,
                baseTokenId.mapCodeToken(),
                tokenId.mapCodeToken(),
            )
            ?: return null

        return chainRegistry.awaitConnection(chainId).socketService.executeAsync(
            request = GetStorageRequest(listOf(storageKey)),
            mapper = scale(PoolPropertiesResponse),
        )
            .result
            ?.let { storage ->
                storage[storage.schema.first]
            }
    }

    fun String.assetIdFromKey() = this.takeLast(64).addHexPrefix()

    object TotalIssuance : Schema<TotalIssuance>() {
        val totalIssuance by uint128()
    }

    suspend fun getPoolTotalIssuances(
        chainId: ChainId,
        reservesAccountId: ByteArray,
    ): BigInteger? {
        val runtimeOrNull = chainRegistry.getRuntimeOrNull(chainId)
        val storageKey = runtimeOrNull?.metadata?.module(Modules.POOL_XYK)
            ?.storage("TotalIssuances")
            ?.storageKey(runtimeOrNull, reservesAccountId)
            ?: return null
        return chainRegistry.awaitConnection(chainId).socketService.executeAsync(
            request = GetStorageRequest(listOf(storageKey)),
            mapper = scale(TotalIssuance),
        )
            .result
            ?.let { storage ->
                storage[storage.schema.totalIssuance]
            }
    }

    override fun getPoolStrategicBonusAPY(reserveAccountOfPool: String): Double? {
        val tempApy = blockExplorerManager.getTempApy(reserveAccountOfPool)
//        println("!!! blockExplorerManager getPoolStrategicBonusAPY for address $reserveAccountOfPool = $tempApy")
        return tempApy
    }

//    private suspend fun updatePoolsSbApy() {
//        println("!!! call blockExplorerManager.updatePoolsSbApy()")
//        blockExplorerManager.updatePoolsSbApy()
//    }
override suspend fun getBasicPool(chainId: ChainId, baseTokenId: String, targetTokenId: String): BasicPoolData? {
    val poolLocal = poolDao.getBasicPool(baseTokenId, targetTokenId) ?: return null

    val soraChain = chainRegistry.getChain(chainId)
    val wallet = accountRepository.getSelectedMetaAccount()
    val accountId = wallet.accountId(soraChain)
    val soraAssets = soraChain.assets.mapNotNull { chainAsset ->
        accountId?.let {
            walletRepository.getAsset(
                metaId = wallet.id,
                accountId = accountId,
                chainAsset = chainAsset,
                minSupportedVersion = null
            )
        }
    }

    val baseAsset = soraAssets.firstOrNull {
        it.token.configuration.currencyId == baseTokenId
    } ?: return null
    val targetAsset = soraAssets.firstOrNull {
        it.token.configuration.currencyId == targetTokenId
    }

    return BasicPoolData(
        baseToken = baseAsset,
        targetToken = targetAsset,
        baseReserves = poolLocal.reserveBase,
        targetReserves = poolLocal.reserveTarget,
        totalIssuance = poolLocal.totalIssuance,
        reserveAccount = poolLocal.reservesAccount,
        sbapy = getPoolStrategicBonusAPY(poolLocal.reservesAccount)
    )
}

    override suspend fun getBasicPools(chainId: ChainId): List<BasicPoolData> {
        println("!!!  getBasicPools() start")
        val runtimeOrNull = chainRegistry.getRuntimeOrNull(chainId)
        val storage = runtimeOrNull?.metadata
            ?.module(Modules.POOL_XYK)
            ?.storage("Reserves")

        val list = mutableListOf<BasicPoolData>()

        val soraChain = chainRegistry.getChain(chainId)

        val wallet = accountRepository.getSelectedMetaAccount()

        val accountId = wallet.accountId(soraChain)
        val soraAssets = soraChain.assets.mapNotNull { chainAsset ->
            accountId?.let {
                walletRepository.getAsset(
                    metaId = wallet.id,
                    accountId = accountId,
                    chainAsset = chainAsset,
                    minSupportedVersion = null
                )
            }
        }

        println("!!!  getBasicPools() soraAssets size: ${soraAssets.size}")

        soraAssets.forEach { asset ->
            val currencyId = asset.token.configuration.currencyId
            val key = currencyId?.let { runtimeOrNull?.reservesKeyToken(it) }
            key?.let {
                getStateKeys(chainId, it).forEach { storageKey ->
                    val targetToken = storageKey.assetIdFromKey()
                    getStorageHex(chainId, storageKey)?.let { storageHex ->
                        storage?.type?.value
                            ?.fromHex(runtimeOrNull, storageHex)
                            ?.safeCast<List<BigInteger>>()?.let { reserves ->

                                val reserveAccount = getPoolReserveAccount(
                                    chainId,
                                    currencyId,
                                    targetToken.fromHex()
                                )
                                val total = reserveAccount?.let {
                                    getPoolTotalIssuances(chainId, it)
                                }?.let {
                                    mapBalance(it, asset.token.configuration.precision)
                                }
                                val targetAsset = soraAssets.firstOrNull { it.token.configuration.currencyId == targetToken }
                                val reserveAccountAddress = reserveAccount?.let { soraChain.addressOf(it) } ?: ""

                                val element = BasicPoolData(
                                    baseToken = asset,
                                    targetToken = targetAsset,
                                    baseReserves = mapBalance(reserves[0], asset.token.configuration.precision),
                                    targetReserves = mapBalance(reserves[1], asset.token.configuration.precision),
                                    totalIssuance = total ?: BigDecimal.ZERO,
                                    reserveAccount = reserveAccountAddress,
                                    sbapy = getPoolStrategicBonusAPY(reserveAccountAddress)
                                )

                                println("!!!  getBasicPools() list.add(BasicPoolData: $element")
                                list.add(
                                    element
                                )
                            }
                    }
                }
            }
        }

        println("!!!  getBasicPools() return list.size = ${list.size}")

        return list
    }

    private fun subscribeAccountPoolProviders(
        chainId: ChainId,
        address: String,
        reservesAccount: ByteArray,
    ): Flow<String> = flow {
        val poolProvidersKey =
            chainRegistry.getRuntimeOrNull(chainId)?.let {
                it.metadata.module(Modules.POOL_XYK)
                    .storage("TotalIssuances")
                    .storageKey(
                        it,
                        reservesAccount,
                        address.toAccountId()
                    )
            } ?: error("!!! subscribeAccountPoolProviders poolProvidersKey is null")
        val poolProvidersFlow = chainRegistry.getConnection(chainId).socketService.subscriptionFlowCatching(
            SubscribeStorageRequest(poolProvidersKey),
            "state_unsubscribeStorage",
        ).map {
            it.map { it.storageChange().getSingleChange().orEmpty() }
        }.map {
            it.getOrNull().orEmpty()
        }
        emitAll(poolProvidersFlow)
    }

    override suspend fun getUserPoolData(
        chainId: ChainId,
        address: String,
        baseTokenId: String,
        targetTokenId: ByteArray
    ): PoolDataDto? {
        val reserves = getPairWithXorReserves(chainId, baseTokenId, targetTokenId)
        val totalIssuanceAndProperties =
            getPoolTotalIssuanceAndProperties(chainId, baseTokenId, targetTokenId, address)

        if (reserves == null || totalIssuanceAndProperties == null) {
            return null
        }
        val reservesAccount = chainRegistry.getChain(chainId).addressOf(totalIssuanceAndProperties.third)

        return PoolDataDto(
            baseTokenId,
            targetTokenId.toHexString(true),
            reserves.first,
            reserves.second,
            totalIssuanceAndProperties.first,
            totalIssuanceAndProperties.second,
            reservesAccount,
        )
    }

    override suspend fun calcAddLiquidityNetworkFee(
        chainId: ChainId,
        address: String,
        tokenBase: Asset,
        tokenTarget: Asset,
        tokenBaseAmount: BigDecimal,
        tokenTargetAmount: BigDecimal,
        pairEnabled: Boolean,
        pairPresented: Boolean,
        slippageTolerance: Double
    ): BigDecimal? {
        val amountFromMin = PolkaswapFormulas.calculateMinAmount(tokenBaseAmount, slippageTolerance)
        val amountToMin = PolkaswapFormulas.calculateMinAmount(tokenTargetAmount, slippageTolerance)
        val dexId = getPoolBaseTokenDexId(chainId, tokenBase.currencyId)
        val chain = chainRegistry.getChain(chainId)

        val fee = extrinsicService.estimateFee(chain) {
            liquidityAdd(
                dexId = dexId,
                baseTokenId = tokenBase.currencyId,
                targetTokenId = tokenTarget.currencyId,
                pairPresented = pairPresented,
                pairEnabled = pairEnabled,
                tokenBaseAmount = tokenBase.planksFromAmount(tokenBaseAmount),
                tokenTargetAmount = tokenTarget.planksFromAmount(tokenTargetAmount),
                amountBaseMin = tokenBase.planksFromAmount(amountFromMin),
                amountTargetMin = tokenTarget.planksFromAmount(amountToMin),
            )
        }

        val feeToken = chain.utilityAsset
        return feeToken?.amountFromPlanks(fee)
    }

    override suspend fun calcRemoveLiquidityNetworkFee(
        chainId: ChainId,
        tokenBase: Asset,
        tokenTarget: Asset,
    ): BigDecimal? {
        val chain = chainRegistry.getChain(chainId)
        val baseTokenId = tokenBase.currencyId ?: return null
        val targetTokenId = tokenTarget.currencyId ?: return null

        val fee = extrinsicService.estimateFee(chain) {
            removeLiquidity(
                dexId = getPoolBaseTokenDexId(chainId, baseTokenId),
                outputAssetIdA = baseTokenId,
                outputAssetIdB = targetTokenId,
                markerAssetDesired = tokenBase.planksFromAmount(BigDecimal.ONE),
                outputAMin = tokenBase.planksFromAmount(BigDecimal.ONE),
                outputBMin = tokenTarget.planksFromAmount(BigDecimal.ONE),
            )
        }
        val feeToken = chain.utilityAsset
        return feeToken?.amountFromPlanks(fee)
    }

    override suspend fun getPoolBaseTokenDexId(chainId: ChainId, tokenId: String?): Int {
        return getPoolBaseTokens(chainId).first {
            it.second == tokenId
        }.first
    }

    private suspend fun getPoolBaseTokens(chainId: ChainId): List<Pair<Int, String>> {
        val runtimeSnapshot = chainRegistry.getRuntimeOrNull(chainId)
        val metadataStorage = runtimeSnapshot?.metadata
            ?.module("DEXManager")
            ?.storage("DEXInfos")
        val partialKey = metadataStorage
            ?.storageKey() ?: error("getPoolBaseTokenDexId storageKey not supported")

        return chainRegistry.awaitConnection(chainId).socketService.executeAsync(
            request = StateKeys(listOf(partialKey)),
            mapper = pojoList<String>().nonNull()
        ).let { storageKeys ->
            storageKeys.mapNotNull { storageKey ->
                chainRegistry.awaitConnection(chainId).socketService.executeAsync(
                    request = GetStorageRequest(listOf(storageKey)),
                    mapper = pojo<String>().nonNull()
                ).let { storage ->
                    val storageType = metadataStorage.type.value!!
                    val storageRawData =
                        storageType.fromHex(runtimeSnapshot, storage)
                    (storageRawData as? Struct.Instance)?.let { instance ->
                        instance.mapToToken("baseAssetId")?.let { token ->
                            storageKey.takeInt32() to token
                        }
                    }
                }
            }
        }
    }

    fun Struct.Instance.mapToToken(field: String) =
        this.get<Struct.Instance>(field)?.getTokenId()?.toHexString(true)

    fun String.takeInt32() = uint32.fromHex(this.takeLast(8)).toInt()

    private suspend fun getPoolTotalIssuanceAndProperties(
        chainId: ChainId,
        baseTokenId: String,
        tokenId: ByteArray,
        address: String
    ): Triple<BigInteger, BigInteger, ByteArray>? {
        return getPoolReserveAccount(chainId, baseTokenId, tokenId)?.let { account ->
            getPoolTotalIssuances(
                chainId,
                account
            )?.let {
                val provider = getPoolProviders(
                    chainId,
                    account,
                    address
                )
                Triple(it, provider, account)
            }
        }
    }

    object PoolProviders : Schema<PoolProviders>() {
        val poolProviders by uint128()
    }

    private suspend fun getPoolProviders(
        chainId: ChainId,
        reservesAccountId: ByteArray,
        currentAddress: String
    ): BigInteger {
        val storageKey =
            chainRegistry.getRuntimeOrNull(chainId)?.let {
                it.metadata.module(Modules.POOL_XYK)
                    .storage("PoolProviders").storageKey(
                        it,
                        reservesAccountId,
                        currentAddress.toAccountId()
                    )
            } ?: return BigInteger.ZERO
        return runCatching {
            chainRegistry.awaitConnection(chainId).socketService.executeAsync(
                request = GetStorageRequest(listOf(storageKey)),
                mapper = scale(PoolProviders),
            )
                .let { storage ->
                    storage.result?.let {
                        it[it.schema.poolProviders]
                    } ?: BigInteger.ZERO
                }
        }.getOrElse {
            it.printStackTrace()
            throw it
        }
    }

    object ReservesResponse : Schema<ReservesResponse>() {
        val first by uint128()
        val second by uint128()
    }

    private suspend fun getPairWithXorReserves(
        chainId: ChainId,
        baseTokenId: String,
        tokenId: ByteArray
    ): Pair<BigInteger, BigInteger>? {
        val storageKey =
            chainRegistry.getRuntimeOrNull(chainId)?.reservesKey(baseTokenId, tokenId)
                ?: return null
        return try {
             chainRegistry.awaitConnection(chainId).socketService.executeAsync(
                request = GetStorageRequest(listOf(storageKey)),
                mapper = scale(ReservesResponse),
            )
                .result
                ?.let { storage ->
                    storage[storage.schema.first] to storage[storage.schema.second]
                }
        } catch (e: Exception) {
            println("!!! getPairWithXorReserves error = ${e.message}")

            e.printStackTrace()
            throw e
        }
    }

//    override suspend fun getPoolOfAccount(address: String?, tokenFromId: String, tokenToId: String, chainId: String): CommonUserPoolData? {
//        return getPoolsOfAccount(address, tokenFromId, tokenToId, chainId).firstOrNull {
//            it.user.address == address
//        }
//    }

//    suspend fun getPoolsOfAccount(address: String?, tokenFromId: String, tokenToId: String, chainId: String): List<CommonUserPoolData> {
////        val runtimeOrNull = chainRegistry.getRuntimeOrNull(soraMainChainId)
////        val socketService = chainRegistry.getConnection(chainId).socketService
////        socketService.executeAsyncCatching()
//        val tokensPair: List<Pair<String, List<ByteArray>>>? = address?.let {
//            getUserPoolsTokenIds(it)
//        }
//
//        val pools = mutableListOf<CommonUserPoolData>()
//
//        tokensPair?.forEach { (baseTokenId, tokensId) ->
//            tokensId.mapNotNull { tokenId ->
//                getUserPoolData(address, baseTokenId, tokenId)
//            }.forEach pool@{ poolDataDto ->
//                val metaId = accountRepository.getSelectedLightMetaAccount().id
//                val assets = walletRepository.getAssets(metaId)
//                val token = assets.firstOrNull {
//                    it.token.configuration.currencyId == poolDataDto.assetId
//                } ?: return@pool
//                val baseToken = assets.firstOrNull {
//                    it.token.configuration.currencyId == baseTokenId
//                } ?: return@pool
//                val xorPrecision = baseToken?.token?.configuration?.precision ?: 0
//                val tokenPrecision = token?.token?.configuration?.precision ?: 0
//
//                val apy = getPoolStrategicBonusAPY(poolDataDto.reservesAccount)
//
//                val basePooled = PolkaswapFormulas.calculatePooledValue(
//                    mapBalance(
//                        poolDataDto.reservesFirst,
//                        xorPrecision
//                    ),
//                    mapBalance(
//                        poolDataDto.poolProvidersBalance,
//                        xorPrecision
//                    ),
//                    mapBalance(
//                        poolDataDto.totalIssuance,
//                        xorPrecision
//                    ),
//                    baseToken?.token?.configuration?.precision,
//                )
//                val secondPooled = PolkaswapFormulas.calculatePooledValue(
//                    mapBalance(
//                        poolDataDto.reservesSecond,
//                        tokenPrecision
//                    ),
//                    mapBalance(
//                        poolDataDto.poolProvidersBalance,
//                        xorPrecision
//                    ),
//                    mapBalance(
//                        poolDataDto.totalIssuance,
//                        xorPrecision
//                    ),
//                    token?.token?.configuration?.precision,
//                )
//                val share = PolkaswapFormulas.calculateShareOfPoolFromAmount(
//                    mapBalance(
//                        poolDataDto.poolProvidersBalance,
//                        xorPrecision
//                    ),
//                    mapBalance(
//                        poolDataDto.totalIssuance,
//                        xorPrecision
//                    ),
//                )
//                val userPoolData = CommonUserPoolData(
//                    basic = BasicPoolData(
//                        baseToken = baseToken,
//                        targetToken = token,
//                        baseReserves = mapBalance(
//                            poolDataDto.reservesFirst,
//                            xorPrecision
//                        ),
//                        targetReserves = mapBalance(
//                            poolDataDto.reservesSecond,
//                            tokenPrecision
//                        ),
//                        totalIssuance = mapBalance(
//                            poolDataDto.totalIssuance,
//                            xorPrecision
//                        ),
//                        reserveAccount = poolDataDto.reservesAccount,
//                        sbapy = apy,
//                    ),
//                    user = UserPoolData(
////                        address = address,
//                        basePooled = basePooled,
//                        targetPooled = secondPooled,
//                        share,
//                        mapBalance(
//                            poolDataDto.poolProvidersBalance,
//                            xorPrecision
//                        ),
//                    ),
//                )
//                pools.add(userPoolData)
//            }
//        }
//        return pools
//    }

    fun RuntimeSnapshot.reservesKey(baseTokenId: String, tokenId: ByteArray): String =
        this.metadata.module(Modules.POOL_XYK)
            .storage("Reserves")
            .storageKey(
                this,
                baseTokenId.mapCodeToken(),
                tokenId.mapCodeToken(),
            )

    @Suppress("UNCHECKED_CAST")
    fun SubscriptionChange.storageChange(): SubscribeStorageResult {
        val result = params.result as? Map<*, *> ?: throw IllegalArgumentException("${params.result} is not a valid storage result")

        val block = result["block"] as? String ?: throw IllegalArgumentException("$result is not a valid storage result")
        val changes = result["changes"] as? List<List<String>> ?: throw IllegalArgumentException("$result is not a valid storage result")

        return SubscribeStorageResult(block, changes)
    }

    private fun subscribeToPoolData(
        chainId: ChainId,
        baseTokenId: String,
        tokenId: ByteArray,
        reservesAccount: ByteArray,
    ): Flow<String> = flow {
        val reservesKey =
            chainRegistry.getRuntimeOrNull(chainId)?.reservesKey(baseTokenId, tokenId)

        val reservesFlow = reservesKey?.let {
            chainRegistry.getConnection(chainId).socketService.subscriptionFlowCatching(
                SubscribeStorageRequest(reservesKey),
                "state_unsubscribeStorage",
            ).map {
                it.map { it.storageChange().getSingleChange().orEmpty() }
            }.map {
                it.getOrNull().orEmpty()
            }
        } ?: emptyFlow()

        val totalIssuanceKey =
            chainRegistry.getRuntimeOrNull(chainId)?.let {
                it.metadata.module(Modules.POOL_XYK)
                    .storage("TotalIssuances")
                    .storageKey(it, reservesAccount)
            }
        val totalIssuanceFlow = totalIssuanceKey?.let {
            chainRegistry.getConnection(chainId).socketService.subscriptionFlowCatching(
                SubscribeStorageRequest(totalIssuanceKey),
                "state_unsubscribeStorage",
            ).map {
                it.getOrNull()?.storageChange()?.getSingleChange().orEmpty()
            }
        } ?: emptyFlow()

        val resultFlow = reservesFlow
            .combine(totalIssuanceFlow) { reservesString, totalIssuanceString ->
                (reservesString + totalIssuanceString).take(5)
            }

        emitAll(resultFlow)
    }

    // changes are in format [[storage key, value], [..], ..]
    class SubscribeStorageResult(val block: String, val changes: List<List<String?>>) {
        fun getSingleChange() = changes.first()[1]
    }

    fun Struct.Instance.getTokenId() = get<List<*>>("code")
        ?.map { (it as BigInteger).toByte() }
        ?.toByteArray()


    suspend fun getUserPoolsTokenIdsKeys(chainId: ChainId, address: String): List<String> {
        val accountPoolsKey = chainRegistry.getRuntimeOrNull(chainId)?.accountPoolsKey(address)
        return runCatching {
            chainRegistry.awaitConnection(chainId).socketService.executeAsync(
                request = StateKeys(listOfNotNull(accountPoolsKey)),
                mapper = pojoList<String>().nonNull()
            )
        }.onFailure {
            println("!!! getUserPoolsTokenIdsKeys error: ${it.message}")
            it.printStackTrace()
        }
            .getOrThrow()
    }

    suspend fun getUserPoolsTokenIds(
        chainId: ChainId,
        address: String
    ): List<Pair<String, List<ByteArray>>> {
        return runCatching {
            val storageKeys = getUserPoolsTokenIdsKeys(chainId, address)
            storageKeys.map { storageKey ->
                chainRegistry.awaitConnection(chainId).socketService.executeAsync(
                    request = GetStorageRequest(listOf(storageKey)),
                    mapper = pojo<String>().nonNull(),
                )
                    .let { storage ->
                        val storageType =
                            chainRegistry.getRuntimeOrNull(chainId)?.metadata?.module(Modules.POOL_XYK)
                                ?.storage("AccountPools")?.type?.value!!
                        val storageRawData =
                            storageType.fromHex(chainRegistry.getRuntimeOrNull(chainId)!!, storage)
                        val tokens: List<ByteArray> = if (storageRawData is List<*>) {
                            storageRawData.filterIsInstance<Struct.Instance>()
                                .mapNotNull { struct ->
                                    struct.getTokenId()
                                }
                        } else {
                            emptyList()
                        }
                        storageKey.assetIdFromKey() to tokens
                    }
            }
        }.getOrThrow()
    }

    override suspend fun observeRemoveLiquidity(
        chainId: ChainId,
        tokenBase: Asset,
        tokenTarget: Asset,
        markerAssetDesired: BigDecimal,
        firstAmountMin: BigDecimal,
        secondAmountMin: BigDecimal
    ): Result<String>? {
        val soraChain = accountRepository.getChain(chainId)
        val accountId = accountRepository.getSelectedMetaAccount().substrateAccountId
        val baseTokenId = tokenBase.currencyId ?: return null
        val targetTokenId = tokenTarget.currencyId ?: return null

        return extrinsicService.submitExtrinsic(
            chain = soraChain,
            accountId = accountId
        ) {
            removeLiquidity(
                dexId = getPoolBaseTokenDexId(chainId, baseTokenId),
                outputAssetIdA = baseTokenId,
                outputAssetIdB = targetTokenId,
                markerAssetDesired = tokenBase.planksFromAmount(markerAssetDesired),
                outputAMin = tokenBase.planksFromAmount(firstAmountMin),
                outputBMin = tokenTarget.planksFromAmount(secondAmountMin),
            )
        }
    }

    override suspend fun observeAddLiquidity(
        chainId: ChainId,
        address: String,
        keypair: Keypair,
        tokenBase: Asset,
        tokenTarget: Asset,
        amountBase: BigDecimal,
        amountTarget: BigDecimal,
        pairEnabled: Boolean,
        pairPresented: Boolean,
        slippageTolerance: Double
    ): Result<String>? {
        val amountFromMin = PolkaswapFormulas.calculateMinAmount(amountBase, slippageTolerance)
        val amountToMin = PolkaswapFormulas.calculateMinAmount(amountTarget, slippageTolerance)
        val dexId = getPoolBaseTokenDexId(chainId, tokenBase.currencyId)
        val soraChain = accountRepository.getChain(chainId)
        val accountId = accountRepository.getSelectedMetaAccount().substrateAccountId

        val baseTokenId = tokenBase.currencyId
        val targetTokenId = tokenTarget.currencyId
        if (baseTokenId == null || targetTokenId == null) return null

        return extrinsicService.submitExtrinsic(
            chain = soraChain,
            accountId = accountId,
            useBatchAll = !pairPresented
        ) {
            if (!pairPresented) {
                if (!pairEnabled) {
                    register(
                        dexId = dexId,
                        baseAssetId = baseTokenId,
                        targetAssetId = targetTokenId
                    )
                }
                initializePool(
                    dexId = dexId,
                    baseAssetId = baseTokenId,
                    targetAssetId = targetTokenId
                )
            }

            depositLiquidity(
                dexId = dexId,
                baseAssetId = baseTokenId,
                targetAssetId = targetTokenId,
                baseAssetAmount = mapBalance(amountBase, tokenBase.precision),
                targetAssetAmount = mapBalance(amountTarget, tokenTarget.precision),
                amountFromMin = mapBalance(amountFromMin, tokenBase.precision),
                amountToMin = mapBalance(amountToMin, tokenTarget.precision)
            )
        }
    }

    override suspend fun updateAccountPools(chainId: ChainId, address: String) {
        println("!!! call blockExplorerManager.updateAccountPools()")
        blockExplorerManager.updatePoolsSbApy()

        val pools = mutableListOf<UserPoolJoinedLocal>()

        val assets = chainRegistry.getChain(chainId).assets

        val tokenIds = getUserPoolsTokenIds(chainId, address)
        println("!!! call blockExplorerManager.updateAccountPools() tokenIds = ${tokenIds.size}")
        tokenIds.forEach { (baseTokenId, tokensId) ->

            val baseToken = assets.firstOrNull {
                it.currencyId == baseTokenId
            } ?: return@forEach

            val xorPrecision = baseToken.precision

            tokensId.mapNotNull { tokenId ->
                getUserPoolData(chainId, address, baseTokenId, tokenId)
            }.forEach pool@{ poolDataDto ->
                val token = assets.firstOrNull {
                    it.currencyId == poolDataDto.assetId
                } ?: return@pool
                val tokenPrecision = token.precision

                val basicPoolLocal = BasicPoolLocal(
                    tokenIdBase = baseTokenId,
                    tokenIdTarget = poolDataDto.assetId,
                    reserveBase = mapBalance(
                        poolDataDto.reservesFirst,
                        xorPrecision
                    ),
                    reserveTarget = mapBalance(
                        poolDataDto.reservesSecond,
                        tokenPrecision
                    ),
                    totalIssuance = mapBalance(
                        poolDataDto.totalIssuance,
                        xorPrecision
                    ),
                    reservesAccount = poolDataDto.reservesAccount,
                )

                val userPoolLocal = UserPoolLocal(
                    accountAddress = address,
                    userTokenIdBase = baseTokenId,
                    userTokenIdTarget = poolDataDto.assetId,
                    poolProvidersBalance = mapBalance(
                        poolDataDto.poolProvidersBalance,
                        xorPrecision
                    )
                )

                pools.add(
                    UserPoolJoinedLocal(
                        userPoolLocal = userPoolLocal,
                        basicPoolLocal = basicPoolLocal,
                    )
                )
            }
        }

        db.withTransaction {
            println("!!! updateAccountPools: poolDao.clearTable(address)")
            poolDao.clearTable(address)
            println("!!! updateAccountPools: poolDao.insertBasicPools() size = ${pools.map {
                it.basicPoolLocal
            }.size}")
            poolDao.insertBasicPools(
                pools.map {
                    it.basicPoolLocal
                }
            )
            println("!!! updateAccountPools: poolDao.insertUSERPools() size = ${pools.map {
                it.userPoolLocal
            }.size}")
            poolDao.insertUserPools(
                pools.map {
                    it.userPoolLocal
                }
            )
        }
    }

    override suspend fun updateBasicPools(chainId: ChainId) {
        println("!!! pswapRepo updateBasicPools")
        val runtimeOrNull = chainRegistry.getRuntimeOrNull(chainId)
        val storage = runtimeOrNull?.metadata
            ?.module(Modules.POOL_XYK)
            ?.storage("Reserves")

        val list = mutableListOf<BasicPoolLocal>()

        val soraChain = chainRegistry.getChain(chainId)
        val assets = soraChain.assets

        getPoolBaseTokens(chainId).forEach { (dexId, tokenId) ->
            val asset = assets.firstOrNull { it.currencyId == tokenId } ?: return@forEach
            val key = runtimeOrNull?.reservesKeyToken(tokenId) ?: return@forEach

            getStateKeys(chainId, key).forEach { storageKey ->
                val targetToken = storageKey.assetIdFromKey()
                getStorageHex(chainId, storageKey)?.let { storageHex ->
                    storage?.type?.value
                        ?.fromHex(runtimeOrNull, storageHex)
                        ?.safeCast<List<BigInteger>>()?.let { reserves ->

                            val reserveAccount = getPoolReserveAccount(
                                chainId,
                                tokenId,
                                targetToken.fromHex()
                            )

                            val total = reserveAccount?.let {
                                getPoolTotalIssuances(chainId, it)
                            }?.let {
                                mapBalance(it, asset.precision)
                            }

                            val reserveAccountAddress = reserveAccount?.let { soraChain.addressOf(it) } ?: ""

                            list.add(
                                BasicPoolLocal(
                                    tokenIdBase = tokenId,
                                    tokenIdTarget = targetToken,
                                    reserveBase = mapBalance(reserves[0], asset.precision),
                                    reserveTarget = mapBalance(reserves[1], asset.precision),
                                    totalIssuance = total ?: BigDecimal.ZERO,
                                    reservesAccount = reserveAccountAddress,
                                )
                            )
                        }
                }
            }
        }

        val minus = poolDao.getBasicPools().filter { db ->
            list.find { it.tokenIdBase == db.tokenIdBase && it.tokenIdTarget == db.tokenIdTarget } == null
        }
        poolDao.deleteBasicPools(minus)
        println("!!! pswapRepo insertBasicPools(list) size = ${list.size}")
        poolDao.insertBasicPools(list)
    }
    val assetsFlow = accountRepository.selectedMetaAccountFlow()
        .distinctUntilChanged { old, new -> old.id != new.id }
        .flatMapLatest { meta ->
            walletRepository.assetsFlow(meta)
        }.mapList { it.asset }

    @OptIn(FlowPreview::class)
    override fun subscribePool(address: String, baseTokenId: String, targetTokenId: String): Flow<CommonPoolData> {
        return combine(
            poolDao.subscribePool(address, baseTokenId, targetTokenId),
            assetsFlow
        ) { pool, assets ->
            mapPoolLocalToData(pool, assets)
        }
            .mapNotNull { it }
            .debounce(500)

//        return poolDao.subscribePool(address, baseTokenId, targetTokenId).mapNotNull {
//            val metaId = accountRepository.getSelectedLightMetaAccount().id
//            val assets = walletRepository.getAssets(metaId)
//            mapPoolLocalToData(it, assets)
//        }.debounce(500)
    }

    @OptIn(FlowPreview::class)
    override fun subscribePools(address: String): Flow<List<CommonPoolData>> {
        println("!!! repoImpl call subscribePools for address: $address")

        return combine(
            poolDao.subscribeAllPools(address),
            assetsFlow
        ) { pools, assets ->
            println("!!! repoImpl subscribePools pools: ${pools.size}")
            val mapNotNull = pools.mapNotNull { poolLocal ->
                mapPoolLocalToData(poolLocal, assets)
            }
            println("!!! repoImpl subscribePools mapNotNull pools: ${mapNotNull.size}")
            mapNotNull
        }
            .debounce(500)

//        return poolDao.subscribeAllPools(address).map { pools ->
//            println("!!! repoImpl subscribePools pools: ${pools.size}")
//            val metaId = accountRepository.getSelectedLightMetaAccount().id
//            val assets = walletRepository.getAssets(metaId)
//            val mapNotNull = pools.mapNotNull { poolLocal ->
//                mapPoolLocalToData(poolLocal, assets)
//            }
//            println("!!! repoImpl subscribePools mapNotNull pools: ${mapNotNull.size}")
//            mapNotNull
//        }.debounce(500)
            .onEach {
                println("!!! repoImpl .debounce(500) pools: ${it.size}")
            }
    }

    fun RuntimeSnapshot.accountPoolsKey(address: String): String =
        this.metadata.module(Modules.POOL_XYK)
            .storage("AccountPools")
            .storageKey(this, address.toAccountId())

    private fun mapPoolLocalToData(
        poolLocal: UserPoolJoinedLocalNullable,
        assets: List<jp.co.soramitsu.wallet.impl.domain.model.Asset>
    ): CommonPoolData? {
        val baseToken = assets.firstOrNull {
            it.token.configuration.currencyId == poolLocal.basicPoolLocal.tokenIdBase
        } ?: return null
        val token = assets.firstOrNull {
            it.token.configuration.currencyId == poolLocal.basicPoolLocal.tokenIdTarget
        } ?: return null

        val basicPoolData = BasicPoolData(
            baseToken = baseToken,
            targetToken = token,
            baseReserves = poolLocal.basicPoolLocal.reserveBase,
            targetReserves = poolLocal.basicPoolLocal.reserveTarget,
            totalIssuance = poolLocal.basicPoolLocal.totalIssuance,
            reserveAccount = poolLocal.basicPoolLocal.reservesAccount,
            sbapy = getPoolStrategicBonusAPY(poolLocal.basicPoolLocal.reservesAccount),
        )

        val userPoolData = poolLocal.userPoolLocal?.let { userPoolLocal ->
            val basePooled = PolkaswapFormulas.calculatePooledValue(
                poolLocal.basicPoolLocal.reserveBase,
                userPoolLocal.poolProvidersBalance,
                poolLocal.basicPoolLocal.totalIssuance,
                baseToken.token.configuration.precision,
            )
            val secondPooled = PolkaswapFormulas.calculatePooledValue(
                poolLocal.basicPoolLocal.reserveTarget,
                userPoolLocal.poolProvidersBalance,
                poolLocal.basicPoolLocal.totalIssuance,
                token.token.configuration.precision,
            )
            val share = PolkaswapFormulas.calculateShareOfPoolFromAmount(
                userPoolLocal.poolProvidersBalance,
                poolLocal.basicPoolLocal.totalIssuance,
            )
            UserPoolData(
                basePooled = basePooled,
                targetPooled = secondPooled,
                poolShare = share,
                poolProvidersBalance = userPoolLocal.poolProvidersBalance,
            )
        }
        return CommonPoolData(
            basic = basicPoolData,
            user = userPoolData,
        )
    }

}
