package jp.co.soramitsu.feature_account_impl.presentation

import jp.co.soramitsu.feature_account_api.domain.model.CryptoType
import jp.co.soramitsu.feature_account_api.domain.model.NetworkType

interface AccountRouter {

    fun backToCreateAccountScreen()

    fun backToWelcomeScreen()

    fun openCreatePincode()

    fun openConfirmMnemonicScreen(
        accountName: String,
        mnemonic: List<String>,
        cryptoType: CryptoType,
        networkType: NetworkType,
        derivationPath: String
    )

    fun backToBackupMnemonicScreen()
}