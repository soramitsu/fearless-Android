package jp.co.soramitsu.feature_account_impl.presentation

import jp.co.soramitsu.feature_account_api.domain.model.CryptoType
import jp.co.soramitsu.feature_account_api.domain.model.Node

interface AccountRouter {

    fun backToCreateAccountScreen()

    fun backToWelcomeScreen()

    fun openMain()

    fun openCreatePincode()

    fun openConfirmMnemonicScreen(
        accountName: String,
        mnemonic: List<String>,
        cryptoType: CryptoType,
        node: Node,
        derivationPath: String
    )

    fun openAboutScreen()

    fun openTermsScreen()

    fun openPrivacyScreen()

    fun backToBackupMnemonicScreen()

    fun backToProfileScreen()

    fun back()

    fun openAccounts()

    fun openNodes()

    fun openLanguages()

    fun openAddAccount()

    fun openAccountDetails(address: String)

    fun openEditAccounts()

    fun backToMainScreen()

    fun openNodeDetails(nodeId: Int)

    fun openAddNode()

    fun createAccountForNetworkType(networkType: Node.NetworkType)
}