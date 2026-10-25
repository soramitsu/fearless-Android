package jp.co.soramitsu.wallet.api.presentation.mixin.fee

import java.math.BigInteger
import jp.co.soramitsu.common.base.errors.ValidationException
import jp.co.soramitsu.common.resources.ResourceManager
import jp.co.soramitsu.common.validation.MovedToAssetHubException
import jp.co.soramitsu.common.validation.WaitForFeeCalculationException
import jp.co.soramitsu.common.validation.toFeeError
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

/**
 * The fee of a screen that estimates it eagerly in viewModelScope, where an uncaught failure would crash the app.
 * A failed estimation is kept until the user tries to continue, explained there, and started again from there unless
 * the call moved to Asset Hub and can never succeed.
 */
class FeeEstimation(private val resourceManager: ResourceManager) {

    private val retries = MutableStateFlow(0)

    /** Changes when a failed estimation has to run again; combine it with the inputs the fee depends on. */
    val retryFlow: StateFlow<Int> = retries

    private val failure = MutableStateFlow<ValidationException?>(null)

    /** The last estimation as the user reads its failure, or null while it succeeded or has not finished. */
    val failureFlow: StateFlow<ValidationException?> = failure

    val hasFailed: Boolean
        get() = failure.value != null

    /** @return the fee, or null when it could not be estimated */
    suspend fun estimate(feeEstimator: suspend () -> BigInteger): BigInteger? {
        val result = runCatching { feeEstimator() }
        val error = result.exceptionOrNull()
        if (error is CancellationException) throw error

        failure.value = error?.toFeeError(resourceManager)

        return result.getOrNull()
    }

    /**
     * Why there is no fee to continue with: the estimation failed, or it is still running.
     * Asking starts a failed estimation again, so that "try again later" holds for the next attempt.
     */
    fun missingFeeError(): ValidationException {
        val failed = failure.value ?: return WaitForFeeCalculationException(resourceManager)
        if (failed !is MovedToAssetHubException) retries.value++

        return failed
    }
}
