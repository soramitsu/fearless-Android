package jp.co.soramitsu.app.root.navigation

import androidx.navigation.NavController
import jp.co.soramitsu.app.R
import jp.co.soramitsu.app.root.presentation.RootRouter
import jp.co.soramitsu.common.utils.postToUiThread
import jp.co.soramitsu.feature_account_api.domain.model.Node
import jp.co.soramitsu.feature_account_impl.presentation.AccountRouter
import jp.co.soramitsu.feature_account_impl.presentation.account.details.AccountDetailsFragment
import jp.co.soramitsu.feature_account_impl.presentation.exporting.json.password.ExportJsonPasswordFragment
import jp.co.soramitsu.feature_account_impl.presentation.exporting.mnemonic.ExportMnemonicFragment
import jp.co.soramitsu.feature_account_impl.presentation.exporting.seed.ExportSeedFragment
import jp.co.soramitsu.feature_account_impl.presentation.importing.ImportAccountFragment
import jp.co.soramitsu.feature_account_impl.presentation.mnemonic.backup.BackupMnemonicFragment
import jp.co.soramitsu.feature_account_impl.presentation.mnemonic.confirm.ConfirmMnemonicFragment
import jp.co.soramitsu.feature_account_impl.presentation.mnemonic.confirm.ConfirmMnemonicPayload
import jp.co.soramitsu.feature_account_impl.presentation.node.details.NodeDetailsFragment
import jp.co.soramitsu.feature_account_impl.presentation.pincode.PinCodeAction
import jp.co.soramitsu.feature_account_impl.presentation.pincode.PincodeFragment
import jp.co.soramitsu.feature_onboarding_impl.OnboardingRouter
import jp.co.soramitsu.feature_onboarding_impl.presentation.create.CreateAccountFragment
import jp.co.soramitsu.feature_onboarding_impl.presentation.welcome.WelcomeFragment
import jp.co.soramitsu.feature_wallet_api.domain.model.Asset
import jp.co.soramitsu.feature_wallet_impl.presentation.WalletRouter
import jp.co.soramitsu.feature_wallet_impl.presentation.balance.detail.BalanceDetailFragment
import jp.co.soramitsu.feature_wallet_impl.presentation.model.TransactionModel
import jp.co.soramitsu.feature_wallet_impl.presentation.send.TransferDraft
import jp.co.soramitsu.feature_wallet_impl.presentation.send.amount.ChooseAmountFragment
import jp.co.soramitsu.feature_wallet_impl.presentation.send.confirm.ConfirmTransferFragment
import jp.co.soramitsu.feature_wallet_impl.presentation.transaction.detail.TransactionDetailFragment
import jp.co.soramitsu.splash.SplashRouter

class Navigator : SplashRouter, OnboardingRouter, AccountRouter, WalletRouter, RootRouter {

    private var navController: NavController? = null

    fun attachNavController(navController: NavController) {
        this.navController = navController
    }

    override fun openAddFirstAccount() {
        navController?.navigate(R.id.action_splash_to_onboarding, WelcomeFragment.getBundle(false))
    }

    override fun openCreatePin() {
        val action = PinCodeAction.Create(R.id.action_open_main)
        val bundle = PincodeFragment.getPinCodeBundle(action)
        navController?.navigate(R.id.action_splash_to_pin, bundle)
    }

    override fun openCheckPin() {
        val action = PinCodeAction.Check(R.id.action_open_main)
        val bundle = PincodeFragment.getPinCodeBundle(action)
        navController?.navigate(R.id.action_splash_to_pin, bundle)
    }

    override fun openMainScreen() {
        navController?.navigate(R.id.action_splash_to_main)
    }

    override fun openCreateAccount(selectedNetworkType: Node.NetworkType?) {
        navController?.navigate(R.id.action_welcomeFragment_to_createAccountFragment, CreateAccountFragment.getBundle(selectedNetworkType))
    }

    override fun backToWelcomeScreen() {
        navController?.popBackStack()
    }

    override fun openMain() {
        navController?.navigate(R.id.action_open_main)
    }

    override fun openDestination(destination: Int) {
        navController?.navigate(destination)
    }

    override fun openCreatePincode() {
        val action = PinCodeAction.Create(R.id.action_open_main)
        val bundle = PincodeFragment.getPinCodeBundle(action)
        when (navController?.currentDestination?.id) {
            R.id.importAccountFragment -> navController?.navigate(R.id.action_importAccountFragment_to_pincodeFragment, bundle)
            R.id.confirmMnemonicFragment -> navController?.navigate(R.id.action_confirmMnemonicFragment_to_pincodeFragment, bundle)
        }
    }

    override fun openConfirmMnemonicOnCreate(confirmMnemonicPayload: ConfirmMnemonicPayload) {
        val bundle = ConfirmMnemonicFragment.getBundle(confirmMnemonicPayload)

        navController?.navigate(
            R.id.action_backupMnemonicFragment_to_confirmMnemonicFragment,
            bundle
        )
    }

    override fun openAboutScreen() {
        navController?.navigate(R.id.action_profileFragment_to_aboutFragment)
    }

    override fun openTermsScreen() {
        navController?.navigate(R.id.openTerms)
    }

    override fun openPrivacyScreen() {
        navController?.navigate(R.id.openPrivacy)
    }

