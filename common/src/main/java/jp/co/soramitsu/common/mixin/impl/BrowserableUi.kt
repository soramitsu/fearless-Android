package jp.co.soramitsu.common.mixin.impl

import jp.co.soramitsu.common.base.BaseFragment
import jp.co.soramitsu.common.base.BaseViewModel
import jp.co.soramitsu.common.mixin.api.Browserable
import jp.co.soramitsu.common.utils.showBrowser

fun <T> BaseFragment<T>.observeBrowserEvents(viewModel: T) where T : BaseViewModel, T : Browserable {
    viewModel.openBrowserEvent.observeEvent(this::showBrowser)
}