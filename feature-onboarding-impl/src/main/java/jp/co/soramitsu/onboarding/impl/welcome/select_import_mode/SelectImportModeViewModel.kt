package jp.co.soramitsu.onboarding.impl.welcome.select_import_mode

import androidx.lifecycle.SavedStateHandle
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
    savedStateHandle: SavedStateHandle,
    pendulumPreInstalledAccountsScenario: PendulumPreInstalledAccountsScenario
) : BaseViewModel(), SelectImportModeScreenInterface {

    // Both restore a whole wallet. The add-account import screen has no source for either and would
    // silently open the recovery phrase import instead.
    private val isAddingAccount = savedStateHandle.get<Boolean>(SelectImportModeDialog.IS_ADDING_ACCOUNT_KEY) == true

    val state: StateFlow<SelectImportModeState> =
        MutableStateFlow(
            SelectImportModeState(
                preinstalledFeatureEnabled = !isAddingAccount && pendulumPreInstalledAccountsScenario.isFeatureEnabled(),
                isGoogleImportAllowed = !isAddingAccount
            )
        )

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
