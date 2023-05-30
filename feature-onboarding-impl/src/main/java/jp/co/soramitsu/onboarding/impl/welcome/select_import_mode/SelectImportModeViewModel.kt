package jp.co.soramitsu.onboarding.impl.welcome.select_import_mode

import dagger.hilt.android.lifecycle.HiltViewModel
import jp.co.soramitsu.account.api.domain.model.ImportMode
import jp.co.soramitsu.common.base.BaseViewModel
import jp.co.soramitsu.onboarding.impl.OnboardingRouter
import javax.inject.Inject

@HiltViewModel
class SelectImportModeViewModel @Inject constructor(
    private val router: OnboardingRouter
) : BaseViewModel(), SelectImportModeScreenInterface {

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
}
