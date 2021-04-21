package jp.co.soramitsu.feature_staking_impl.presentation.common.validation

import androidx.lifecycle.LiveData
import jp.co.soramitsu.common.mixin.api.DefaultFailure
import jp.co.soramitsu.common.mixin.api.RetryPayload
import jp.co.soramitsu.common.mixin.api.Validatable
import jp.co.soramitsu.common.resources.ResourceManager
import jp.co.soramitsu.common.utils.Event

class ValidationExecutor(
    val resourceManager: ResourceManager
) : Validatable {

    private fun <P, S> requireValid(
        validationSystem: ValidationSystem<P, S>,
        payload: P,
        loadingIndicator: MutableLiveData<Boolean>? = null,
        block: () -> Unit
    ) {
        loadingIndicator?.value = true

        launch {
            validationSystem.validate(payload)
                .unwrap(
                    onValid = block,
                    onFailure = {
                        loadingIndicator?.value = false

                        showError(it)
                    },
                    onInvalid = {
                        loadingIndicator?.value = false

                        retryEvent.value = Event(
                            RetryPayload(
                                title = resourceManager.getString(R.string.choose_amount_network_error),
                                message = resourceManager.getString(R.string.choose_amount_error_balance),
                                onRetry = { requireValid(validationSystem, payload, loadingIndicator, block) }
                            )
                        )
                    }
                )
        }
    }

    override val validationFailureEvent: LiveData<Event<DefaultFailure>>
        get() = TODO("Not yet implemented")

    override fun validationWarningConfirmed() {
        TODO("Not yet implemented")
    }

    override val retryEvent: LiveData<Event<RetryPayload>>
        get() = TODO("Not yet implemented")
}

