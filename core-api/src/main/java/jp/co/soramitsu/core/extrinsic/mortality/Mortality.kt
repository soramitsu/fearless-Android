package jp.co.soramitsu.core.extrinsic.mortality

import jp.co.soramitsu.core.models.ChainId
import jp.co.soramitsu.core.models.IChain
import jp.co.soramitsu.core.rpc.RpcCalls
import jp.co.soramitsu.core.rpc.normalizeRpcBlockHash
import jp.co.soramitsu.fearless_utils.extensions.fromHex
import jp.co.soramitsu.fearless_utils.runtime.definitions.types.generics.Era
import kotlinx.coroutines.flow.Flow
import java.math.BigInteger
import kotlin.math.max

interface IChainStateRepository {
    suspend fun expectedBlockTimeInMillis(
        chainId: ChainId,
        defaultTime: BigInteger = DEFAULT_BLOCK_TIME_MILLIS
    ): BigInteger

    suspend fun blockHashCount(chainId: ChainId): BigInteger?

    suspend fun currentBlock(chainId: ChainId): BigInteger

    fun currentBlockNumberFlow(chainId: ChainId): Flow<BigInteger>
}

private val DEFAULT_BLOCK_TIME_MILLIS = BigInteger.valueOf(6_000)
private val MORTAL_PERIOD_MILLIS = BigInteger.valueOf(5 * 60 * 1_000)
private const val MIN_MORTAL_PERIOD_BLOCKS = 4
private const val MAX_MORTAL_PERIOD_BLOCKS = 1 shl 16

class MortalityConstructor(
    private val rpcCalls: RpcCalls,
    private val chainStateRepository: IChainStateRepository
) {
    suspend fun construct(chain: IChain): Mortality {
        val genesisHash = chain.id.fromHex()

        return runCatching {
            val currentBlock = chainStateRepository.currentBlock(chain.id)
            val blockHash = rpcCalls.getBlockHash(chain.id, currentBlock)

            constructMortalEra(
                currentBlock = currentBlock,
                blockHashCount = chainStateRepository.blockHashCount(chain.id),
                expectedBlockTimeInMillis = chainStateRepository.expectedBlockTimeInMillis(chain.id),
                blockHash = blockHash
            )
        }.getOrNull() ?: Mortality(
            era = Era.Immortal,
            blockHash = genesisHash
        )
    }
}

data class Mortality(
    val era: Era,
    val blockHash: ByteArray
)

internal fun constructMortalEra(
    currentBlock: BigInteger,
    blockHashCount: BigInteger?,
    expectedBlockTimeInMillis: BigInteger,
    blockHash: String
): Mortality? {
    if (currentBlock.signum() < 0 || expectedBlockTimeInMillis.signum() <= 0) return null

    val currentBlockInt = currentBlock.toIntOrNull() ?: return null
    val periodInBlocks = mortalityPeriodInBlocks(expectedBlockTimeInMillis, blockHashCount) ?: return null
    val normalizedBlockHash = runCatching {
        normalizeRpcBlockHash(blockHash, "block hash")
    }.getOrNull() ?: return null

    return Mortality(
        era = Era.getEraFromBlockPeriod(currentBlockInt, periodInBlocks),
        blockHash = normalizedBlockHash.fromHex()
    )
}

private fun mortalityPeriodInBlocks(
    expectedBlockTimeInMillis: BigInteger,
    blockHashCount: BigInteger?
): Int? {
    val targetBlocks = MORTAL_PERIOD_MILLIS
        .divideCeil(expectedBlockTimeInMillis)
        .coerceAtLeast(BigInteger.ONE)
        .coerceAtMost(BigInteger.valueOf(MAX_MORTAL_PERIOD_BLOCKS.toLong()))
        .toInt()

    var period = nextPowerOfTwo(targetBlocks)
        .coerceIn(MIN_MORTAL_PERIOD_BLOCKS, MAX_MORTAL_PERIOD_BLOCKS)

    val maxPeriodFromHashCount = blockHashCount?.let {
        if (it < BigInteger.valueOf(MIN_MORTAL_PERIOD_BLOCKS.toLong())) return null
        it.coerceAtMost(BigInteger.valueOf(MAX_MORTAL_PERIOD_BLOCKS.toLong())).toInt()
    }

    if (maxPeriodFromHashCount != null && period > maxPeriodFromHashCount) {
        period = Integer.highestOneBit(maxPeriodFromHashCount)
        if (period < MIN_MORTAL_PERIOD_BLOCKS) return null
    }

    return period
}

private fun BigInteger.divideCeil(divisor: BigInteger): BigInteger {
    val (quotient, remainder) = divideAndRemainder(divisor)
    return if (remainder.signum() == 0) quotient else quotient + BigInteger.ONE
}

private fun nextPowerOfTwo(value: Int): Int {
    if (value <= 1) return 1
    return max(1, Integer.highestOneBit(value - 1) shl 1)
}

private fun BigInteger.toIntOrNull(): Int? {
    return runCatching { intValueExact() }.getOrNull()
}
