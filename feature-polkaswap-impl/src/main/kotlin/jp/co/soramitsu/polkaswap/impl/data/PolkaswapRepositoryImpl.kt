package jp.co.soramitsu.polkaswap.impl.data

import java.math.BigInteger
import javax.inject.Inject
import jp.co.soramitsu.account.api.extrinsic.ExtrinsicService
import jp.co.soramitsu.common.data.network.config.PolkaswapRemoteConfig
import jp.co.soramitsu.common.data.network.config.RemoteConfigFetcher
import jp.co.soramitsu.common.data.network.runtime.model.QuoteResponse
import jp.co.soramitsu.common.utils.dexManager
import jp.co.soramitsu.common.utils.poolTBC
import jp.co.soramitsu.common.utils.poolXYK
import jp.co.soramitsu.common.utils.u32ArgumentFromStorageKey
import jp.co.soramitsu.fearless_utils.runtime.definitions.types.composite.Struct
import jp.co.soramitsu.fearless_utils.runtime.metadata.storage
import jp.co.soramitsu.fearless_utils.runtime.metadata.storageKey
import jp.co.soramitsu.polkaswap.api.data.PolkaswapRepository
import jp.co.soramitsu.polkaswap.api.models.Market
import jp.co.soramitsu.polkaswap.api.models.WithDesired
import jp.co.soramitsu.polkaswap.api.models.names
import jp.co.soramitsu.polkaswap.api.models.toFilters
import jp.co.soramitsu.polkaswap.api.presentation.models.SwapQuote
import jp.co.soramitsu.polkaswap.impl.data.network.blockchain.bindings.bindDexInfos
import jp.co.soramitsu.runtime.multiNetwork.ChainRegistry
import jp.co.soramitsu.runtime.multiNetwork.chain.model.ChainId
import jp.co.soramitsu.runtime.network.rpc.RpcCalls
import jp.co.soramitsu.runtime.storage.source.StorageDataSource
import kotlinx.coroutines.flow.Flow

class PolkaswapRepositoryImpl @Inject constructor(
    private val remoteConfigFetcher: RemoteConfigFetcher,
    private val remoteStorage: StorageDataSource,
    private val extrinsicService: ExtrinsicService,
    private val chainRegistry: ChainRegistry,
    private val rpcCalls: RpcCalls,
) : PolkaswapRepository {

    override suspend fun getAvailableDexes(chainId: ChainId): List<BigInteger> {
        val remoteDexes = dexInfos(chainId).keys
        val config = getPolkaswapConfig().availableDexIds.map { it.code }
        return remoteDexes.filter { it in config }
    }

    private suspend fun dexInfos(chainId: ChainId): Map<BigInteger, String?> {
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
        return remoteStorage.observe(
            chainId = chainId,
            keyBuilder = {
                val fromArgument = Struct.Instance(mapOf("code" to fromTokenId))
                val toArgument = Struct.Instance(mapOf("code" to toTokenId))
                it.metadata.poolXYK()?.storage("Reserves")?.storageKey(it, fromArgument, toArgument)
            }
        ) { scale, _ ->
            scale.orEmpty()
        }
    }

    override fun observePoolTBCReserves(chainId: ChainId, tokenId: String): Flow<String> {
        return remoteStorage.observe(
            chainId = chainId,
            keyBuilder = {
                it.metadata.poolTBC()?.storage("CollateralReserves")?.storageKey(it, Struct.Instance(mapOf("code" to tokenId)))
            }
        ) { scale, _ ->
            scale.orEmpty()
        }
    }

    override suspend fun getSwapQuote(
        chainId: ChainId,
        tokenFromId: String,
        tokenToId: String,
        amount: BigInteger,
        desired: WithDesired,
        curMarkets: List<Market>,
        dexId: Int
    ): QuoteResponse {
        return rpcCalls.liquidityProxyQuote(
            chainId,
            tokenFromId,
            tokenToId,
            amount,
            desired.backString,
            curMarkets.names(),
            curMarkets.toFilters(),
            dexId
        )
    }
}
