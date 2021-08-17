package jp.co.soramitsu.feature_wallet_impl.presentation.common.mixin

import androidx.lifecycle.MutableLiveData
import jp.co.soramitsu.common.mixin.api.RetryPayload
import jp.co.soramitsu.common.resources.ResourceManager
import jp.co.soramitsu.common.utils.Event
import jp.co.soramitsu.feature_wallet_api.data.mappers.mapFeeToFeeModel
import jp.co.soramitsu.feature_wallet_api.domain.interfaces.WalletInteractor
import jp.co.soramitsu.feature_wallet_api.domain.model.Asset
import jp.co.soramitsu.feature_wallet_api.presentation.mixin.FeeLoaderMixin
import jp.co.soramitsu.feature_wallet_api.presentation.mixin.FeeStatus
import jp.co.soramitsu.feature_wallet_impl.R
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import java.math.BigDecimal

class FeeLoaderProvider(
    private val interactor: WalletInteractor,
    private val resourceManager: ResourceManager
) : FeeLoaderMixin.Presentation {

    override val feeLiveData = MutableLiveData<FeeStatus>()

    override val retryEvent = MutableLiveData<Event<RetryPayload>>()

    override fun loadFee(
        coroutineScope: CoroutineScope,
        feeConstructor: suspend (Asset) -> BigDecimal,
        onRetryCancelled: () -> Unit
    ) {
        feeLiveData.value = FeeStatus.Loading

        coroutineScope.launch(Dispatchers.Default) {
            val asset = interactor.currentAssetFlow().first()
            val token = asset.token

            val feeResult = runCatching {
                feeConstructor(asset)
            }

            val value = if (feeResult.isSuccess) {
                val feeModel = mapFeeToFeeModel(feeResult.getOrThrow(), token)

                FeeStatus.Loaded(feeModel)
            } else {
                retryEvent.postValue(
                    Event(
                        RetryPayload(
                            title = resourceManager.getString(R.string.choose_amount_network_error),
                            message = resourceManager.getString(R.string.choose_amount_error_fee),
                            onRetry = { loadFee(coroutineScope, feeConstructor, onRetryCancelled) },
                            onCancel = onRetryCancelled
                        )
                    )
                )

                FeeStatus.Error
            }

            feeLiveData.postValue(value)
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
