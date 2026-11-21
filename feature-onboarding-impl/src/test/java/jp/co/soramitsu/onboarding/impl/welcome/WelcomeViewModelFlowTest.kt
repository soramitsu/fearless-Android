package jp.co.soramitsu.onboarding.impl.welcome

import androidx.activity.result.ActivityResultLauncher
import android.content.Intent
import androidx.arch.core.executor.testing.InstantTaskExecutorRule
import androidx.lifecycle.SavedStateHandle
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.coVerifyOrder
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import jp.co.soramitsu.account.api.domain.PendulumPreInstalledAccountsScenario
import jp.co.soramitsu.account.api.domain.interfaces.AccountInteractor
import jp.co.soramitsu.account.api.domain.interfaces.AccountRepository
import jp.co.soramitsu.account.api.domain.model.AccountType
import jp.co.soramitsu.account.api.domain.model.AddAccountPayload
import jp.co.soramitsu.account.api.domain.model.ImportMode
import jp.co.soramitsu.backup.BackupService
import jp.co.soramitsu.common.data.network.AppLinksProvider
import jp.co.soramitsu.common.model.WalletEcosystem
import jp.co.soramitsu.fearless_utils.encrypt.mnemonic.Mnemonic
import jp.co.soramitsu.onboarding.api.data.OnboardingConfig
import jp.co.soramitsu.onboarding.api.domain.OnboardingInteractor
import jp.co.soramitsu.onboarding.impl.OnboardingRouter
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/** Runs under Robolectric because a failed remote config download is logged with android.util.Log. */
@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
class WelcomeViewModelFlowTest {
    @get:Rule
    val instantTaskRule = InstantTaskExecutorRule()

    private val dispatcher = StandardTestDispatcher()
    private val router = mockk<OnboardingRouter>(relaxed = true)
    private val repository = mockk<AccountRepository>(relaxed = true)
    private val onboarding = mockk<OnboardingInteractor>(relaxed = true)
    private val interactor = mockk<AccountInteractor>(relaxed = true)
    private val backupService = mockk<BackupService>(relaxed = true)
    private val appLinks = mockk<AppLinksProvider>()
    private val preinstalled = mockk<PendulumPreInstalledAccountsScenario>(relaxed = true)
    private var payload = WelcomeFragmentPayload(displayBack = false, createChainAccount = null)

    private val slides = listOf(
        OnboardingConfig.OnboardingConfigItem.Variants.ScreenInfo(
            OnboardingConfig.OnboardingConfigItem.Variants.ScreenInfo.TitleInfo("Slide", "#ffffff"),
            "Slide description",
            "slide.png"
        )
    )
    private val config = OnboardingConfig.OnboardingConfigItem(
        minVersion = "4.3.0",
        background = "background.png",
        enEn = OnboardingConfig.OnboardingConfigItem.Variants(new = emptyList(), regular = slides)
    )

    @Before
    fun setup() {
        Dispatchers.setMain(dispatcher)
        coEvery { repository.isAccountSelected() } returns true
        coEvery { repository.isCodeSet() } returns true
        coEvery { onboarding.getAppVersionSupportedConfig() } returns Result.success(config)
        every { onboarding.shouldShowWelcomeSlides("4.3.0") } returns true
        every { appLinks.termsUrl } returns "https://example.invalid/terms"
        every { appLinks.privacyUrl } returns "https://example.invalid/privacy"
    }

    @After
    fun teardown() = Dispatchers.resetMain()

    private fun viewModel() = WelcomeViewModel(
        router,
        appLinks,
        SavedStateHandle(mapOf(WelcomeFragment.KEY_PAYLOAD to payload)),
        backupService,
        preinstalled,
        onboarding,
        repository,
        interactor,
        mockk(relaxed = true),
        mockk(relaxed = true)
    )

    private fun TestScope.started(): WelcomeViewModel = viewModel().also { runCurrent() }

    @Test
    fun anExistingWalletSeesNewSlidesOnceAndThenUnlocks() = runTest(dispatcher) {
        val vm = started()

        assertEquals(WelcomeEvent.Onboarding.PagerScreen, vm.events.first())
        assertEquals(slides, vm.onboardingFlowState.value!!.toList())
        assertEquals("background.png", vm.onboardingBackground.value)
        assertEquals(WelcomeEvent.Onboarding.SplashScreen.route, vm.startDestination)

        vm.onClose()
        runCurrent()
        vm.onSkip()
        runCurrent()

        verify(exactly = 1) { onboarding.saveWelcomeSlidesShownVersion("4.3.0") }
        verify(exactly = 2) { router.openInitialCheckPincode() }
        verify(exactly = 0) { router.openCreatePincode() }
    }

    @Test
    fun slidesAlreadySeenGoStraightToThePinCode() = runTest(dispatcher) {
        every { onboarding.shouldShowWelcomeSlides("4.3.0") } returns false

        val vm = started()

        assertNull(vm.onboardingFlowState.value)
        verify(exactly = 1) { router.openInitialCheckPincode() }
    }

