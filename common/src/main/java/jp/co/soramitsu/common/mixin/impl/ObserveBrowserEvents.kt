package jp.co.soramitsu.common.mixin.impl

import jp.co.soramitsu.common.base.BaseComposeBottomSheetDialogFragment
import jp.co.soramitsu.common.base.BaseBottomSheetDialogFragment
import jp.co.soramitsu.common.base.BaseComposeFragment
import jp.co.soramitsu.common.base.BaseFragment
import jp.co.soramitsu.common.base.BaseViewModel
import jp.co.soramitsu.common.mixin.api.Browserable
import jp.co.soramitsu.common.utils.showBrowser

fun <T> BaseFragment<T>.observeBrowserEvents(viewModel: T) where T : BaseViewModel, T : Browserable {
    viewModel.openBrowserEvent.observeEvent(this::showBrowser)
}

fun <T> BaseComposeBottomSheetDialogFragment<T>.observeBrowserEvents(viewModel: T) where T : BaseViewModel, T : Browserable {
    viewModel.openBrowserEvent.observeEvent(this::showBrowser)
}

fun <T> BaseBottomSheetDialogFragment<T>.observeBrowserEvents(viewModel: T) where T : BaseViewModel, T : Browserable {
    viewModel.openBrowserEvent.observeEvent(this::showBrowser)
}

fun <T> BaseComposeFragment<T>.observeBrowserEvents(viewModel: T) where T : BaseViewModel, T : Browserable {
    viewModel.openBrowserEvent.observeEvent(this::showBrowser)
}
