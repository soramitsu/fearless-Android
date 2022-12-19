package jp.co.soramitsu.onboarding.impl

import jp.co.soramitsu.account.api.presentation.account.create.ChainAccountCreatePayload

interface OnboardingRouter {

    fun openCreateAccountFromOnboarding()
    fun openCreateAccountSkipWelcome(payload: ChainAccountCreatePayload)

    fun backToWelcomeScreen()

    fun openImportAccountScreen(blockChainType: Int)
    fun openImportAccountSkipWelcome(payload: ChainAccountCreatePayload)

    fun back()
}
