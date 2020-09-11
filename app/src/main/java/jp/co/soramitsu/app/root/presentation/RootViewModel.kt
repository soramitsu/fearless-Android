package jp.co.soramitsu.app.root.presentation

import jp.co.soramitsu.app.root.domain.RootInteractor
import jp.co.soramitsu.common.base.BaseViewModel

class RootViewModel(
    interactor: RootInteractor
) : BaseViewModel() {

    fun jsonFileOpened(content: String?) {}
}