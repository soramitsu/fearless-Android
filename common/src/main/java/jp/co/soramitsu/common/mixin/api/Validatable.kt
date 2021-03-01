package jp.co.soramitsu.common.mixin.api

import android.content.Context
import androidx.lifecycle.LiveData
import jp.co.soramitsu.common.base.BaseFragment
import jp.co.soramitsu.common.base.BaseViewModel
import jp.co.soramitsu.common.utils.Event
import jp.co.soramitsu.common.validation.DefaultFailureLevel
import jp.co.soramitsu.common.validation.ValidationStatus
import jp.co.soramitsu.common.view.dialog.errorDialog
import jp.co.soramitsu.common.view.dialog.warningDialog

class DefaultFailure(
    val level: ValidationStatus.NotValid.Level,
    val title: String,
    val message: String
)

interface Validatable {
    val validationFailureEvent: LiveData<Event<DefaultFailure>>

    fun validationWarningConfirmed()
}

fun <T> BaseFragment<T>.observeValidations(
    viewModel: T,
    dialogContext: Context = requireContext()
) where T : BaseViewModel, T : Validatable {
    viewModel.validationFailureEvent.observeEvent {
        val level = it.level

        when {
            level >= DefaultFailureLevel.ERROR -> errorDialog(dialogContext) {
                setTitle(it.title)
                setMessage(it.message)
            }
            level >= DefaultFailureLevel.WARNING -> warningDialog(
                dialogContext,
                onConfirm = viewModel::validationWarningConfirmed
            ) {
                setTitle(it.title)
                setMessage(it.message)
            }
        }
    }
}