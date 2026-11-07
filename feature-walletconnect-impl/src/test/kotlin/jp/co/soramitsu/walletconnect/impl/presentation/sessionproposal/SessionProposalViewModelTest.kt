package jp.co.soramitsu.walletconnect.impl.presentation.sessionproposal

import androidx.arch.core.executor.testing.InstantTaskExecutorRule
import co.jp.soramitsu.feature_walletconnect_impl.R
import co.jp.soramitsu.walletconnect.domain.WalletConnectInteractor
import co.jp.soramitsu.walletconnect.domain.WalletConnectRouter
import co.jp.soramitsu.walletconnect.model.WalletConnectProposal
import co.jp.soramitsu.walletconnect.model.WalletConnectValidation
import co.jp.soramitsu.walletconnect.model.WalletConnectVerifyContext
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import jp.co.soramitsu.account.api.domain.interfaces.AccountRepository
import jp.co.soramitsu.account.impl.presentation.account.mixin.api.AccountListingMixin
import jp.co.soramitsu.common.resources.ResourceManager
import jp.co.soramitsu.walletconnect.impl.presentation.WCDelegate
import jp.co.soramitsu.walletconnect.impl.presentation.awaitErrorDialog
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import kotlinx.coroutines.withTimeout
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class SessionProposalViewModelTest {

    @get:Rule
    val instantTasks = InstantTaskExecutorRule()

    private val proposal = mockk<WalletConnectProposal>(relaxed = true) {
        every { name } returns "dApp"
        every { requiredNamespaces } returns emptyMap()
        every { optionalNamespaces } returns emptyMap()
    }
    private val interactor = mockk<WalletConnectInteractor>(relaxed = true)
    private val router = mockk<WalletConnectRouter>(relaxed = true)
    private val resourceManager = mockk<ResourceManager>()
    private val accountListingMixin = mockk<AccountListingMixin> {
        every { accountsFlow(any()) } returns flowOf(emptyList())
    }

    @Before
    fun setUp() {
        Dispatchers.setMain(UnconfinedTestDispatcher())
        every { resourceManager.getString(any()) } answers { "string:${firstArg<Int>()}" }
        every { resourceManager.getString(any(), *anyVararg()) } answers {
            "string:${firstArg<Int>()}:" + secondArg<Array<Any>>().joinToString(",")
        }
        coEvery { interactor.getChains() } returns emptyList()
        coEvery { interactor.checkChainsSupported(any()) } returns Result.success(true)
    }

    @After
    fun tearDown() {
        WCDelegate.sessionProposalEvent = null
        Dispatchers.resetMain()
    }

    @Test
    fun `proposal flagged as scam by Verify is rejected and cannot be approved`() {
        WCDelegate.sessionProposalEvent = proposal to verifyContext(isScam = true)

        val viewModel = viewModel()
        val dialog = viewModel.awaitErrorDialog()
        viewModel.onApproveClick()

        assertTrue(dialog.message.startsWith("string:${R.string.walletconnect_scam_blocked}"))
        assertTrue(dialog.message.contains("drainer.example"))
        verify { interactor.rejectSession(proposal, any(), any()) }
        coVerify(exactly = 0) { interactor.approveSession(any(), any(), any(), any(), any()) }
        dialog.positiveClick()
        verify { router.back() }
    }

    @Test
    fun `proposal that Verify does not flag can still be approved`() {
        WCDelegate.sessionProposalEvent = proposal to verifyContext(isScam = null)

        viewModel().onApproveClick()

        coVerify { interactor.approveSession(proposal, any(), any(), any(), any()) }
        verify(exactly = 0) { interactor.rejectSession(any(), any(), any()) }
    }

    @Test
    fun `verified origin is shown without a warning`() {
        WCDelegate.sessionProposalEvent = proposal to WalletConnectVerifyContext(
            validation = WalletConnectValidation.VALID,
            verifiedOrigin = "https://app.example.org",
            claimedUrl = "https://app.example.org/",
            isScam = false
        )

        val state = viewModel().shownState()

        assertEquals("app.example.org", state.originHost)
        assertNull(state.originWarning)
    }

    @Test
    fun `origin that differs from the claimed URL is shown with a mismatch warning`() {
        WCDelegate.sessionProposalEvent = proposal to WalletConnectVerifyContext(
            validation = WalletConnectValidation.INVALID,
            verifiedOrigin = "https://app.examp1e.org",
            claimedUrl = "https://app.example.org",
            isScam = false
        )

        val state = viewModel().shownState()

        assertEquals("app.examp1e.org", state.originHost)
        assertEquals("string:${R.string.walletconnect_origin_mismatch}:app.examp1e.org,app.example.org", state.originWarning)
    }

    @Test
    fun `unverified dApp shows its claimed URL with an unverified warning`() {
        WCDelegate.sessionProposalEvent = proposal to WalletConnectVerifyContext.unknown("https://app.example.org")

        val state = viewModel().shownState()

        assertEquals("app.example.org", state.originHost)
        assertEquals("string:${R.string.walletconnect_origin_unverified}", state.originWarning)
    }

    private fun verifyContext(isScam: Boolean?) = WalletConnectVerifyContext(
        validation = WalletConnectValidation.UNKNOWN,
        verifiedOrigin = "https://drainer.example".takeIf { isScam == true },
        claimedUrl = "https://drainer.example",
        isScam = isScam == true
    )

    private fun SessionProposalViewModel.shownState(): SessionProposalViewState = runBlocking {
        withTimeout(10_000) { state.first { it.sessionProposal != null } }
    }

    private fun viewModel() = SessionProposalViewModel(
        accountListingMixin = accountListingMixin,
        walletConnectInteractor = interactor,
        walletConnectRouter = router,
        resourceManager = resourceManager,
        accountRepository = mockk<AccountRepository>(relaxed = true)
    )
}
