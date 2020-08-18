package jp.co.soramitsu.app.navigation

import android.content.Context
import androidx.navigation.NavController
import jp.co.soramitsu.app.MainActivity
import jp.co.soramitsu.app.R
import jp.co.soramitsu.feature_account_impl.domain.model.PinCodeAction
import jp.co.soramitsu.feature_account_impl.presentation.AccountRouter
import jp.co.soramitsu.feature_account_impl.presentation.mnemonic.backup.BackupMnemonicFragment
import jp.co.soramitsu.feature_account_impl.presentation.pincode.PincodeFragment
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

    override fun openMain(context: Context) {
        MainActivity.start(context)
    }

    override fun openCreateAccount() {
        navController?.navigate(R.id.action_welcomeFragment_to_createAccountFragment)
    }

    override fun backToWelcomeScreen() {
        navController?.popBackStack()
    }

    override fun showPincode(action: PinCodeAction) {
        val bundle = PincodeFragment.getBundle(action)
        navController?.navigate(R.id.pincodeFragment, bundle)
    }

    override fun openConfirmMnemonicScreen() {
        navController?.navigate(R.id.action_backupMnemonicFragment_to_confirmMnemonicFragment)
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
}