package jp.co.soramitsu.wallet.api.presentation.mixin.fee

import androidx.lifecycle.MutableLiveData
import java.math.BigDecimal
import java.math.BigInteger
import jp.co.soramitsu.common.base.errors.ValidationException
import jp.co.soramitsu.common.mixin.api.RetryPayload
import jp.co.soramitsu.common.resources.ResourceManager
import jp.co.soramitsu.common.utils.Event
import jp.co.soramitsu.common.validation.isCallMovedToAssetHub
import jp.co.soramitsu.common.validation.toFeeError
import jp.co.soramitsu.feature_wallet_api.R
import jp.co.soramitsu.wallet.api.data.mappers.mapFeeToFeeModel
import jp.co.soramitsu.wallet.impl.domain.TokenUseCase
import jp.co.soramitsu.wallet.impl.domain.model.Token
import jp.co.soramitsu.wallet.impl.domain.model.amountFromPlanks
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class FeeLoaderProvider(
    private val resourceManager: ResourceManager,
    private val tokenUseCase: TokenUseCase
) : FeeLoaderMixin.Presentation {

    override val feeLiveData = MutableLiveData<FeeStatus>()

    override val retryEvent = MutableLiveData<Event<RetryPayload>>()

    // Why the last fee failed, for screens that ask for it after the retry dialog (or without showing it).
    @Volatile
    private var failure: ValidationException? = null

    private val requestLock = Any()
    private var requestSequence = 0L
    private var requestJob: Job? = null

    override fun loadFee(
        coroutineScope: CoroutineScope,
        feeConstructor: suspend (Token) -> BigInteger,
        onRetryCancelled: () -> Unit,
        onComplete: ((FeeStatus?) -> Unit)?
    ) {
        val job = synchronized(requestLock) {
            val sequence = ++requestSequence
            val previousJob = requestJob
            val nextJob = coroutineScope.launch(Dispatchers.Default, start = CoroutineStart.LAZY) {
                var requestFailure: Throwable? = null
                val value = try {
                    val token = tokenUseCase.currentToken()
                    val feeInPlanks = feeConstructor(token)
                    currentCoroutineContext().ensureActive()
                    require(feeInPlanks.signum() >= 0) { "Fee must not be negative" }
                    val fee = token.amountFromPlanks(feeInPlanks)
                    FeeStatus.Loaded(mapFeeToFeeModel(fee, token))
                } catch (cancelled: CancellationException) {
                    throw cancelled
                } catch (error: Throwable) {
                    requestFailure = error
                    FeeStatus.Error
                }

                withContext(Dispatchers.Main) {
                    synchronized(requestLock) publication@ {
                        if (sequence != requestSequence || !isActive) return@publication
                        val error = requestFailure
                        val feeError = error?.toFeeError(resourceManager)
                        failure = feeError
                        feeLiveData.value = value

                        // LiveData observers may synchronously start another request.
                        if (sequence != requestSequence || !isActive) return@publication
                        if (feeError != null && error != null) {
                            val (title, message) = feeError
                            val onRetry = {
                                synchronized(requestLock) {
                                    if (sequence == requestSequence && coroutineScope.isActive) {
                                        loadFee(coroutineScope, feeConstructor, onRetryCancelled, onComplete)
                                    }
                                }
                            }
                            val onCancel = {
                                synchronized(requestLock) {
                                    if (sequence == requestSequence && coroutineScope.isActive) {
                                        requestSequence++
                                        requestJob?.cancel()
                                        onRetryCancelled()
                                    }
                                }
                            }
                            retryEvent.value = Event(
                                RetryPayload(
                                    title = title,
                                    message = message,
                                    onRetry = onRetry.takeUnless { error.isCallMovedToAssetHub() },
                                    onCancel = onCancel
                                )
                            )
                        }
                        if (sequence == requestSequence && isActive) onComplete?.invoke(value)
                    }
                }
            }
            requestJob = nextJob
            failure = null
            feeLiveData.value = FeeStatus.Loading
            // Invalidate before cancelling; a non-cooperative old producer cannot publish.
            previousJob?.cancel()
            nextJob
        }
        job.start()
    }

    override fun invalidate() {
        synchronized(requestLock) {
            requestSequence++
            val previousJob = requestJob
            requestJob = null
            failure = null
            feeLiveData.value = FeeStatus.Loading
            previousJob?.cancel()
        }
    }

    override fun requireFee(
        block: (BigDecimal) -> Unit,
        onError: (title: String, message: String) -> Unit
    ) {
        val feeStatus = feeLiveData.value
        val failed = failure

        if (feeStatus is FeeStatus.Loaded) {
            block(feeStatus.feeModel.fee)
        } else if (feeStatus is FeeStatus.Error && failed != null) {
            // The payout confirmation shows no retry dialog: say why instead of "not yet loaded".
            onError(failed.message, failed.explanation)
        } else {
            onError(
                resourceManager.getString(R.string.fee_not_yet_loaded_title),
                resourceManager.getString(R.string.fee_not_yet_loaded_message)
            )
        }
    }
}
