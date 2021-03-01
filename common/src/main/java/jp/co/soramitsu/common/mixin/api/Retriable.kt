package jp.co.soramitsu.common.mixin.api

import android.content.Context
import androidx.lifecycle.LiveData
import jp.co.soramitsu.common.base.BaseFragment
import jp.co.soramitsu.common.base.BaseViewModel
import jp.co.soramitsu.common.utils.Event
import jp.co.soramitsu.common.view.dialog.retryDialog

typealias Action = () -> Unit

class RetryPayload(
    val title: String,
    val message: String,
    val onRetry: Action,
    val onCancel: Action? = null
)

interface Retriable {

    val retryEvent: LiveData<Event<RetryPayload>>
}

fun <T> BaseFragment<T>.observeRetries(
    viewModel: T,
    context: Context = requireContext()
) where T : BaseViewModel, T : Retriable {
    viewModel.retryEvent.observeEvent {
        retryDialog(
            context = context,
            onRetry = it.onRetry,
            onCancel = it.onCancel
        ) {
            setTitle(it.title)
            setMessage(it.message)
        }
    }
}