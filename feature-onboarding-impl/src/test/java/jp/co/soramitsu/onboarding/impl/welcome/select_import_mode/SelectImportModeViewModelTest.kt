package jp.co.soramitsu.onboarding.impl.welcome.select_import_mode

import androidx.lifecycle.SavedStateHandle
import io.mockk.every
import io.mockk.mockk
import jp.co.soramitsu.account.api.domain.PendulumPreInstalledAccountsScenario
import jp.co.soramitsu.onboarding.impl.OnboardingRouter
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SelectImportModeViewModelTest {
    private val router = mockk<OnboardingRouter>(relaxed = true)
    private val preInstalledAccounts = mockk<PendulumPreInstalledAccountsScenario> {
        every { isFeatureEnabled() } returns true
    }

    private fun viewModel(bundle: Map<String, Any?>) = SelectImportModeViewModel(
        router,
        SavedStateHandle(bundle),
        preInstalledAccounts
    )

    @Test
    fun `a new wallet can be restored from google drive or a pre-installed account`() {
        val state = viewModel(mapOf(SelectImportModeDialog.IS_ADDING_ACCOUNT_KEY to false)).state.value

        assertTrue(state.isGoogleImportAllowed)
        assertTrue(state.preinstalledFeatureEnabled)
    }

    @Test
    fun `adding an account to a wallet offers only the imports that can add one`() {
        val state = viewModel(mapOf(SelectImportModeDialog.IS_ADDING_ACCOUNT_KEY to true)).state.value

        assertFalse(state.isGoogleImportAllowed)
        assertFalse(state.preinstalledFeatureEnabled)
    }

    @Test
    fun `a dialog opened without arguments keeps every wallet import`() {
        val state = viewModel(emptyMap()).state.value

        assertTrue(state.isGoogleImportAllowed)
        assertTrue(state.preinstalledFeatureEnabled)
    }
}
