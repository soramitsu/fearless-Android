package jp.co.soramitsu.feature_account_impl.presentation.exporting.mnemonic

import jp.co.soramitsu.common.account.mnemonicViewer.mapMnemonicToMnemonicWords
import jp.co.soramitsu.common.resources.ResourceManager
import jp.co.soramitsu.common.utils.map
import jp.co.soramitsu.feature_account_api.domain.interfaces.AccountInteractor
import jp.co.soramitsu.feature_account_api.domain.model.WithDerivationPath
import jp.co.soramitsu.feature_account_api.domain.model.WithMnemonic
import jp.co.soramitsu.feature_account_impl.presentation.AccountRouter
import jp.co.soramitsu.feature_account_impl.presentation.exporting.ExportSource
import jp.co.soramitsu.feature_account_impl.presentation.exporting.ExportViewModel

class ExportMnemonicViewModel(
    private val router: AccountRouter,
    resourceManager: ResourceManager,
    accountInteractor: AccountInteractor,
    accountAddress: String
) : ExportViewModel(accountInteractor, accountAddress, resourceManager, ExportSource.Mnemonic) {

    val mnemonic = securityTypeLiveData.map {
        val words = (it as WithMnemonic).mnemonicWords()

        mapMnemonicToMnemonicWords(words)
    }

    val derivationPath = securityTypeLiveData.map {
        (it as WithDerivationPath).derivationPath
    }

    fun back() {
        router.back()
    }
}