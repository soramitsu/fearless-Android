package jp.co.soramitsu.app.main.presentation

import jp.co.soramitsu.app.main.domain.MainInteractor
import jp.co.soramitsu.common.base.BaseViewModel

class MainViewModel(
    interactor: MainInteractor
) : BaseViewModel() {

    fun jsonFileOpened(content: String?) {}
}