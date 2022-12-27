package jp.co.soramitsu.runtime.extrinsic

import jp.co.soramitsu.fearless_utils.runtime.definitions.types.generics.Era
import jp.co.soramitsu.runtime.multiNetwork.chain.model.ChainId
import jp.co.soramitsu.runtime.network.rpc.RpcCalls
import jp.co.soramitsu.runtime.repository.ChainStateRepository
import java.lang.Integer.min

private const val FALLBACK_MAX_HASH_COUNT = 250
private const val MAX_FINALITY_LAG = 5
private const val FALLBACK_PERIOD = 6 * 1000
private const val MORTAL_PERIOD = 5 * 60 * 1000


class Mortality(val era: Era.Mortal, val blockHash: String)


class MortalityConstructor(
    private val rpcCalls: RpcCalls,
    private val chainStateRepository: ChainStateRepository
) {

    suspend fun constructMortality(chainId: ChainId): Mortality {
        val finalizedHash = rpcCalls.getFinalizedHead(chainId)

        val bestHeader = rpcCalls.getBlockHeader(chainId)
        val finalizedHeader = rpcCalls.getBlockHeader(chainId, finalizedHash)

        val currentHeader = bestHeader.parentHash?.let { rpcCalls.getBlockHeader(chainId, it) } ?: bestHeader

        val currentNumber = currentHeader.number
        val finalizedNumber = finalizedHeader.number

        val startBlockNumber = if (currentNumber - finalizedNumber > MAX_FINALITY_LAG) currentNumber else finalizedNumber

        val blockHashCount = chainStateRepository.blockHashCount(chainId)?.toInt()
        val blockTime = chainStateRepository.expectedBlockTimeInMillis(chainId).toInt() // TODO Babe.ExpectedBlockTime may be null for some chains

        val mortalPeriod = MORTAL_PERIOD / blockTime + MAX_FINALITY_LAG

        val unmappedPeriod = min(blockHashCount ?: FALLBACK_MAX_HASH_COUNT, mortalPeriod)

        val era = Era.getEraFromBlockPeriod(startBlockNumber, unmappedPeriod)
        val eraBlockNumber = ((startBlockNumber - era.phase) / era.period) * era.period + era.phase

        val eraBlockHash = rpcCalls.getBlockHash(chainId, eraBlockNumber.toBigInteger())

        return Mortality(era, eraBlockHash)
    }
}
