package jp.co.soramitsu.onboarding.impl.welcome.select_import_mode

import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import jp.co.soramitsu.account.api.domain.PendulumPreInstalledAccountsScenario
import jp.co.soramitsu.account.api.domain.model.ImportMode
import jp.co.soramitsu.common.base.BaseViewModel
import jp.co.soramitsu.onboarding.impl.OnboardingRouter
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

@HiltViewModel
class SelectImportModeViewModel @Inject constructor(
    private val router: OnboardingRouter,
    pendulumPreInstalledAccountsScenario: PendulumPreInstalledAccountsScenario
) : BaseViewModel(), SelectImportModeScreenInterface {

    val state: StateFlow<SelectImportModeState> =
        MutableStateFlow(SelectImportModeState(pendulumPreInstalledAccountsScenario.isFeatureEnabled()))

    override fun onCancelClick() {
        router.back()
    }

    override fun onGoogleClick() {
        router.backWithResult(SelectImportModeDialog.RESULT_IMPORT_MODE to ImportMode.Google)
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

    override fun onPreinstalledImportClick() {
        router.backWithResult(SelectImportModeDialog.RESULT_IMPORT_MODE to ImportMode.Preinstalled)
    }
}
