package jp.co.soramitsu.feature_account_impl.presentation

import jp.co.soramitsu.feature_account_impl.domain.model.PinCodeAction

interface AccountRouter {

    fun backToCreateAccountScreen()

    fun backToWelcomeScreen()

    fun showPincode(action: PinCodeAction)

    fun backToBackupMnemonicScreen()
}