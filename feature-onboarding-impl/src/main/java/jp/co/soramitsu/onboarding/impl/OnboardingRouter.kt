package jp.co.soramitsu.onboarding.impl

import jp.co.soramitsu.account.api.domain.model.ImportMode
import jp.co.soramitsu.account.api.presentation.account.create.ChainAccountCreatePayload
import kotlinx.coroutines.flow.Flow

interface OnboardingRouter {

    fun openCreateAccountFromOnboarding()
    fun openCreateAccountSkipWelcome(payload: ChainAccountCreatePayload)

    fun backToWelcomeScreen()

    fun openImportAccountScreen(blockChainType: Int, importMode: ImportMode)

    fun openImportAccountSkipWelcome(payload: ChainAccountCreatePayload)

    fun openImportRemoteWalletDialog()

    fun back()

    fun backWithResult(vararg results: Pair<String, Any?>)

    fun openSelectImportModeForResult(): Flow<ImportMode>
}
