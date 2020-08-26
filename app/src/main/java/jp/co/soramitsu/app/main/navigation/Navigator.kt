package jp.co.soramitsu.app.main.navigation

import android.content.Context
import androidx.navigation.NavController
import jp.co.soramitsu.app.R
import jp.co.soramitsu.app.main.presentation.MainActivity
import jp.co.soramitsu.feature_account_api.domain.model.CryptoType
import jp.co.soramitsu.feature_account_api.domain.model.NetworkType
import jp.co.soramitsu.feature_account_impl.presentation.AccountRouter
import jp.co.soramitsu.feature_account_impl.presentation.mnemonic.backup.BackupMnemonicFragment
import jp.co.soramitsu.feature_account_impl.presentation.mnemonic.confirm.ConfirmMnemonicFragment
import jp.co.soramitsu.feature_onboarding_impl.OnboardingRouter
import jp.co.soramitsu.splash.SplashRouter

class Navigator : SplashRouter, OnboardingRouter, AccountRouter {

    private var navController: NavController? = null

    fun attachNavController(navController: NavController, graph: Int) {
        navController.setGraph(graph)
        this.navController = navController
    }

    fun detachNavController(navController: NavController) {
        if (this.navController == navController) {
            this.navController = null
        }
    }

    fun popBackStack() {
        navController?.popBackStack()
    }

    fun showPin() {
        navController?.navigate(R.id.action_welcomeFragment_to_pincodeFragment)
    }

    override fun openMain(context: Context) {
        MainActivity.start(context)
    }

    override fun openCreateAccount() {
        navController?.navigate(R.id.action_welcomeFragment_to_createAccountFragment)
    }

    override fun backToWelcomeScreen() {
        navController?.popBackStack()
    }

    override fun showProfile() {
        navController?.navigate(R.id.action_pincodeFragment_to_profileFragment)
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
        networkType: NetworkType,
        derivationPath: String
    ) {
        val bundle = ConfirmMnemonicFragment.getBundle(accountName, mnemonic, cryptoType, networkType, derivationPath)
        navController?.navigate(R.id.action_backupMnemonicFragment_to_confirmMnemonicFragment, bundle)
    }

    override fun openAboutScreen() {
        navController?.navigate(R.id.action_profileFragment_to_aboutFragment)
    }

    override fun openTermsScreen() {
        navController?.navigate(R.id.action_welcomeFragment_to_termsFragment)
    }

    override fun openPrivacyScreen() {
        navController?.navigate(R.id.action_welcomeFragment_to_privacyFragment)
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
}