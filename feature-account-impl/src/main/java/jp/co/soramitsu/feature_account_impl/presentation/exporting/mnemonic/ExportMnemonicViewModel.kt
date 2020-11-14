package jp.co.soramitsu.feature_account_impl.presentation.exporting.mnemonic

import jp.co.soramitsu.common.account.mnemonicViewer.mapMnemonicToMnemonicWords
import jp.co.soramitsu.common.resources.ResourceManager
import jp.co.soramitsu.common.utils.map
import jp.co.soramitsu.feature_account_api.domain.interfaces.AccountInteractor
import jp.co.soramitsu.feature_account_api.domain.model.WithDerivationPath
import jp.co.soramitsu.feature_account_api.domain.model.WithMnemonic
import jp.co.soramitsu.feature_account_impl.R
import jp.co.soramitsu.feature_account_impl.presentation.AccountRouter
import jp.co.soramitsu.feature_account_impl.presentation.exporting.ExportSource
import jp.co.soramitsu.feature_account_impl.presentation.exporting.ExportViewModel

class ExportMnemonicViewModel(
    private val router: AccountRouter,
    resourceManager: ResourceManager,
    accountInteractor: AccountInteractor,
    accountAddress: String
) : ExportViewModel(accountInteractor, accountAddress, resourceManager, ExportSource.Mnemonic) {

    private val mnemonicSourceLiveData = securityTypeLiveData.map { it as WithMnemonic }

    val mnemonicWordsLiveData = mnemonicSourceLiveData.map {
        mapMnemonicToMnemonicWords(it.mnemonicWords())
    }

    val derivationPathLiveData = securityTypeLiveData.map {
        (it as? WithDerivationPath)?.derivationPath
    }

    fun back() {
        router.back()
    }

    fun exportClicked() {
        showSecurityWarning()
    }

    override fun securityWarningConfirmed() {
        val mnemonic = mnemonicSourceLiveData.value?.mnemonic ?: return

        val networkType = networkTypeLiveData.value?.name ?: return

        val derivationPath = derivationPathLiveData.value

        val shareText = if (derivationPath.isNullOrBlank()) {
            resourceManager.getString(R.string.export_mnemonic_without_derivation, networkType, mnemonic)
        } else {
            resourceManager.getString(R.string.export_mnemonic_with_derivation, networkType, mnemonic, derivationPath)
        }

        exportText(shareText)
    }

    fun openConfirmMnemonic() {
        val mnemonicSource = mnemonicSourceLiveData.value ?: return

        router.openConfirmMnemonicOnExport(mnemonicSource.mnemonicWords())
    }
}