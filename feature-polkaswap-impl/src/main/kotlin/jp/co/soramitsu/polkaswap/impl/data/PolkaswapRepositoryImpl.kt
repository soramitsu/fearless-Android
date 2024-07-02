package jp.co.soramitsu.polkaswap.impl.data

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
import jp.co.soramitsu.common.utils.poolTBC
import jp.co.soramitsu.common.utils.poolXYK
import jp.co.soramitsu.common.utils.u32ArgumentFromStorageKey
import jp.co.soramitsu.core.extrinsic.ExtrinsicService
import jp.co.soramitsu.core.rpc.RpcCalls
import jp.co.soramitsu.core.runtime.models.responses.QuoteResponse
import jp.co.soramitsu.polkaswap.api.data.PolkaswapRepository
import jp.co.soramitsu.polkaswap.api.domain.models.BasicPoolData
import jp.co.soramitsu.polkaswap.api.models.Market
import jp.co.soramitsu.polkaswap.api.models.WithDesired
import jp.co.soramitsu.polkaswap.api.models.backStrings
import jp.co.soramitsu.polkaswap.api.models.toFilters
import jp.co.soramitsu.polkaswap.api.models.toMarkets
import jp.co.soramitsu.polkaswap.impl.data.network.blockchain.bindings.bindDexInfos
import jp.co.soramitsu.polkaswap.impl.data.network.blockchain.swap
import jp.co.soramitsu.runtime.ext.addressOf
import jp.co.soramitsu.runtime.multiNetwork.ChainRegistry
import jp.co.soramitsu.runtime.multiNetwork.chain.model.Chain
import jp.co.soramitsu.runtime.multiNetwork.chain.model.ChainId
import jp.co.soramitsu.runtime.multiNetwork.chain.model.soraMainChainId
import jp.co.soramitsu.runtime.storage.source.StorageDataSource
import jp.co.soramitsu.shared_utils.extensions.fromHex
import jp.co.soramitsu.shared_utils.runtime.RuntimeSnapshot
import jp.co.soramitsu.shared_utils.runtime.definitions.types.composite.Struct
import jp.co.soramitsu.shared_utils.runtime.definitions.types.fromHex
import jp.co.soramitsu.shared_utils.runtime.metadata.module
import jp.co.soramitsu.shared_utils.runtime.metadata.storage
import jp.co.soramitsu.shared_utils.runtime.metadata.storageKey
import jp.co.soramitsu.shared_utils.scale.Schema
import jp.co.soramitsu.shared_utils.scale.sizedByteArray
import jp.co.soramitsu.shared_utils.scale.uint128
import jp.co.soramitsu.shared_utils.wsrpc.exception.RpcException
import jp.co.soramitsu.shared_utils.wsrpc.executeAsync
import jp.co.soramitsu.shared_utils.wsrpc.mappers.nonNull
import jp.co.soramitsu.shared_utils.wsrpc.mappers.pojo
import jp.co.soramitsu.shared_utils.wsrpc.mappers.pojoList
import jp.co.soramitsu.shared_utils.wsrpc.mappers.scale
import jp.co.soramitsu.shared_utils.wsrpc.request.runtime.RuntimeRequest
import jp.co.soramitsu.shared_utils.wsrpc.request.runtime.storage.GetStorageRequest
import jp.co.soramitsu.wallet.impl.domain.interfaces.WalletRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flow

