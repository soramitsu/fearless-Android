package jp.co.soramitsu.wallet.api.presentation.mixin.fee

import androidx.lifecycle.MutableLiveData
import java.math.BigDecimal
import java.math.BigInteger
import jp.co.soramitsu.common.mixin.api.RetryPayload
import jp.co.soramitsu.common.resources.ResourceManager
import jp.co.soramitsu.common.utils.Event
import jp.co.soramitsu.feature_wallet_api.R
import jp.co.soramitsu.wallet.api.data.mappers.mapFeeToFeeModel
import jp.co.soramitsu.wallet.impl.domain.TokenUseCase
import jp.co.soramitsu.wallet.impl.domain.model.Token
import jp.co.soramitsu.wallet.impl.domain.model.amountFromPlanks
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class FeeLoaderProvider(
    private val resourceManager: ResourceManager,
    private val tokenUseCase: TokenUseCase
) : FeeLoaderMixin.Presentation {

    override val feeLiveData = MutableLiveData<FeeStatus>()

    override val retryEvent = MutableLiveData<Event<RetryPayload>>()

    override fun loadFee(
        coroutineScope: CoroutineScope,
        feeConstructor: suspend (Token) -> BigInteger,
        onRetryCancelled: () -> Unit,
        onComplete: ((FeeStatus?) -> Unit)?
    ) {
        feeLiveData.value = FeeStatus.Loading

        coroutineScope.launch(Dispatchers.Default) {
            val token = tokenUseCase.currentToken()

            val feeResult = runCatching {
                feeConstructor(token)
            }

            val value = if (feeResult.isSuccess) {
                val feeInPlanks = feeResult.getOrThrow()
                val fee = token.amountFromPlanks(feeInPlanks)
                val feeModel = mapFeeToFeeModel(fee, token)

                FeeStatus.Loaded(feeModel)
            } else if (feeResult.exceptionOrNull() is CancellationException) {
                null
            } else {
                retryEvent.postValue(
                    Event(
                        RetryPayload(
                            title = resourceManager.getString(R.string.choose_amount_network_error),
                            message = resourceManager.getString(R.string.choose_amount_error_fee),
                            onRetry = { loadFee(coroutineScope, feeConstructor, onRetryCancelled, onComplete) },
                            onCancel = onRetryCancelled
                        )
                    )
                )

                feeResult.exceptionOrNull()?.printStackTrace()

                FeeStatus.Error
            }
            value?.let {
                feeLiveData.postValue(value)
            }
            withContext(Dispatchers.Main) {
                onComplete?.invoke(value)
            }
        }
    }

    override fun requireFee(
        block: (BigDecimal) -> Unit,
        onError: (title: String, message: String) -> Unit
    ) {
        val feeStatus = feeLiveData.value

        if (feeStatus is FeeStatus.Loaded) {
            block(feeStatus.feeModel.fee)
        } else {
            onError(
                resourceManager.getString(R.string.fee_not_yet_loaded_title),
                resourceManager.getString(R.string.fee_not_yet_loaded_message)
            )
        }
    }
}
