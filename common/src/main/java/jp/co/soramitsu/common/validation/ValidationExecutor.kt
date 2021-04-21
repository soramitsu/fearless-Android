package jp.co.soramitsu.common.validation

import androidx.lifecycle.MutableLiveData
import jp.co.soramitsu.common.base.TitleAndMessage
import jp.co.soramitsu.common.mixin.api.DefaultFailure
import jp.co.soramitsu.common.mixin.api.Validatable
import jp.co.soramitsu.common.resources.ResourceManager
import jp.co.soramitsu.common.utils.Event

typealias ProgressConsumer = (Boolean) -> Unit

fun MutableLiveData<Boolean>.progressConsumer(): ProgressConsumer = { value = it }

class ValidationExecutor(
    val resourceManager: ResourceManager,
) : Validatable {

    suspend fun <P, S> requireValid(
        validationSystem: ValidationSystem<P, S>,
        payload: P,
        errorDisplayer: (Throwable) -> Unit,
        validationFailureTransformer: (S) -> TitleAndMessage,
        progressConsumer: ProgressConsumer? = null,
        block: () -> Unit,
    ) {
        progressConsumer?.invoke(true)

        validationSystem.validate(payload)
            .unwrap(
                onValid = block,
                onFailure = {
                    progressConsumer?.invoke(false)

                    errorDisplayer(it)
                },
                onInvalid = {
                    progressConsumer?.invoke(false)

                    val (title, message) = validationFailureTransformer(it.reason)

                    validationFailureEvent.value = Event(
                        DefaultFailure(
                            level = it.level,
                            title = title,
                            message = message,
                            confirmWarning = {
                                progressConsumer?.invoke(true)

                                block()
                            }
                        )
                    )
                }
            )
    }

    override val validationFailureEvent = MutableLiveData<Event<DefaultFailure>>()
}
