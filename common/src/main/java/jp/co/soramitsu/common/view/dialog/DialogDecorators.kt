package jp.co.soramitsu.common.view.dialog

import android.content.Context
import androidx.fragment.app.FragmentManager
import jp.co.soramitsu.common.R
import jp.co.soramitsu.common.compose.component.emptyClick
import jp.co.soramitsu.common.presentation.ErrorDialog

typealias DialogClickHandler = () -> Unit

fun infoDialog(
    context: Context,
    childFragmentManager: FragmentManager,
    title: String,
    message: String
) {
    ErrorDialog(
        title = title,
        message = message,
        positiveButtonText = context.resources.getString(R.string.common_ok)
    ).show(childFragmentManager)
}

fun warningDialog(
    context: Context,
    childFragmentManager: FragmentManager,
    title: String,
    message: String,
    onConfirm: DialogClickHandler,
    onCancel: DialogClickHandler? = null
) {
    ErrorDialog(
        title = title,
        message = message,
        positiveButtonText = context.resources.getString(R.string.common_ok),
        negativeButtonText = context.resources.getString(R.string.common_cancel),
        positiveClick = onConfirm,
        negativeClick = onCancel ?: emptyClick
    ).show(childFragmentManager)
}

fun errorDialog(
    context: Context,
    childFragmentManager: FragmentManager,
    title: String,
    message: String,
    onConfirm: DialogClickHandler? = null
) {
    ErrorDialog(
        title = title,
        message = message,
        positiveButtonText = context.resources.getString(R.string.common_ok),
        positiveClick = { onConfirm?.invoke() }
    ).show(childFragmentManager)
}

fun retryDialog(
    context: Context,
    fragmentManager: FragmentManager,
    title: String,
    message: String,
    onRetry: DialogClickHandler? = null,
    onCancel: DialogClickHandler? = null
) {
    ErrorDialog(
        title = title,
        message = message,
        isHideable = false,
        positiveButtonText = context.resources.getString(R.string.common_retry),
        negativeButtonText = context.resources.getString(R.string.common_ok),
        positiveClick = { onRetry?.invoke() },
        negativeClick = { onCancel?.invoke() },
        onBackClick = { onCancel?.invoke() }
    ).show(fragmentManager)
}
