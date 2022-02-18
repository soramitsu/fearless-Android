package jp.co.soramitsu.feature_onboarding_impl

interface OnboardingRouter {

    fun openCreateAccount()

    fun backToWelcomeScreen()

    fun openImportAccountScreen(blockChainType: Int)

    fun back()
}
