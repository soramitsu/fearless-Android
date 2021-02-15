package jp.co.soramitsu.feature_onboarding_impl

import jp.co.soramitsu.domain.model.Node

interface OnboardingRouter {

    fun openCreateAccount(selectedNetworkType: Node.NetworkType?)

    fun backToWelcomeScreen()

    fun openImportAccountScreen(selectedNetworkType: Node.NetworkType?)

    fun back()
}