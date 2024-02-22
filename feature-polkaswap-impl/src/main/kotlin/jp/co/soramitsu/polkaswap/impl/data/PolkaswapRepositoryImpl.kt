package jp.co.soramitsu.polkaswap.impl.data

import java.math.BigInteger
import javax.inject.Inject
import jp.co.soramitsu.account.api.domain.interfaces.AccountRepository
import jp.co.soramitsu.common.data.network.config.PolkaswapRemoteConfig
import jp.co.soramitsu.common.data.network.config.RemoteConfigFetcher
import jp.co.soramitsu.common.utils.dexManager
import jp.co.soramitsu.common.utils.poolTBC
import jp.co.soramitsu.common.utils.poolXYK
import jp.co.soramitsu.common.utils.u32ArgumentFromStorageKey
import jp.co.soramitsu.core.extrinsic.ExtrinsicService
import jp.co.soramitsu.core.rpc.RpcCalls
import jp.co.soramitsu.core.rpc.calls.liquidityProxyIsPathAvailable
import jp.co.soramitsu.core.rpc.calls.liquidityProxyListEnabledSourcesForPath
import jp.co.soramitsu.core.rpc.calls.liquidityProxyQuote
import jp.co.soramitsu.core.runtime.models.responses.QuoteResponse
import jp.co.soramitsu.polkaswap.api.data.PolkaswapRepository
import jp.co.soramitsu.polkaswap.api.models.Market
import jp.co.soramitsu.polkaswap.api.models.WithDesired
import jp.co.soramitsu.polkaswap.api.models.backStrings
import jp.co.soramitsu.polkaswap.api.models.toFilters
import jp.co.soramitsu.polkaswap.api.models.toMarkets
import jp.co.soramitsu.polkaswap.impl.data.network.blockchain.bindings.bindDexInfos
import jp.co.soramitsu.polkaswap.impl.data.network.blockchain.swap
import jp.co.soramitsu.runtime.multiNetwork.ChainRegistry
import jp.co.soramitsu.runtime.multiNetwork.chain.model.Chain
import jp.co.soramitsu.runtime.multiNetwork.chain.model.ChainId
import jp.co.soramitsu.runtime.storage.source.StorageDataSource
import jp.co.soramitsu.shared_utils.extensions.fromHex
import jp.co.soramitsu.shared_utils.runtime.definitions.types.composite.Struct
import jp.co.soramitsu.shared_utils.runtime.metadata.storage
import jp.co.soramitsu.shared_utils.runtime.metadata.storageKey
import jp.co.soramitsu.shared_utils.wsrpc.exception.RpcException
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flow

class PolkaswapRepositoryImpl @Inject constructor(
    private val remoteConfigFetcher: RemoteConfigFetcher,
    private val remoteStorage: StorageDataSource,
    private val extrinsicService: ExtrinsicService,
    private val chainRegistry: ChainRegistry,
    private val rpcCalls: RpcCalls,
    private val accountRepository: AccountRepository
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
        return rpcCalls.liquidityProxyIsPathAvailable(chainId, tokenFromId, tokenToId, dexId)
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
        return rpcCalls.liquidityProxyQuote(
            chainId,
            tokenFromId,
            tokenToId,
            amount,
            desired.backString,
            curMarkets.backStrings(),
            curMarkets.toFilters(),
            dexId
        )
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
            rpcCalls.liquidityProxyListEnabledSourcesForPath(chainId, dexId, tokenId1, tokenId2).toMarkets()
        } catch (e: RpcException) {
            listOf()
        }
    }
}
