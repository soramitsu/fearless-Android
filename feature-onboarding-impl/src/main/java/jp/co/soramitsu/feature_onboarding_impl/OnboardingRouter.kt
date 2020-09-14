package jp.co.soramitsu.feature_onboarding_impl

interface OnboardingRouter {

    fun openCreateAccount()

    fun backToWelcomeScreen()

    fun openMnemonicScreen(accountName: String)

    fun openTermsScreen()

    fun openPrivacyScreen()

    fun openImportAccountScreen()

    fun back()
}