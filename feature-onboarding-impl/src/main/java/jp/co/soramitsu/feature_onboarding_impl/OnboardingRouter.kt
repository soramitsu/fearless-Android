package jp.co.soramitsu.feature_onboarding_impl

import jp.co.soramitsu.feature_account_api.presentation.account.create.ChainAccountCreatePayload

interface OnboardingRouter {

    fun openCreateAccount()
    fun openCreateAccountSkipWelcome(payload: ChainAccountCreatePayload)

    fun backToWelcomeScreen()

    fun openImportAccountScreen(blockChainType: Int)
    fun openImportAccountSkipWelcome(payload: ChainAccountCreatePayload)

    fun back()
}