    @Test
    fun withoutAPinCodeTheWalletAsksToCreateOne() = runTest(dispatcher) {
        coEvery { onboarding.getAppVersionSupportedConfig() } returns Result.success(null)
        coEvery { repository.isCodeSet() } returns false

        started()

        verify(exactly = 1) { router.openCreatePincode() }
        verify(exactly = 0) { router.openInitialCheckPincode() }
    }

    @Test
    fun aFailedSlidesDownloadDoesNotBlockUnlocking() = runTest(dispatcher) {
        coEvery { onboarding.getAppVersionSupportedConfig() } returns Result.failure(IllegalStateException("offline"))

        val vm = started()

        assertNull(vm.onboardingFlowState.value)
        verify(exactly = 1) { router.openInitialCheckPincode() }
        vm.onClose()
        runCurrent()
        verify(exactly = 0) { onboarding.saveWelcomeSlidesShownVersion(any()) }
    }

    @Test
    fun closingSlidesWithoutAWalletReturnsToTheWelcomePage() = runTest(dispatcher) {
        coEvery { repository.isAccountSelected() } returns false
        val vm = started()
        assertTrue(vm.events.first() is WelcomeEvent.Onboarding.WelcomeScreen)

        vm.onClose()
        runCurrent()

        val event = vm.events.first()
        assertTrue(event is WelcomeEvent.Onboarding.WelcomeScreen)
        assertNull((event as WelcomeEvent.Onboarding.WelcomeScreen).accountType)
        verify(exactly = 0) { router.openInitialCheckPincode() }
    }

    @Test
    fun startingTheSlidesShowsThePager() = runTest(dispatcher) {
        val vm = started()
        vm.events.first()

        vm.onStart()

        assertEquals(WelcomeEvent.Onboarding.PagerScreen, vm.events.first())
    }

    @Test
    fun termsAndPrivacyOpenTheirPages() = runTest(dispatcher) {
        val vm = started()

        vm.termsClicked()
        assertEquals("https://example.invalid/terms", vm.openBrowserEvent.value!!.peekContent())
        vm.privacyClicked()
        assertEquals("https://example.invalid/privacy", vm.openBrowserEvent.value!!.peekContent())
    }

    @Test
    fun googleAndPreinstalledEntriesBecomeScreenEvents() = runTest(dispatcher) {
        payload = WelcomeFragmentPayload(displayBack = true, createChainAccount = null, route = "WelcomeScreen")
        every { preinstalled.isFeatureEnabled() } returns true
        val vm = started()

        assertTrue(vm.state.value.isBackVisible)
        assertTrue(vm.state.value.preinstalledFeatureEnabled)
        assertFalse(vm.state.value.isLoading)
        vm.googleSigninClicked()
        assertEquals(WelcomeEvent.AuthorizeGoogle, vm.events.first())
        vm.getPreInstalledWalletClicked()
        assertEquals(WelcomeEvent.ScanQR, vm.events.first())
    }

    @Test
    fun choosingAnImportModeOpensItOrSignsInWithGoogle() = runTest(dispatcher) {
        payload = WelcomeFragmentPayload(displayBack = false, createChainAccount = null, route = "WelcomeScreen")
        every { router.openSelectImportModeForResult() } returns flowOf(ImportMode.RawSeed, ImportMode.Google)
        val vm = started()

        vm.importAccountClicked(AccountType.SubstrateOrEvm)
        runCurrent()

        verify(exactly = 1) { router.openImportAccountScreen(WalletEcosystem.Substrate, ImportMode.RawSeed) }
        assertEquals(WelcomeEvent.AuthorizeGoogle, vm.events.first())
    }

    @Test
    fun substrateCreateOpensTheCreationFlowAndChoiceWithoutIntentAsksForAnAccountType() = runTest(dispatcher) {
        payload = WelcomeFragmentPayload(displayBack = false, createChainAccount = null, route = "WelcomeScreen")
        val vm = started()

        vm.createAccountClicked(AccountType.SubstrateOrEvm)
        verify(exactly = 1) { router.openCreateAccountFromOnboarding(AccountType.SubstrateOrEvm) }

        vm.substrateEvmClick()
        val substrate = vm.events.first() as WelcomeEvent.Onboarding.WelcomeScreen
        assertEquals(AccountType.SubstrateOrEvm, substrate.accountType)
        vm.tonClick()
        val ton = vm.events.first() as WelcomeEvent.Onboarding.WelcomeScreen
        assertEquals(AccountType.Ton, ton.accountType)
    }

