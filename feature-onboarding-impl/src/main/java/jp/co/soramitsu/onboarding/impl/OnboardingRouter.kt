package jp.co.soramitsu.onboarding.impl

import jp.co.soramitsu.account.api.domain.model.ImportMode
import jp.co.soramitsu.account.api.presentation.account.create.ChainAccountCreatePayload
import jp.co.soramitsu.account.api.presentation.create_backup_password.CreateBackupPasswordPayload
import kotlinx.coroutines.flow.Flow

interface OnboardingRouter {

    fun openCreateAccountFromOnboarding()

    fun openCreateAccountSkipWelcome(payload: ChainAccountCreatePayload)

    fun backToWelcomeScreen()

    fun openImportAccountScreen(blockChainType: Int, importMode: ImportMode)

    fun openImportAccountSkipWelcome(payload: ChainAccountCreatePayload)

    fun openCreateWalletDialog(isFromGoogleBackup: Boolean)

    fun openImportRemoteWalletDialog()

    fun openCreateBackupPasswordDialog(payload: CreateBackupPasswordPayload)

    fun openMnemonicAgreementsDialog(
        isFromGoogleBackup: Boolean,
        accountName: String
    )

    fun back()

    fun backWithResult(vararg results: Pair<String, Any?>)

    fun openSelectImportModeForResult(): Flow<ImportMode>
}
