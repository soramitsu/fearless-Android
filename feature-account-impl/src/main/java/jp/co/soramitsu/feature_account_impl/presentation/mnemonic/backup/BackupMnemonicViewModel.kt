package jp.co.soramitsu.feature_account_impl.presentation.mnemonic.backup

import jp.co.soramitsu.common.base.BaseViewModel
import jp.co.soramitsu.feature_account_api.domain.interfaces.AccountInteractor
import jp.co.soramitsu.feature_account_impl.presentation.AccountRouter

class BackupMnemonicViewModel(
    private val accountInteractor: AccountInteractor,
    private val router: AccountRouter
) : BaseViewModel() {

}