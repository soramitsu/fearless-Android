package jp.co.soramitsu.common.mixin.api

import androidx.lifecycle.LiveData
import jp.co.soramitsu.common.utils.Event

typealias Action = () -> Unit

/** @param onRetry null when retrying cannot help; the dialog then only offers to leave */
class RetryPayload(
    val title: String,
    val message: String,
    val onRetry: Action?,
    val onCancel: Action? = null
)

interface Retriable {

    val retryEvent: LiveData<Event<RetryPayload>>
}
