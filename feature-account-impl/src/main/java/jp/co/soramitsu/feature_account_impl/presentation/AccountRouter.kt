package jp.co.soramitsu.feature_account_impl.presentation

import jp.co.soramitsu.common.navigation.DelayedNavigation
import jp.co.soramitsu.common.navigation.PinRequired
import jp.co.soramitsu.common.navigation.SecureRouter
import jp.co.soramitsu.feature_account_impl.presentation.account.list.AccountChosenNavDirection
import jp.co.soramitsu.feature_account_impl.presentation.exporting.json.confirm.ExportJsonConfirmPayload
import jp.co.soramitsu.feature_account_impl.presentation.mnemonic.confirm.ConfirmMnemonicPayload

interface AccountRouter : SecureRouter {

    fun backToCreateAccountScreen()

    fun backToWelcomeScreen()

    fun openMain()

    fun openCreatePincode()

    fun openMnemonicScreen(accountName: String)

    fun openConfirmMnemonicOnCreate(confirmMnemonicPayload: ConfirmMnemonicPayload)

    fun openAboutScreen()

    fun backToBackupMnemonicScreen()

    fun backToProfileScreen()

    fun back()

    fun openWallets(accountChosenNavDirection: AccountChosenNavDirection)

    fun openNodes(chainId: String)

    fun openLanguages()

    fun openAddAccount()

    fun openAccountDetails(metaAccountId: Long)

    fun openEditAccounts()

    fun backToMainScreen()

    fun openNodeDetails(chainId: String, nodeUrl: String)

    fun openAddNode(chainId: String)

    @PinRequired
    fun openExportMnemonic(accountAddress: String): DelayedNavigation

    @PinRequired
    fun openExportSeed(accountAddress: String): DelayedNavigation

    @PinRequired
    fun openExportJsonPassword(accountAddress: String): DelayedNavigation

    fun openConfirmMnemonicOnExport(mnemonic: List<String>)

    fun openExportJsonConfirm(payload: ExportJsonConfirmPayload)

    fun returnToWallet()

    fun finishExportFlow()

    fun openChangePinCode()
}
