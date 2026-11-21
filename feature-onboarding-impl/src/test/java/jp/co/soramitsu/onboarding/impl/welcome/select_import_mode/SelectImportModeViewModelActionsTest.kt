package jp.co.soramitsu.onboarding.impl.welcome.select_import_mode

import androidx.arch.core.executor.testing.InstantTaskExecutorRule
import androidx.lifecycle.SavedStateHandle
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import io.mockk.verifyOrder
import jp.co.soramitsu.account.api.domain.PendulumPreInstalledAccountsScenario
import jp.co.soramitsu.account.api.domain.model.ImportMode
import jp.co.soramitsu.onboarding.impl.OnboardingRouter
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test

class SelectImportModeViewModelActionsTest {
    @get:Rule
    val instantTaskRule = InstantTaskExecutorRule()

    private val router = mockk<OnboardingRouter>(relaxed = true)
    private val preInstalledAccounts = mockk<PendulumPreInstalledAccountsScenario> {
        every { isFeatureEnabled() } returns false
    }
    private val viewModel = SelectImportModeViewModel(router, SavedStateHandle(), preInstalledAccounts)

    @Test
    fun everyChoiceReturnsItsImportModeToTheCaller() {
        viewModel.onMnemonicPhraseClick()
        viewModel.onRawSeedClick()
        viewModel.onJsonClick()
        viewModel.onGoogleClick()
        viewModel.onGoogleSignInSuccess()
        viewModel.onPreinstalledImportClick()

        verifyOrder {
            router.backWithResult(SelectImportModeDialog.RESULT_IMPORT_MODE to ImportMode.MnemonicPhrase)
            router.backWithResult(SelectImportModeDialog.RESULT_IMPORT_MODE to ImportMode.RawSeed)
            router.backWithResult(SelectImportModeDialog.RESULT_IMPORT_MODE to ImportMode.Json)
            router.backWithResult(SelectImportModeDialog.RESULT_IMPORT_MODE to ImportMode.Google)
            router.backWithResult(SelectImportModeDialog.RESULT_IMPORT_MODE to ImportMode.Google)
            router.backWithResult(SelectImportModeDialog.RESULT_IMPORT_MODE to ImportMode.Preinstalled)
        }
    }

    @Test
    fun cancelClosesWithoutAResultAndASignInErrorIsShown() {
        viewModel.onCancelClick()
        viewModel.onGoogleLoginError("12501")

        verify(exactly = 1) { router.back() }
        verify(exactly = 0) { router.backWithResult(*anyVararg()) }
        assertEquals("GoogleLoginError\n12501", viewModel.errorLiveData.value!!.peekContent())
    }
}
