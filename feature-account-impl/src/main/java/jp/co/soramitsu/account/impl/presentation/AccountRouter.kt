package jp.co.soramitsu.account.impl.presentation

import jp.co.soramitsu.account.api.presentation.account.create.ChainAccountCreatePayload
import jp.co.soramitsu.account.api.presentation.actions.AddAccountBottomSheet
import jp.co.soramitsu.account.impl.domain.account.details.AccountInChain
import jp.co.soramitsu.account.impl.presentation.account.list.AccountChosenNavDirection
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

    fun openMnemonicScreen(accountName: String, payload: ChainAccountCreatePayload?)

    fun openConfirmMnemonicOnCreate(confirmMnemonicPayload: ConfirmMnemonicPayload)

    fun openAboutScreen()

    fun backToBackupMnemonicScreen()

    fun backToProfileScreen()

    fun back()

    fun openWallets(accountChosenNavDirection: AccountChosenNavDirection)

    fun openNodes(chainId: ChainId)

    fun openLanguages()

    fun openAddAccount()

    fun openAccountDetails(metaAccountId: Long)

    fun openExportWallet(metaAccountId: Long)

    fun openAccountsForExport(metaId: Long, from: AccountInChain.From)

    fun openEditAccounts()

    fun backToMainScreen()

    fun openNodeDetails(payload: NodeDetailsPayload)

    fun openAddNode(chainId: ChainId)

    @PinRequired
    fun openExportMnemonic(metaId: Long, chainId: ChainId, isExportWallet: Boolean = false): DelayedNavigation

    @PinRequired
    fun openExportSeed(metaId: Long, chainId: ChainId, isExportWallet: Boolean = false): DelayedNavigation

    @PinRequired
    fun openExportJsonPassword(metaId: Long, chainId: ChainId, isExportWallet: Boolean = false): DelayedNavigation

    fun openConfirmMnemonicOnExport(mnemonic: List<String>)

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
}
