package jp.co.soramitsu.onboarding.impl

import jp.co.soramitsu.account.api.domain.model.AccountType
import jp.co.soramitsu.account.api.domain.model.ImportMode
import jp.co.soramitsu.account.api.presentation.importing.ImportAccountType
import kotlinx.coroutines.flow.Flow

interface OnboardingRouter {

    fun openCreateAccountFromOnboarding(accountType: AccountType)

    fun backToWelcomeScreen()

    fun openImportAccountScreen(importAccountType: ImportAccountType, importMode: ImportMode)

    fun openCreateWalletDialog(isFromGoogleBackup: Boolean)

    fun openImportRemoteWalletDialog()

    fun back()

    fun backWithResult(vararg results: Pair<String, Any?>)

    fun openSelectImportModeForResult(): Flow<ImportMode>

    fun openCreatePincode()

    fun openInitialCheckPincode()
}
