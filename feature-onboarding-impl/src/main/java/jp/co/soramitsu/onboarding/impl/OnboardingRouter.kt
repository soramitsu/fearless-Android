package jp.co.soramitsu.onboarding.impl

import jp.co.soramitsu.account.api.domain.model.AccountType
import jp.co.soramitsu.account.api.domain.model.ImportMode
import jp.co.soramitsu.account.api.presentation.account.create.ChainAccountCreatePayload
import jp.co.soramitsu.account.api.presentation.create_backup_password.CreateBackupPasswordPayload
import jp.co.soramitsu.onboarding.impl.welcome.WelcomeViewModel
import kotlinx.coroutines.flow.Flow

interface OnboardingRouter {

    fun openCreateAccountFromOnboarding(accountType: AccountType)

//    fun openCreateAccountSkipWelcome(payload: ChainAccountCreatePayload)

    fun backToWelcomeScreen()

    fun openImportAccountScreen(blockChainType: Int, importMode: ImportMode)

//    fun openImportAccountSkipWelcome(payload: ChainAccountCreatePayload)

    fun openCreateWalletDialog(isFromGoogleBackup: Boolean)

    fun openImportRemoteWalletDialog()

    fun openMnemonicAgreementsDialog(
        isFromGoogleBackup: Boolean,
        accountName: String,
        accountType: String
    )

    fun back()

    fun backWithResult(vararg results: Pair<String, Any?>)

    fun openSelectImportModeForResult(): Flow<ImportMode>

    fun openCreatePincode()

    fun openInitialCheckPincode()
}
