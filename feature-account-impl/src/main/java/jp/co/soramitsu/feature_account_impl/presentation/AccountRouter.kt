package jp.co.soramitsu.feature_account_impl.presentation

import jp.co.soramitsu.common.navigation.DelayedNavigation
import jp.co.soramitsu.common.navigation.PinRequired
import jp.co.soramitsu.common.navigation.SecureRouter
import jp.co.soramitsu.feature_account_api.domain.model.Node
import jp.co.soramitsu.feature_account_impl.presentation.exporting.json.confirm.ExportJsonConfirmPayload
import jp.co.soramitsu.feature_account_impl.presentation.mnemonic.confirm.ConfirmMnemonicPayload

interface AccountRouter : SecureRouter {

    fun backToCreateAccountScreen()

    fun backToWelcomeScreen()

    fun openMain()

    fun openCreatePincode()

    fun openMnemonicScreen(accountName: String, selectedNetworkType: Node.NetworkType)

    fun openConfirmMnemonicOnCreate(confirmMnemonicPayload: ConfirmMnemonicPayload)

    fun openAboutScreen()

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

    fun openNodeDetails(nodeId: Int, isSelected: Boolean)

    fun openAddNode()

    fun createAccountForNetworkType(networkType: Node.NetworkType)

    @PinRequired
    fun openExportMnemonic(accountAddress: String): DelayedNavigation

    @PinRequired
    fun openExportSeed(accountAddress: String): DelayedNavigation

    @PinRequired
    fun openExportJsonPassword(accountAddress: String): DelayedNavigation

    fun openConfirmMnemonicOnExport(mnemonic: List<String>)

    fun openExportJsonConfirm(payload: ExportJsonConfirmPayload)

    fun returnToMain()

    fun finishExportFlow()

    fun openChangePinCode()
}