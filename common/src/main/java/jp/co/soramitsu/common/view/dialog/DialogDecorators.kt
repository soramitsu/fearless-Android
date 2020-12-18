package jp.co.soramitsu.common.view.dialog

import androidx.appcompat.app.AlertDialog
import androidx.fragment.app.Fragment
import jp.co.soramitsu.common.R

typealias DialogClickHandler = () -> Unit

fun Fragment.showWarningDialog(
    onConfirm: DialogClickHandler,
    onCancel: DialogClickHandler? = null,
    decorated: AlertDialog.Builder.() -> Unit
) {
    val builder = AlertDialog.Builder(requireContext())

    builder
        .setCancelable(false)
        .setPositiveButton(R.string.common_continue) { _, _ -> onConfirm() }
        .setNegativeButton(R.string.common_cancel) { _, _ -> onCancel?.invoke() }

    builder.decorated()

    builder.show()
}

fun Fragment.showErrorDialog(
    onConfirm: DialogClickHandler? = null,
    decorated: AlertDialog.Builder.() -> Unit
) {
    val builder = AlertDialog.Builder(requireContext())

    builder.setCancelable(false)
        .setTitle(R.string.common_error_general_title)
        .setPositiveButton(R.string.common_ok) { _, _ -> onConfirm?.invoke() }

    builder.decorated()

    builder.show()
}