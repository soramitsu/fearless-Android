package jp.co.soramitsu.account.impl.presentation

import jp.co.soramitsu.account.api.domain.model.ImportMode
import jp.co.soramitsu.account.api.presentation.create_backup_password.SaveBackupPayload
import jp.co.soramitsu.account.impl.presentation.exporting.json.confirm.ExportJsonConfirmPayload
import jp.co.soramitsu.account.impl.presentation.mnemonic.confirm.ConfirmMnemonicPayload
import jp.co.soramitsu.account.impl.presentation.node.details.NodeDetailsPayload
import jp.co.soramitsu.common.model.WalletEcosystem
import jp.co.soramitsu.common.navigation.DelayedNavigation
import jp.co.soramitsu.common.navigation.PinRequired
import jp.co.soramitsu.common.navigation.SecureRouter
import jp.co.soramitsu.runtime.multiNetwork.chain.model.ChainId
import kotlinx.coroutines.flow.Flow

interface AccountRouter : SecureRouter {

    fun backToCreateAccountScreen()

    fun backToWelcomeScreen()

    fun openMain()

    fun openCreatePincode()

    fun openMnemonicScreen(
        accountName: String,
        accountTypes: List<WalletEcosystem>
    )

    fun openMnemonicScreenAddAccount(
        walletId: Long,
        accountName: String,
        type: WalletEcosystem
    )

    fun openMnemonicDialogGoogleBackup(
        accountName: String,
        accountTypes: List<WalletEcosystem>
    )

    fun openConfirmMnemonicOnCreate(confirmMnemonicPayload: ConfirmMnemonicPayload)

    fun openAboutScreen()

    fun backToBackupMnemonicScreen()

    fun backToProfileScreen()

    fun back()

    fun backWithResult(vararg results: Pair<String, Any?>)

    fun openSelectWallet()

    fun openNodes(chainId: ChainId)

    fun openLanguages()

    fun openAccountDetails(metaAccountId: Long)

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

    fun openOptionsAddAccount(metaId: Long, type: WalletEcosystem)

    fun openPolkaswapDisclaimerFromProfile()

    fun openGetSoraCard()
    fun openSoraCardDetails()

    fun openCreateWalletDialogFromGoogleBackup()

    fun openCreateBackupPasswordDialogWithResult(payload: SaveBackupPayload?): Flow<Int>

    fun openMnemonicAgreementsDialogForGoogleBackup(
        accountName: String,
        accountTypes: List<WalletEcosystem>
    )

    fun openImportRemoteWalletDialog()

    fun openConnectionsScreen()
    fun openTonConnectionsScreen()

    fun openScoreDetailsScreen(metaId: Long)

    fun openEcosystemAccountsOptions(walletId: Long, type: WalletEcosystem)

    fun openEcosystemAccountsFragment(walletId: Long, type: WalletEcosystem)

    fun openSelectImportModeForResult(): Flow<ImportMode>

    fun openImportAddAccountScreen(walletId: Long, walletEcosystem: WalletEcosystem, importMode: ImportMode)

    fun openOptionsWallet(walletId: Long, allowDetails: Boolean)

    fun openCrowdloansScreen()
}
