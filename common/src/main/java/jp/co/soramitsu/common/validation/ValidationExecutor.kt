package jp.co.soramitsu.common.validation

import androidx.lifecycle.MutableLiveData
import kotlinx.coroutines.CancellationException
import jp.co.soramitsu.common.base.TitleAndMessage
import jp.co.soramitsu.common.mixin.api.DefaultFailure
import jp.co.soramitsu.common.mixin.api.Validatable
import jp.co.soramitsu.common.resources.ResourceManager
import jp.co.soramitsu.common.utils.Event

typealias ProgressConsumer = (Boolean) -> Unit

fun MutableLiveData<Boolean>.progressConsumer(): ProgressConsumer = { value = it }

class ValidationExecutor(
    val resourceManager: ResourceManager
) : Validatable {

    suspend fun <P, S> requireValid(
        validationSystem: ValidationSystem<P, S>,
        payload: P,
        errorDisplayer: (Throwable) -> Unit,
        validationFailureTransformer: (S) -> TitleAndMessage,
        progressConsumer: ProgressConsumer? = null,
        autoFixPayload: (original: P, failureStatus: S) -> P = { original, _ -> original },
        isCurrent: () -> Boolean = { true },
        block: (P) -> Unit
    ) {
        if (!isCurrent()) return
        progressConsumer?.invoke(true)
        if (!isCurrent()) return

        val result = validationSystem.validate(payload)
        (result.exceptionOrNull() as? CancellationException)?.let {
            if (isCurrent()) progressConsumer?.invoke(false)
            throw it
        }
        if (!isCurrent()) return
        result.unwrap(
            onValid = { if (isCurrent()) block(payload) },
            onFailure = failure@{
                if (!isCurrent()) return@failure
                progressConsumer?.invoke(false)
                if (isCurrent()) errorDisplayer(it)
            },
            onInvalid = invalid@{
                if (!isCurrent()) return@invalid
                progressConsumer?.invoke(false)
                if (!isCurrent()) return@invalid
                val (title, message) = validationFailureTransformer(it.reason)
                if (!isCurrent()) return@invalid
                validationFailureEvent.value = Event(
                    DefaultFailure(
                        level = it.level,
                        title = title,
                        message = message,
                        confirmWarning = confirm@{
                            if (!isCurrent()) return@confirm
                            progressConsumer?.invoke(true)
                            if (!isCurrent()) return@confirm
                            val transformedPayload = autoFixPayload(payload, it.reason)
                            if (isCurrent()) block(transformedPayload)
                        }
                    )
                )
            }
        )
    }

    override val validationFailureEvent = MutableLiveData<Event<DefaultFailure>>()
}
