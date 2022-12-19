package jp.co.soramitsu.common.mixin.impl

import android.content.Context
import jp.co.soramitsu.common.base.BaseFragment
import jp.co.soramitsu.common.base.BaseViewModel
import jp.co.soramitsu.common.mixin.api.Retriable
import jp.co.soramitsu.common.view.dialog.retryDialog

fun <T> BaseFragment<T>.observeRetries(
    viewModel: T,
    context: Context = requireContext()
) where T : BaseViewModel, T : Retriable {
    viewModel.retryEvent.observeEvent {
        retryDialog(
            context = context,
            fragmentManager = childFragmentManager,
            title = it.title,
            message = it.message,
            onRetry = it.onRetry,
            onCancel = it.onCancel
        )
    }
}
