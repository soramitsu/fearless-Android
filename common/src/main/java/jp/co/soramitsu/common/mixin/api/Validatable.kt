package jp.co.soramitsu.common.mixin.api

import androidx.lifecycle.LiveData
import jp.co.soramitsu.common.utils.Event
import jp.co.soramitsu.common.validation.ValidationStatus

class DefaultFailure(
    val level: ValidationStatus.NotValid.Level,
    val title: String,
    val message: String,
    val confirmWarning: Action
)

interface Validatable {
    val validationFailureEvent: LiveData<Event<DefaultFailure>>
}
