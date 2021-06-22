package jp.co.soramitsu.common.mixin.impl

import android.content.Context
import jp.co.soramitsu.common.base.BaseFragment
import jp.co.soramitsu.common.mixin.api.Validatable
import jp.co.soramitsu.common.validation.DefaultFailureLevel
import jp.co.soramitsu.common.view.dialog.errorDialog
import jp.co.soramitsu.common.view.dialog.warningDialog

fun BaseFragment<*>.observeValidations(
    viewModel: Validatable,
    dialogContext: Context = requireContext()
) {
    viewModel.validationFailureEvent.observeEvent {
        val level = it.level

        when {
            level >= DefaultFailureLevel.ERROR -> errorDialog(dialogContext) {
                setTitle(it.title)
                setMessage(it.message)
            }
            level >= DefaultFailureLevel.WARNING -> warningDialog(
                dialogContext,
                onConfirm = it.confirmWarning
            ) {
                setTitle(it.title)
                setMessage(it.message)
            }
        }
    }
}
