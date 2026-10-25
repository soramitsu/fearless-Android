package jp.co.soramitsu.staking.impl.domain.staking.unbond

import jp.co.soramitsu.common.utils.sumByBigInteger
import jp.co.soramitsu.core.extrinsic.ExtrinsicService
import jp.co.soramitsu.core.extrinsic.MutationExecutionGuard
import jp.co.soramitsu.fearless_utils.runtime.extrinsic.ExtrinsicBuilder
import jp.co.soramitsu.staking.api.domain.model.StakingState
import jp.co.soramitsu.staking.impl.domain.model.Unbonding
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import java.math.BigInteger

class UnbondInteractor(
    private val extrinsicService: ExtrinsicService
) {

    suspend fun estimateFee(
        stashState: StakingState,
        walletId: Long,
        isCurrent: () -> Boolean,
        formExtrinsic: suspend ExtrinsicBuilder.() -> Unit
    ): BigInteger = withContext(Dispatchers.IO) {
        currentCoroutineContext().ensureActive()
        if (!isCurrent()) throw CancellationException("Unbond review changed")
        val fee = extrinsicService.estimateFeeForWallet(stashState.chain, stashState.executionAddressId.copyOf(), walletId) {
            currentCoroutineContext().ensureActive()
            if (!isCurrent()) throw CancellationException("Unbond review changed")
            formExtrinsic.invoke(this)
            currentCoroutineContext().ensureActive()
            if (!isCurrent()) throw CancellationException("Unbond review changed")
        }
        currentCoroutineContext().ensureActive()
        if (!isCurrent()) throw CancellationException("Unbond review changed")
        fee
    }

    suspend fun unbond(
        stashState: StakingState,
        walletId: Long,
        isCurrent: () -> Boolean,
        authorize: (String) -> MutationExecutionGuard,
        formExtrinsic: suspend ExtrinsicBuilder.() -> Unit
    ): Result<String> = withContext(Dispatchers.IO) {
        currentCoroutineContext().ensureActive()
        if (!isCurrent()) throw CancellationException("Unbond review changed")
        val result = extrinsicService.submitAuthorizedExtrinsicForWallet(
            chain = stashState.chain,
            accountId = stashState.executionAddressId.copyOf(),
            walletId = walletId,
            authorize = { intent ->
                if (!isCurrent()) throw CancellationException("Unbond review changed")
                authorize(intent)
            }
        ) {
            currentCoroutineContext().ensureActive()
            if (!isCurrent()) throw CancellationException("Unbond review changed")
            formExtrinsic.invoke(this)
            currentCoroutineContext().ensureActive()
            if (!isCurrent()) throw CancellationException("Unbond review changed")
        }
        // The service returns failures in Result; cancellation must retain its original reason.
        (result.exceptionOrNull() as? CancellationException)?.let { throw it }
        result
    }

    // unbondings are always going from the oldest to newest so last in the list will be the newest one
    fun newestUnbondingAmount(unbondings: List<Unbonding>) = unbondings.last().amount

    fun allUnbondingsAmount(unbondings: List<Unbonding>): BigInteger = unbondings.sumByBigInteger(Unbonding::amount)
}
