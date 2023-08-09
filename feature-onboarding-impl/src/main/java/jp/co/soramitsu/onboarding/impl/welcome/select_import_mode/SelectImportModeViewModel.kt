package jp.co.soramitsu.onboarding.impl.welcome.select_import_mode

import android.content.Intent
import androidx.activity.compose.ManagedActivityResultLauncher
import androidx.activity.result.ActivityResult
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import jp.co.soramitsu.account.api.domain.model.ImportMode
import jp.co.soramitsu.backup.BackupService
import jp.co.soramitsu.common.base.BaseViewModel
import jp.co.soramitsu.onboarding.impl.OnboardingRouter
import kotlinx.coroutines.launch

@HiltViewModel
class SelectImportModeViewModel @Inject constructor(
    private val router: OnboardingRouter,
    private val backupService: BackupService
) : BaseViewModel(), SelectImportModeScreenInterface {

    override fun onCancelClick() {
        router.back()
    }

    override fun onGoogleClick(launcher: ManagedActivityResultLauncher<Intent, ActivityResult>) {
        launch {
            try {
                backupService.logout()
                if (backupService.authorize(launcher)) {
                    onGoogleSignInSuccess()
                }
            } catch (e: Exception) {
                e.printStackTrace()
                showError(e)
            }
        }
    }

    override fun onMnemonicPhraseClick() {
        router.backWithResult(SelectImportModeDialog.RESULT_IMPORT_MODE to ImportMode.MnemonicPhrase)
    }

    override fun onRawSeedClick() {
        router.backWithResult(SelectImportModeDialog.RESULT_IMPORT_MODE to ImportMode.RawSeed)
    }

    override fun onJsonClick() {
        router.backWithResult(SelectImportModeDialog.RESULT_IMPORT_MODE to ImportMode.Json)
    }

    override fun onGoogleSignInSuccess() {
        router.backWithResult(SelectImportModeDialog.RESULT_IMPORT_MODE to ImportMode.Google)
    }

    override fun onGoogleLoginError(message: String) {
        showError("GoogleLoginError\n$message")
    }
}
