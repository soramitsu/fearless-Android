package jp.co.soramitsu.wallet.api.domain

import jp.co.soramitsu.core.models.Asset
import java.math.BigInteger

interface ExistentialDepositUseCase {
    /**
     * The network minimum (existential deposit) of [chainAsset] in planks.
     *
     * Fails with [ExistentialDepositUnavailableException] when the minimum cannot be determined; an unknown
     * minimum is never reported as zero. Zero comes back only when the network says so, or for networks that
     * have no such minimum (EVM and TON).
     * Coroutine cancellation propagates; it is never an unavailable-minimum result.
     */
    suspend operator fun invoke(chainAsset: Asset): Result<BigInteger>
}

class ExistentialDepositUnavailableException(
    val chainAsset: Asset,
    cause: Throwable?
) : Exception("Existential deposit of ${chainAsset.symbol} on ${chainAsset.chainName} is unavailable", cause)