    @Test
    fun tonCreationMakesAnUnbackedTwentyFourWordWalletAndOpensMain() = runTest(dispatcher) {
        payload = WelcomeFragmentPayload(displayBack = false, createChainAccount = null, route = "WelcomeScreen")
        coEvery { interactor.generateMnemonic(Mnemonic.Length.TWENTY_FOUR) } returns listOf("alpha", "beta", "gamma")
        coEvery { interactor.createAccount(any()) } returns Result.success(7L)
        coEvery { interactor.isCodeSet() } returns true
        val vm = started()

        vm.createAccountClicked(AccountType.Ton)
        assertTrue(vm.showLoading.value)
        runCurrent()

        coVerifyOrder {
            interactor.generateMnemonic(Mnemonic.Length.TWENTY_FOUR)
            interactor.createAccount(AddAccountPayload.Ton(accountName = "Wallet", mnemonic = "alpha beta gamma", isBackedUp = false))
        }
        verify(exactly = 1) { router.openMain() }
        verify(exactly = 0) { router.openCreatePincode() }
    }

    @Test
    fun tonCreationWithoutAPinCodeAsksForOne() = runTest(dispatcher) {
        payload = WelcomeFragmentPayload(displayBack = false, createChainAccount = null, route = "WelcomeScreen")
        coEvery { interactor.generateMnemonic(any()) } returns listOf("alpha")
        coEvery { interactor.createAccount(any()) } returns Result.success(7L)
        coEvery { interactor.isCodeSet() } returns false
        val vm = started()

        vm.createAccountClicked(AccountType.Ton)
        runCurrent()

        verify(exactly = 1) { router.openCreatePincode() }
        verify(exactly = 0) { router.openMain() }
    }

    @Test
    fun aFailedTonCreationShowsTheErrorAndStopsLoading() = runTest(dispatcher) {
        payload = WelcomeFragmentPayload(displayBack = false, createChainAccount = null, route = "WelcomeScreen")
        coEvery { interactor.generateMnemonic(any()) } returns listOf("alpha")
        coEvery { interactor.createAccount(any()) } returns Result.failure(IllegalStateException("keystore unavailable"))
        val vm = started()

        vm.createAccountClicked(AccountType.Ton)
        runCurrent()

        assertFalse(vm.showLoading.value)
        assertEquals("keystore unavailable", vm.errorLiveData.value!!.peekContent())
        verify(exactly = 0) { router.openMain() }
        verify(exactly = 0) { router.openCreatePincode() }
    }

    @Test
    fun scannedPreinstalledWalletIsImportedBeforeThePinCode() = runTest(dispatcher) {
        payload = WelcomeFragmentPayload(displayBack = false, createChainAccount = null, route = "WelcomeScreen")
        coEvery { preinstalled.import("cafe") } returns Result.success(1L)
        val vm = started()

        vm.onQrScanResult("cafe")
        runCurrent()

        coVerify(exactly = 1) { preinstalled.import("cafe") }
        verify(exactly = 1) { router.openCreatePincode() }
    }

    @Test
    fun aMissingOrRejectedQrCodeIsReported() = runTest(dispatcher) {
        payload = WelcomeFragmentPayload(displayBack = false, createChainAccount = null, route = "WelcomeScreen")
        coEvery { preinstalled.import("bad") } returns Result.failure(RuntimeException("Can't decode qr code."))
        val vm = started()

        vm.onQrScanResult(null)
        assertEquals("Can't scan qr code", vm.errorLiveData.value!!.peekContent())

        vm.onQrScanResult("bad")
        runCurrent()
        assertEquals("Can't decode qr code.", vm.errorLiveData.value!!.peekContent())
        verify(exactly = 0) { router.openCreatePincode() }
    }

    @Test
    fun aSuccessfulGoogleSignInOpensTheRemoteWalletList() = runTest(dispatcher) {
        payload = WelcomeFragmentPayload(displayBack = false, createChainAccount = null, route = "WelcomeScreen")
        val launcher = mockk<ActivityResultLauncher<Intent>>()
        every { backupService.isAvailable } returns true
        coEvery { backupService.authorize(launcher) } returns true
        val vm = started()

        vm.authorizeGoogle(launcher)
        runCurrent()

        coVerifyOrder {
            backupService.logout()
            backupService.authorize(launcher)
        }
        verify(exactly = 1) { router.openImportRemoteWalletDialog() }
    }

    @Test
    fun aCancelledGoogleSignInStaysOnTheWelcomePage() = runTest(dispatcher) {
        payload = WelcomeFragmentPayload(displayBack = false, createChainAccount = null, route = "WelcomeScreen")
        val launcher = mockk<ActivityResultLauncher<Intent>>()
        every { backupService.isAvailable } returns true
        coEvery { backupService.authorize(launcher) } returns false
        val vm = started()

        vm.authorizeGoogle(launcher)
        runCurrent()

        verify(exactly = 0) { router.openImportRemoteWalletDialog() }
        assertNull(vm.errorDialogStateLiveData.value)
    }
}
