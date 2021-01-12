package jp.co.soramitsu.feature_onboarding_impl

import jp.co.soramitsu.feature_account_api.domain.model.Node

interface OnboardingRouter {

    fun openCreateAccount(selectedNetworkType: Node.NetworkType?)

    fun backToWelcomeScreen()

    fun openMnemonicScreen(accountName: String, selectedNetworkType: Node.NetworkType?)

    fun openImportAccountScreen(selectedNetworkType: Node.NetworkType?)

    fun back()
}