class PolkaswapRepositoryImpl @Inject constructor(
    private val remoteConfigFetcher: RemoteConfigFetcher,
    private val remoteStorage: StorageDataSource,
    private val extrinsicService: ExtrinsicService,
    private val chainRegistry: ChainRegistry,
    private val rpcCalls: RpcCalls,
    private val accountRepository: AccountRepository,
    private val walletRepository: WalletRepository

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
        return flow { emit(waitForChain(chainId)) }.flatMapLatest {  remoteStorage.observe(
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
        }}
    }

    override fun observePoolTBCReserves(chainId: ChainId, tokenId: String): Flow<String> {
        return flow { emit(waitForChain(chainId)) }.flatMapLatest { remoteStorage.observe(
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
    }}

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

    suspend fun getStorageHex(storageKey: String): String? =
        chainRegistry.awaitConnection(soraMainChainId).socketService.executeAsync(
            request = GetStorageRequest(listOf(storageKey)),
            mapper = pojo<String>(),
        ).result

    suspend fun getStateKeys(partialKey: String): List<String> =
        chainRegistry.awaitConnection(soraMainChainId).socketService.executeAsync(
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
        baseTokenId: String,
        tokenId: ByteArray
    ): ByteArray? {
        val runtimeOrNull = chainRegistry.getRuntimeOrNull(soraMainChainId)
        val storageKey = runtimeOrNull?.metadata
            ?.module(Modules.POOL_XYK)
            ?.storage("Properties")?.storageKey(
                runtimeOrNull,
                baseTokenId.mapCodeToken(),
                tokenId.mapCodeToken(),
            )
            ?: return null

        return chainRegistry.awaitConnection(soraMainChainId).socketService.executeAsync(
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
        reservesAccountId: ByteArray,
    ): BigInteger? {
        val runtimeOrNull = chainRegistry.getRuntimeOrNull(soraMainChainId)
        val storageKey = runtimeOrNull?.metadata?.module(Modules.POOL_XYK)
                ?.storage("TotalIssuances")
                ?.storageKey(runtimeOrNull, reservesAccountId)
            ?: return null
        return chainRegistry.awaitConnection(soraMainChainId).socketService.executeAsync(
            request = GetStorageRequest(listOf(storageKey)),
            mapper = scale(TotalIssuance),
        )
            .result
            ?.let { storage ->
                storage[storage.schema.totalIssuance]
            }
    }

    protected fun getPoolStrategicBonusAPY(
        reserveAccountOfPool: String,
    ): Double? = null
//        blockExplorerManager.getTempApy(reserveAccountOfPool)

    override suspend fun getBasicPools(): List<BasicPoolData> {
        println("!!!  getBasicPools() start")
        val runtimeOrNull = chainRegistry.getRuntimeOrNull(soraMainChainId)
        val storage = runtimeOrNull?.metadata
            ?.module(Modules.POOL_XYK)
            ?.storage("Reserves")
        println("!!!  getBasicPools() storage = $storage")
        val list = mutableListOf<BasicPoolData>()

        val soraChain = chainRegistry.getChain(soraMainChainId)
        val assets = soraChain.assets

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
            println("!!!  getBasicPools() soraAssets.forEach { asset ${asset.token.configuration.symbol}")

            val currencyId = asset.token.configuration.currencyId
            val key = currencyId?.let { runtimeOrNull?.reservesKeyToken(it) }
            key?.let {
                getStateKeys(it).forEach { storageKey ->
                    val targetToken = storageKey.assetIdFromKey()
                    getStorageHex(storageKey)?.let { storageHex ->
                        storage?.type?.value
                            ?.fromHex(runtimeOrNull, storageHex)
                            ?.safeCast<List<BigInteger>>()?.let { reserves ->

                                val reserveAccount = getPoolReserveAccount(
                                    currencyId,
                                    targetToken.fromHex()
                                )
                                val total = reserveAccount?.let {
                                    getPoolTotalIssuances(it)
                                }?.let {
                                    mapBalance(it, asset.token.configuration.precision)
                                }
                                val targetAsset = assets.firstOrNull { it.currencyId == targetToken }
                                val reserveAccountAddress = reserveAccount?.let { soraChain.addressOf(it) } ?: ""

                                val element = BasicPoolData(
                                    asset,
                                    targetAsset,
                                    mapBalance(reserves[0], asset.token.configuration.precision),
                                    mapBalance(reserves[1], asset.token.configuration.precision),
                                    total ?: BigDecimal.ZERO,
                                    reserveAccountAddress,
                                    sbapy = getPoolStrategicBonusAPY(reserveAccountAddress)
                                )

                                println("!!!  getBasicPools() list.add(BasicPoolData: $element")
                                list.add(
                                    element
                                )
                                // todo remove
                                if (list.size > 10) return list
                            }
                    }
                }
            }
        }

        println("!!!  getBasicPools() return list.size = ${list.size}")

        return list
    }
}
