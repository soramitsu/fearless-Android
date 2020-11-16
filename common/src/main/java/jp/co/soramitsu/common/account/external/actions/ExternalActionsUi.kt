package jp.co.soramitsu.common.account.external.actions

import jp.co.soramitsu.common.R
import jp.co.soramitsu.common.base.BaseFragment
import jp.co.soramitsu.common.base.BaseViewModel
import jp.co.soramitsu.common.mixin.impl.observeBrowserEvents

fun <T> BaseFragment<T>.setupExternalActions(viewModel: T) where T : BaseViewModel, T : ExternalAccountActions {
    observeBrowserEvents(viewModel)

    viewModel.showExternalActionsEvent.observeEvent {
        showAccountExternalActions(it, viewModel)
    }
}

fun <T> BaseFragment<T>.showAccountExternalActions(
    payload: ExternalAccountActions.Payload,
    viewModel: T
) where T : BaseViewModel, T : ExternalAccountActions {
    ExternalActionsSheet(
        requireContext(),
        ExternalActionsSheet.Payload(
            R.string.profile_title,
            R.string.common_copy_address,
            payload
        ),
        viewModel::copyAddressClicked,
        viewModel::viewExternalClicked
    ).show()
}

fun <T> T.copyAddressClicked(address: String) where T : BaseViewModel, T : ExternalAccountActions {
    copyAddress(address, ::showMessage)
}