    override fun openImportAccountScreen(selectedNetworkType: Node.NetworkType?) {
        navController?.navigate(R.id.importAction, ImportAccountFragment.getBundle(selectedNetworkType))
    }

    override fun openMnemonicScreen(accountName: String, selectedNetworkType: Node.NetworkType?) {
        val bundle = BackupMnemonicFragment.getBundle(accountName, selectedNetworkType)
        navController?.navigate(R.id.action_createAccountFragment_to_backupMnemonicFragment, bundle)
    }

    override fun backToCreateAccountScreen() {
        navController?.popBackStack()
    }

    override fun backToBackupMnemonicScreen() {
        navController?.popBackStack()
    }

    override fun backToProfileScreen() {
        navController?.popBackStack()
    }

    override fun back() {
        navController?.navigateUp()
    }

    override fun openChooseRecipient() {
        navController?.navigate(R.id.action_open_send)
    }

    override fun openChooseAmount(recipientAddress: String) {
        val bundle = ChooseAmountFragment.getBundle(recipientAddress)

        navController?.navigate(R.id.action_chooseRecipientFragment_to_chooseAmountFragment, bundle)
    }

    override fun openConfirmTransfer(transferDraft: TransferDraft) {
        val bundle = ConfirmTransferFragment.getBundle(transferDraft)

        navController?.navigate(R.id.action_chooseAmountFragment_to_confirmTransferFragment, bundle)
    }

    override fun finishSendFlow() {
        navController?.navigate(R.id.finish_send_flow)
    }

    override fun openRepeatTransaction(recipientAddress: String) {
        val bundle = ChooseAmountFragment.getBundle(recipientAddress)

        navController?.navigate(R.id.openSelectAmount, bundle)
    }

    override fun openTransactionDetail(transaction: TransactionModel) {
        val bundle = TransactionDetailFragment.getBundle(transaction)

        navController?.navigate(R.id.open_transaction_detail, bundle)
    }

    override fun openAccounts() {
        navController?.navigate(R.id.action_mainFragment_to_accountsFragment)
    }

    override fun openNodes() {
        navController?.navigate(R.id.action_mainFragment_to_nodesFragment)
    }

    override fun openLanguages() {
        navController?.navigate(R.id.action_mainFragment_to_languagesFragment)
    }

    override fun openAddAccount() {
        navController?.navigate(R.id.action_open_onboarding, WelcomeFragment.getBundle(true))
    }

    override fun openReceive() {
        navController?.navigate(R.id.action_open_receive)
    }

    override fun returnToMain() {
        // to achieve smooth animation
        postToUiThread {
            navController?.navigate(R.id.action_return_to_wallet)
        }
    }

    override fun openAccountDetails(address: String) {
        val extras = AccountDetailsFragment.getBundle(address)

        navController?.navigate(R.id.action_accountsFragment_to_accountDetailsFragment, extras)
    }

    override fun openEditAccounts() {
        navController?.navigate(R.id.action_accountsFragment_to_editAccountsFragment)
    }

    override fun backToMainScreen() {
        navController?.navigate(R.id.action_editAccountsFragment_to_mainFragment)
    }

    override fun openNodeDetails(nodeId: Int, isSelected: Boolean) {
        navController?.navigate(R.id.action_nodesFragment_to_nodeDetailsFragment, NodeDetailsFragment.getBundle(nodeId, isSelected))
    }

    override fun openAssetDetails(token: Asset.Token) {
        val bundle = BalanceDetailFragment.getBundle(token)

        navController?.navigate(R.id.action_mainFragment_to_balanceDetailFragment, bundle)
    }

    override fun openAddNode() {
        navController?.navigate(R.id.action_nodesFragment_to_addNodeFragment)
    }

    override fun createAccountForNetworkType(networkType: Node.NetworkType) {
        navController?.navigate(R.id.action_nodes_to_onboarding, WelcomeFragment.getBundleWithNetworkType(true, networkType))
    }

    override fun openExportMnemonic(accountAddress: String) {
        val extras = ExportMnemonicFragment.getBundle(accountAddress)

        navController?.navigate(R.id.action_accountDetailsFragment_to_exportMnemonicFragment, extras)
    }

    override fun openExportSeed(accountAddress: String) {
        val extras = ExportSeedFragment.getBundle(accountAddress)

        navController?.navigate(R.id.action_accountDetailsFragment_to_exportSeedFragment, extras)
    }

    override fun openConfirmMnemonicOnExport(mnemonic: List<String>) {
        val extras = ConfirmMnemonicFragment.getBundle(ConfirmMnemonicPayload(mnemonic, null))

        navController?.navigate(R.id.action_exportMnemonicFragment_to_confirmExportMnemonicFragment, extras)
    }

    override fun openExportJsonPassword(accountAddress: String) {
        val extras = ExportJsonPasswordFragment.getBundle(accountAddress)

        navController?.navigate(R.id.action_accountDetailsFragment_to_exportJsonPasswordFragment, extras)
    }

    override fun openChangePinCode() {
        val action = PinCodeAction.Change
        val bundle = PincodeFragment.getPinCodeBundle(action)
        navController?.navigate(R.id.action_mainFragment_to_pinCodeFragment, bundle)
    }
}