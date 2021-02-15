package jp.co.soramitsu.feature_onboarding_impl

interface OnboardingRouter {

    fun openCreateAccount(selectedNetworkType: jp.co.soramitsu.domain.model.Node.NetworkType?)

    fun backToWelcomeScreen()

    fun openImportAccountScreen(selectedNetworkType: jp.co.soramitsu.domain.model.Node.NetworkType?)

    fun back()
}