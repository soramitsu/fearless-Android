package jp.co.soramitsu.onboarding.impl.welcome

import androidx.arch.core.executor.testing.InstantTaskExecutorRule
import androidx.lifecycle.SavedStateHandle
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import jp.co.soramitsu.account.api.domain.interfaces.AccountRepository
import jp.co.soramitsu.account.api.domain.model.ImportMode
import jp.co.soramitsu.account.api.presentation.backup.GoogleBackupIssue
import jp.co.soramitsu.backup.BackupService
import jp.co.soramitsu.backup.GoogleSignInResult
import jp.co.soramitsu.common.R
import jp.co.soramitsu.common.base.models.ErrorDialogState
import jp.co.soramitsu.common.model.WalletEcosystem
import jp.co.soramitsu.common.resources.ResourceManager
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
import org.junit.Before
import org.junit.Rule
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class WelcomeGoogleBackupTest {
    @get:Rule val instantTaskRule = InstantTaskExecutorRule()
    private val dispatcher = StandardTestDispatcher()
    private val router = mockk<OnboardingRouter>(relaxed = true)
    private val repository = mockk<AccountRepository>(relaxed = true)
    private val onboarding = mockk<OnboardingInteractor>(relaxed = true)
    private val backupService = mockk<BackupService>(relaxed = true)
    private val resourceManager = mockk<ResourceManager>()

    @Before
    fun setup() {
        Dispatchers.setMain(dispatcher)
        coEvery { repository.isAccountSelected() } returns false
        every { resourceManager.getString(any()) } answers { "text-${firstArg<Int>()}" }
    }

    @After
    fun teardown() = Dispatchers.resetMain()

    private fun viewModel() = WelcomeViewModel(
        router,
        mockk(relaxed = true),
        SavedStateHandle(mapOf(WelcomeFragment.KEY_PAYLOAD to WelcomeFragmentPayload(false, null))),
        backupService,
        mockk(relaxed = true),
        onboarding,
        repository,
        mockk(relaxed = true),
        resourceManager,
        mockk(relaxed = true)
    )

    private fun WelcomeViewModel.dialog(): ErrorDialogState = errorDialogStateLiveData.value!!.peekContent()

    @Test
    fun `a build without google sign-in goes straight to the other imports`() = runTest(dispatcher) {
        every { backupService.isAvailable } returns false
        val vm = viewModel()

        vm.authorizeGoogle(mockk())
        runCurrent()

        assertEquals("text-${GoogleBackupIssue.UNAVAILABLE.titleRes}", vm.dialog().title)
        assertEquals("text-${R.string.google_backup_import_with_phrase}", vm.dialog().positiveButtonText)
        assertEquals("text-${R.string.google_backup_import_with_json}", vm.dialog().negativeButtonText)
        coVerify(exactly = 0) { backupService.authorize(any()) }

        vm.dialog().positiveClick()
        vm.dialog().negativeClick()

        verify(exactly = 1) { router.openImportAccountScreen(WalletEcosystem.Substrate, ImportMode.MnemonicPhrase) }
        verify(exactly = 1) { router.openImportAccountScreen(WalletEcosystem.Substrate, ImportMode.Json) }
    }

    @Test
    fun `a failed google sign-in can be retried or skipped`() = runTest(dispatcher) {
        every { backupService.isAvailable } returns true
        val vm = viewModel()
        runCurrent()
        vm.events.first()

        vm.onGoogleSignInFailed(GoogleSignInResult.Network)

        assertEquals("text-${GoogleBackupIssue.NO_CONNECTION.titleRes}", vm.dialog().title)
        assertEquals("text-${R.string.google_backup_import_another_way}", vm.dialog().negativeButtonText)
        vm.dialog().positiveClick()
        assertEquals(WelcomeEvent.AuthorizeGoogle, vm.events.first())
        vm.dialog().negativeClick()
        verify(exactly = 1) { router.openImportAccountScreen(WalletEcosystem.Substrate, ImportMode.MnemonicPhrase) }
    }

    @Test
    fun `a sign-in set up wrongly for this installation is explained as unavailable`() = runTest(dispatcher) {
        val vm = viewModel()

        vm.onGoogleSignInFailed(GoogleSignInResult.ConfigurationError)

        assertEquals("text-${GoogleBackupIssue.UNAVAILABLE.messageRes}", vm.dialog().message)
    }

    @Test
    fun `backing out of google sign-in needs no message`() = runTest(dispatcher) {
        val vm = viewModel()

        vm.onGoogleSignInFailed(GoogleSignInResult.Cancelled)

        assertNull(vm.errorDialogStateLiveData.value)
    }
}
