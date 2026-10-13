package jp.co.soramitsu.onboarding.impl.welcome

import androidx.arch.core.executor.testing.InstantTaskExecutorRule
import androidx.lifecycle.SavedStateHandle
import io.mockk.coVerify
import io.mockk.coEvery
import io.mockk.mockk
import io.mockk.verify
import jp.co.soramitsu.account.api.domain.interfaces.AccountRepository
import jp.co.soramitsu.account.api.domain.model.AccountType
import jp.co.soramitsu.account.api.domain.model.ImportMode
import jp.co.soramitsu.common.model.WalletEcosystem
import jp.co.soramitsu.onboarding.api.domain.OnboardingInteractor
import jp.co.soramitsu.onboarding.impl.OnboardingRouter
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class WelcomeIntentTest {
    @get:Rule val instantTaskRule = InstantTaskExecutorRule()
    private val dispatcher = StandardTestDispatcher()
    private val router = mockk<OnboardingRouter>(relaxed = true)
    private val repository = mockk<AccountRepository>(relaxed = true)
    private val onboarding = mockk<OnboardingInteractor>(relaxed = true)
    private val savedState = SavedStateHandle(mapOf(
        WelcomeFragment.KEY_PAYLOAD to WelcomeFragmentPayload(false, null)
    ))

    @Before
    fun setup() {
        Dispatchers.setMain(dispatcher)
        coEvery { repository.isAccountSelected() } returns false
        coEvery { onboarding.getAppVersionSupportedConfig() } returns Result.success(null)
    }

    @After
    fun teardown() = Dispatchers.resetMain()

    private fun viewModel() = WelcomeViewModel(
        router, mockk(relaxed = true), savedState, mockk(relaxed = true),
        mockk(relaxed = true), onboarding, repository, mockk(relaxed = true)
    )

    @Test
    fun firstLaunchOffersTaskChoiceBeforeNetwork() = runTest(dispatcher) {
        val vm = viewModel()
        runCurrent()
        val first = vm.events.first()
        assertTrue(first is WelcomeEvent.Onboarding.WelcomeScreen)
        assertNull((first as WelcomeEvent.Onboarding.WelcomeScreen).accountType)
        coVerify(exactly = 0) { onboarding.getAppVersionSupportedConfig() }
        verify(exactly = 0) { router.openCreateAccountFromOnboarding(any()) }
    }

    @Test
    fun createWaitsForNetworkThenKeepsExistingAccountType() = runTest(dispatcher) {
        val vm = viewModel()
        runCurrent()
        vm.events.first()
        vm.createAccountClicked(null)
        assertEquals(WelcomeEvent.Onboarding.SelectEcosystemScreen, vm.events.first())
        verify(exactly = 0) { router.openCreateAccountFromOnboarding(any()) }
        vm.substrateEvmClick()
        verify(exactly = 1) { router.openCreateAccountFromOnboarding(AccountType.SubstrateOrEvm) }
    }

    @Test
    fun restoreIntentSurvivesRecreationAndUsesTonImport() = runTest(dispatcher) {
        val original = viewModel()
        runCurrent()
        original.events.first()
        original.importAccountClicked(null)
        assertEquals(WelcomeEvent.Onboarding.SelectEcosystemScreen, original.events.first())
        val recreated = viewModel()
        runCurrent()
        recreated.tonClick()
        verify(exactly = 1) { router.openImportAccountScreen(WalletEcosystem.Ton, ImportMode.MnemonicPhrase) }
        verify(exactly = 0) { router.openCreateAccountFromOnboarding(any()) }
    }
    @Test
    fun explicitAccountEntryKeepsItsNetworkAndImportPath() = runTest(dispatcher) {
        val route = "WelcomeScreen?accountType=Ton"
        savedState[WelcomeFragment.KEY_PAYLOAD] = WelcomeFragmentPayload(true, null, route)
        val vm = viewModel()
        runCurrent()
        assertEquals(route, vm.startDestination)
        vm.importAccountClicked(AccountType.Ton)
        verify(exactly = 1) { router.openImportAccountScreen(WalletEcosystem.Ton, ImportMode.MnemonicPhrase) }
        coVerify(exactly = 0) { onboarding.getAppVersionSupportedConfig() }
    }

    @Test
    fun existingWalletEntryKeepsTaskChoiceAndDoesNotWaitForRemoteSlides() = runTest(dispatcher) {
        coEvery { repository.isAccountSelected() } returns true
        savedState[WelcomeFragment.KEY_PAYLOAD] = WelcomeFragmentPayload(true, null, "WelcomeScreen")
        val vm = viewModel()
        runCurrent()
        assertEquals("WelcomeScreen", vm.startDestination)
        vm.createAccountClicked(null)
        assertEquals(WelcomeEvent.Onboarding.SelectEcosystemScreen, vm.events.first())
        vm.importAccountClicked(null)
        assertEquals(WelcomeEvent.Onboarding.SelectEcosystemScreen, vm.events.first())
        vm.tonClick()
        verify(exactly = 1) { router.openImportAccountScreen(WalletEcosystem.Ton, ImportMode.MnemonicPhrase) }
        verify(exactly = 0) { router.openCreateAccountFromOnboarding(any()) }
        coVerify(exactly = 0) { onboarding.getAppVersionSupportedConfig() }
    }

    @Test
    fun rootBackReturnsToTheExistingWalletRoute() = runTest(dispatcher) {
        savedState[WelcomeFragment.KEY_PAYLOAD] = WelcomeFragmentPayload(true, null, "WelcomeScreen")
        val vm = viewModel()
        runCurrent()
        vm.backClicked()
        assertEquals(WelcomeEvent.Back, vm.events.first())
        verify(exactly = 0) { router.back() }
        vm.exitOnboarding()
        verify(exactly = 1) { router.back() }
    }

}
