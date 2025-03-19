package jp.co.soramitsu.account.api.presentation.actions

import jp.co.soramitsu.common.R
import jp.co.soramitsu.common.base.BaseBottomSheetDialogFragment
import jp.co.soramitsu.common.base.BaseComposeBottomSheetDialogFragment
import jp.co.soramitsu.common.base.BaseComposeFragment
import jp.co.soramitsu.common.base.BaseFragment
import jp.co.soramitsu.common.base.BaseViewModel
import jp.co.soramitsu.common.mixin.impl.observeBrowserEvents

fun <T> BaseFragment<T>.setupExternalActions(viewModel: T) where T : BaseViewModel, T : ExternalAccountActions {
    observeBrowserEvents(viewModel)

    viewModel.showExternalActionsEvent.observeEvent {
        showAccountExternalActions(it, viewModel)
    }
}

fun <T> BaseComposeBottomSheetDialogFragment<T>.setupExternalActions(viewModel: T) where T : BaseViewModel, T : ExternalAccountActions {
    observeBrowserEvents(viewModel)

    viewModel.showExternalActionsEvent.observeEvent {
        showAccountExternalActions(it, viewModel)
    }
}

fun <T> BaseBottomSheetDialogFragment<T>.setupExternalActions(viewModel: T) where T : BaseViewModel, T : ExternalAccountActions {
    observeBrowserEvents(viewModel)

    viewModel.showExternalActionsEvent.observeEvent {
        showAccountExternalActions(it, viewModel)
    }
}

fun <T> BaseComposeFragment<T>.setupExternalActions(viewModel: T) where T : BaseViewModel, T : ExternalAccountActions {
    observeBrowserEvents(viewModel)

    viewModel.showExternalActionsEvent.observeEvent {
        showAccountExternalActions(it, viewModel)
    }
}

fun <T> BaseComposeFragment<T>.showAccountExternalActions(
    payload: ExternalAccountActions.Payload,
    viewModel: T
) where T : BaseViewModel, T : ExternalAccountActions {
    ExternalActionsSheet(
        requireContext(),
        ExternalActionsSheet.Payload(
            R.string.common_copy_address,
            payload
        ),
        viewModel::copyAddressClicked,
        viewModel::viewExternalClicked
    ).show()
}

fun <T> BaseFragment<T>.showAccountExternalActions(
    payload: ExternalAccountActions.Payload,
    viewModel: T
) where T : BaseViewModel, T : ExternalAccountActions {
    ExternalActionsSheet(
        requireContext(),
        ExternalActionsSheet.Payload(
            R.string.common_copy_address,
            payload
        ),
        viewModel::copyAddressClicked,
        viewModel::viewExternalClicked
    ).show()
}

fun <T> BaseComposeBottomSheetDialogFragment<T>.showAccountExternalActions(
    payload: ExternalAccountActions.Payload,
    viewModel: T
) where T : BaseViewModel, T : ExternalAccountActions {
    ExternalActionsSheet(
        requireContext(),
        ExternalActionsSheet.Payload(
            R.string.common_copy_address,
            payload
        ),
        viewModel::copyAddressClicked,
        viewModel::viewExternalClicked
    ).show()
}

fun <T> BaseBottomSheetDialogFragment<T>.showAccountExternalActions(
    payload: ExternalAccountActions.Payload,
    viewModel: T
) where T : BaseViewModel, T : ExternalAccountActions {
    ExternalActionsSheet(
        requireContext(),
        ExternalActionsSheet.Payload(
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
