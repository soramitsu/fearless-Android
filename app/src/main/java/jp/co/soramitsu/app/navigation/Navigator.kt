package jp.co.soramitsu.app.navigation

import android.content.Context
import androidx.navigation.NavController
import jp.co.soramitsu.app.MainActivity
import jp.co.soramitsu.app.R
import jp.co.soramitsu.feature_onboarding_impl.OnboardingRouter
import jp.co.soramitsu.splash.SplashRouter

class Navigator : SplashRouter, OnboardingRouter {

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
        navController?.navigate(R.id.createAccountAction)
    }

    override fun backToWelcomeScreen() {
        navController?.popBackStack()
    }

    override fun openTermsScreen() {
        navController?.navigate(R.id.termsAction)
    }

    override fun openPrivacyScreen() {
        navController?.navigate(R.id.privacyAction)
    }

    override fun openMnemonicScreen() {
    }
}