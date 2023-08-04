package jp.co.soramitsu.account.impl.presentation

import jp.co.soramitsu.account.api.presentation.account.create.ChainAccountCreatePayload
import jp.co.soramitsu.account.api.presentation.actions.AddAccountBottomSheet
import jp.co.soramitsu.account.api.presentation.create_backup_password.CreateBackupPasswordPayload
import jp.co.soramitsu.account.impl.domain.account.details.AccountInChain
import jp.co.soramitsu.account.impl.presentation.exporting.json.confirm.ExportJsonConfirmPayload
import jp.co.soramitsu.account.impl.presentation.mnemonic.confirm.ConfirmMnemonicPayload
import jp.co.soramitsu.account.impl.presentation.node.details.NodeDetailsPayload
import jp.co.soramitsu.common.navigation.DelayedNavigation
import jp.co.soramitsu.common.navigation.PinRequired
import jp.co.soramitsu.common.navigation.SecureRouter
import jp.co.soramitsu.runtime.multiNetwork.chain.model.ChainId

interface AccountRouter : SecureRouter {

    fun backToCreateAccountScreen()

    fun backToWelcomeScreen()

    fun openMain()

    fun openCreatePincode()

    fun openMnemonicScreen(
        isFromGoogleBackup: Boolean,
        accountName: String,
        payload: ChainAccountCreatePayload?
    )

    fun openMnemonicDialog(
        isFromGoogleBackup: Boolean,
        accountName: String
    )

    fun openConfirmMnemonicOnCreate(confirmMnemonicPayload: ConfirmMnemonicPayload)

    fun openAboutScreen()

    fun backToBackupMnemonicScreen()

    fun backToProfileScreen()

    fun back()

    fun openSelectWallet()

    fun openNodes(chainId: ChainId)

    fun openLanguages()

    fun openAccountDetails(metaAccountId: Long)

    fun openExportWallet(metaAccountId: Long)

    fun openAccountsForExport(metaId: Long, from: AccountInChain.From)

    fun openNodeDetails(payload: NodeDetailsPayload)

    fun openAddNode(chainId: ChainId)

    @PinRequired
    fun getExportMnemonicDestination(metaId: Long, chainId: ChainId, isExportWallet: Boolean = false): DelayedNavigation

    @PinRequired
    fun getExportSeedDestination(metaId: Long, chainId: ChainId, isExportWallet: Boolean = false): DelayedNavigation

    @PinRequired
    fun openExportJsonPasswordDestination(metaId: Long, chainId: ChainId, isExportWallet: Boolean = false): DelayedNavigation

    fun openConfirmMnemonicOnExport(mnemonic: List<String>, metaId: Long)

    fun openExportJsonConfirm(payload: ExportJsonConfirmPayload)

    fun returnToWallet()

    fun finishExportFlow()

    fun openChangePinCode()

    fun openBeacon(qrContent: String? = null)

    fun openOnboardingNavGraph(chainId: ChainId, metaId: Long, isImport: Boolean)

    fun openExperimentalFeatures()

    fun openOptionsAddAccount(payload: AddAccountBottomSheet.Payload)

    fun openPolkaswapDisclaimer()

    fun openGetSoraCard()

    fun openCreateWalletDialog(isFromGoogleBackup: Boolean)

    fun openCreateBackupPasswordDialog(payload: CreateBackupPasswordPayload)

    fun openMnemonicAgreementsDialog(
        isFromGoogleBackup: Boolean,
        accountName: String
    )

    fun openImportRemoteWalletDialog()
}
