package jp.co.soramitsu.app.root.navigation

import androidx.navigation.NavController
import jp.co.soramitsu.app.R
import jp.co.soramitsu.feature_account_api.domain.model.CryptoType
import jp.co.soramitsu.feature_account_api.domain.model.Node
import jp.co.soramitsu.feature_account_impl.presentation.AccountRouter
import jp.co.soramitsu.feature_account_impl.presentation.account.details.AccountDetailsFragment
import jp.co.soramitsu.feature_account_impl.presentation.mnemonic.backup.BackupMnemonicFragment
import jp.co.soramitsu.feature_account_impl.presentation.mnemonic.confirm.ConfirmMnemonicFragment
import jp.co.soramitsu.feature_account_impl.presentation.node.details.NodeDetailsFragment
import jp.co.soramitsu.feature_onboarding_impl.OnboardingRouter
import jp.co.soramitsu.feature_onboarding_impl.presentation.welcome.WelcomeFragment
import jp.co.soramitsu.feature_wallet_impl.presentation.WalletRouter
import jp.co.soramitsu.splash.SplashRouter

class Navigator : SplashRouter, OnboardingRouter, AccountRouter, WalletRouter {

    private var navController: NavController? = null

    fun attachNavController(navController: NavController) {
        this.navController = navController
    }

    fun detachNavController(navController: NavController) {
        if (this.navController == navController) {
            this.navController = null
        }
    }

    override fun openAddFirstAccount() {
        navController?.navigate(R.id.action_splash_to_onboarding, WelcomeFragment.getBundle(false))
    }

    override fun openPin() {
        navController?.navigate(R.id.action_splash_to_pin)
    }

    override fun openCreateAccount() {
        navController?.navigate(R.id.action_welcomeFragment_to_createAccountFragment)
    }

    override fun backToWelcomeScreen() {
        navController?.popBackStack()
    }

    override fun openMain() {
        navController?.navigate(R.id.action_open_main)
    }

    override fun openCreatePincode() {
        when (navController?.currentDestination?.id) {
            R.id.importAccountFragment -> navController?.navigate(R.id.action_importAccountFragment_to_pincodeFragment)
            R.id.confirmMnemonicFragment -> navController?.navigate(R.id.action_confirmMnemonicFragment_to_pincodeFragment)
        }
    }

    override fun openConfirmMnemonicScreen(
        accountName: String,
        mnemonic: List<String>,
        cryptoType: CryptoType,
        node: Node,
        derivationPath: String
    ) {
        val bundle = ConfirmMnemonicFragment.getBundle(
            accountName,
            mnemonic,
            cryptoType,
            node,
            derivationPath
        )
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

    override fun openImportAccountScreen() {
        navController?.navigate(R.id.importAction)
    }

    override fun openMnemonicScreen(accountName: String) {
        val bundle = BackupMnemonicFragment.getBundle(accountName)
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

    override fun openAccounts() {
        navController?.navigate(R.id.action_mainFragment_to_accountsFragment)
    }

    override fun openNodes() {
        navController?.navigate(R.id.action_mainFragment_to_nodesFragment)
    }

    override fun openAddAccount() {
        navController?.navigate(R.id.action_accounts_to_onboarding, WelcomeFragment.getBundle(true))
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

    override fun openNodeDetails(nodeId: Int) {
        navController?.navigate(R.id.action_nodesFragment_to_nodeDetailsFragment, NodeDetailsFragment.getBundle(nodeId))
    }

    override fun openAddNode() {
        navController?.navigate(R.id.action_nodesFragment_to_addNodeFragment)
    }
}