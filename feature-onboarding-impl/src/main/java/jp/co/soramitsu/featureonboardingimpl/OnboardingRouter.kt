package jp.co.soramitsu.featureonboardingimpl

import jp.co.soramitsu.featureaccountapi.presentation.account.create.ChainAccountCreatePayload

interface OnboardingRouter {

    fun openCreateAccount()
    fun openCreateAccountSkipWelcome(payload: ChainAccountCreatePayload)

    fun backToWelcomeScreen()

    fun openImportAccountScreen(blockChainType: Int)
    fun openImportAccountSkipWelcome(payload: ChainAccountCreatePayload)

    fun back()
}
