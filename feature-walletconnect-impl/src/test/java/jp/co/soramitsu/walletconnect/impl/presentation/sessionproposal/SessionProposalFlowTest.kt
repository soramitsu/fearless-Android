package jp.co.soramitsu.walletconnect.impl.presentation.sessionproposal

import android.graphics.drawable.Drawable
import androidx.arch.core.executor.testing.InstantTaskExecutorRule
import co.jp.soramitsu.feature_walletconnect_impl.R
import co.jp.soramitsu.walletconnect.domain.WalletConnectInteractor
import co.jp.soramitsu.walletconnect.domain.WalletConnectRouter
import co.jp.soramitsu.walletconnect.model.ChainChooseResult
import co.jp.soramitsu.walletconnect.model.WalletConnectMetadata
import co.jp.soramitsu.walletconnect.model.WalletConnectProposal
import co.jp.soramitsu.walletconnect.model.WalletConnectProposalNamespace
import co.jp.soramitsu.walletconnect.model.WalletConnectValidation
import co.jp.soramitsu.walletconnect.model.WalletConnectVerifyContext
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import jp.co.soramitsu.account.api.domain.interfaces.AccountRepository
import jp.co.soramitsu.account.impl.presentation.account.mixin.api.AccountListingMixin
import jp.co.soramitsu.account.impl.presentation.account.model.LightMetaAccountUi
import jp.co.soramitsu.common.data.storage.pin.WalletSessionLock
import jp.co.soramitsu.common.resources.ResourceManager
import jp.co.soramitsu.common.utils.IgnoredOnEquals
import jp.co.soramitsu.runtime.multiNetwork.chain.model.Chain
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
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Rule
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class SessionProposalFlowTest {

    @get:Rule
    val instantTasks = InstantTaskExecutorRule()

    private val proposal = WalletConnectProposal(
        id = 1,
        pairingTopic = "pairing",
        proposer = WalletConnectMetadata("Swap", "", "https://swap.example", listOf("icon")),
        proposerPublicKey = "key",
        requiredNamespaces = mapOf(
            "eip155" to WalletConnectProposalNamespace(listOf("eip155:1"), listOf("eth_sendTransaction", "personal_sign"), listOf("chainChanged"))
        ),
        optionalNamespaces = mapOf(
            "eip155" to WalletConnectProposalNamespace(listOf("eip155:137"), listOf("eth_signTypedData_v4"), listOf("accountsChanged"))
        ),
        relayProtocol = "irn",
        expiryTimestamp = 0
    )
    private val icon = mockk<Drawable>()
    private val interactor = mockk<WalletConnectInteractor>(relaxed = true) {
        coEvery { getChains() } returns listOf(chain("1", "Ethereum"), chain("137", "Polygon"), chain("56", "BSC"))
        coEvery { checkChainsSupported(any()) } returns Result.success(true)
    }
    private val router = mockk<WalletConnectRouter>(relaxed = true)
    private val resourceManager = mockk<ResourceManager> {
        every { getString(any()) } answers { "string:${firstArg<Int>()}" }
        every { getString(any(), *anyVararg()) } answers {
            "string:${firstArg<Int>()}:" + secondArg<Array<Any>>().joinToString(",")
        }
    }
    private val accountListingMixin = mockk<AccountListingMixin> {
        every { accountsFlow(any()) } returns flowOf(
            listOf(
                LightMetaAccountUi(7, "Main wallet", true, IgnoredOnEquals(icon)),
                LightMetaAccountUi(8, "Second wallet", false, IgnoredOnEquals(icon))
            )
        )
    }
    private val accountRepository = mockk<AccountRepository> {
        coEvery { getSelectedLightMetaAccount() } returns mockk { every { id } returns 7L }
    }

    @Before
    fun setUp() {
        Dispatchers.setMain(UnconfinedTestDispatcher())
        WalletSessionLock.unlock()
        WCDelegate.sessionProposalEvent = proposal to WalletConnectVerifyContext(
            validation = WalletConnectValidation.VALID,
            verifiedOrigin = "https://swap.example",
            claimedUrl = "https://swap.example",
            isScam = false
        )
    }

    @After
    fun tearDown() {
        WCDelegate.sessionProposalEvent = null
        WalletSessionLock.lock()
        Dispatchers.resetMain()
    }

    @Test
    fun stateListsRequiredAndOptionalNetworksWithTheirPermissions() {
        val state = viewModel().awaitState { it.sessionProposal != null && it.wallets.any { wallet -> wallet.isSelected } }

        assertEquals("Ethereum", state.requiredNetworksSelectorState?.subTitle)
        assertEquals("string:${R.string.connection_required_networks}", state.requiredNetworksSelectorState?.title)
        assertEquals("Polygon", state.optionalNetworksSelectorState?.subTitle)
        assertEquals("Ethereum", state.requiredPermissions?.title)
        assertEquals(
            listOf(
                "string:${R.string.connection_methods}" to "eth_sendTransaction, personal_sign",
                "string:${R.string.connection_events}" to "chainChanged"
            ),
            state.requiredPermissions?.infoItems?.map { it.title to it.subtitle }
        )
        assertEquals("Polygon", state.optionalPermissions?.title)
        assertEquals(
            listOf("eth_signTypedData_v4", "accountsChanged"),
            state.optionalPermissions?.infoItems?.map { it.subtitle }
        )
        assertEquals(listOf(7L to true, 8L to false), state.wallets.map { it.id to it.isSelected })
        assertEquals("Second wallet", state.wallets[1].title)
        assertFalse(state.approving)
        assertFalse(state.rejecting)
    }

    @Test
    fun proposalWithoutPermissionsHasNoPermissionSections() {
        WCDelegate.sessionProposalEvent = proposal.copy(requiredNamespaces = emptyMap(), optionalNamespaces = emptyMap()) to
            WalletConnectVerifyContext.unknown("https://swap.example")

        val state = viewModel().awaitState { it.sessionProposal != null }

        assertNull(state.requiredPermissions)
        assertNull(state.optionalPermissions)
        assertNull(state.requiredNetworksSelectorState)
        assertNull(state.optionalNetworksSelectorState)
    }

    @Test
    fun selectedWalletsAndOptionalNetworksAreApproved() {
        coEvery { interactor.approveSession(any(), any(), any(), any(), any()) } answers { arg<() -> Unit>(3)() }
        every { router.openSelectMultipleChainsForResult(listOf("eip155:137"), listOf("eip155:137")) } returns
            flowOf(ChainChooseResult(emptySet()))
        val viewModel = viewModel()
        val wallets = viewModel.awaitState { it.wallets.any { wallet -> wallet.isSelected } }.wallets

        viewModel.onWalletSelected(wallets[1])
        viewModel.onWalletSelected(wallets[0])
        viewModel.awaitState { state -> state.wallets.map { it.isSelected } == listOf(false, true) }
        viewModel.onOptionalNetworksClicked()
        viewModel.onApproveClick()

        coVerify { interactor.approveSession(proposal, setOf(8L), emptySet(), any(), any()) }
        verify {
            router.openOperationSuccessAndPopUpToNearestRelatedScreen(
                null,
                null,
                "string:${R.string.connection_approve_success_message}:Swap",
                "string:${R.string.all_done}"
            )
        }
    }

    @Test
    fun approvalFailureShowsTheErrorAndClosingRejectsSilently() {
        coEvery { interactor.approveSession(any(), any(), any(), any(), any()) } answers {
            arg<(Throwable) -> Unit>(4)(IllegalStateException("relay down"))
        }
        every { interactor.silentRejectSession(any(), any(), any()) } answers { secondArg<() -> Unit>()() }
        val viewModel = viewModel()

        viewModel.onApproveClick()
        val dialog = viewModel.awaitErrorDialog { it.message == "relay down" }
        dialog.positiveClick()

        assertEquals("string:${R.string.common_error_general_title}", dialog.title)
        verify { interactor.silentRejectSession(proposal, any(), any()) }
        verify { router.back() }
        assertFalse(viewModel.state.value.approving)
    }

    @Test
    fun unsupportedMethodsAskBeforeApprovingOrRejecting() {
        WCDelegate.sessionProposalEvent = proposal.copy(
            requiredNamespaces = mapOf("eip155" to WalletConnectProposalNamespace(listOf("eip155:1"), listOf("eth_unknownMethod"), emptyList()))
        ) to WalletConnectVerifyContext.unknown("https://swap.example")
        every { interactor.rejectSession(any(), any(), any()) } answers { secondArg<() -> Unit>()() }
        val viewModel = viewModel()

        viewModel.onApproveClick()
        val dialog = viewModel.awaitErrorDialog {
            it.message == "string:${R.string.connection_methods_not_supported_warning}"
        }
        coVerify(exactly = 0) { interactor.approveSession(any(), any(), any(), any(), any()) }

        dialog.negativeClick()
        verify { interactor.rejectSession(any(), any(), any()) }
        verify {
            router.openOperationSuccessAndPopUpToNearestRelatedScreen(
                null,
                null,
                "string:${R.string.common_rejected}",
                "string:${R.string.all_done}"
            )
        }

        dialog.positiveClick()
        coVerify { interactor.approveSession(any(), setOf(7L), setOf("eip155:137"), any(), any()) }
    }

    @Test
    fun unsupportedChainsOnlyLetTheUserCloseTheProposal() {
        coEvery { interactor.checkChainsSupported(any()) } returns Result.success(false)
        every { interactor.silentRejectSession(any(), any(), any()) } answers { thirdArg<(Throwable) -> Unit>()(IllegalStateException()) }
        val viewModel = viewModel()

        val dialog = viewModel.awaitErrorDialog {
            it.message == "string:${R.string.connection_chains_not_supported_error}"
        }
        dialog.onBackClick()

        verify { interactor.silentRejectSession(proposal, any(), any()) }
        verify { router.back() }
    }

    @Test
    fun failedRejectionCanBeRetried() {
        every { interactor.rejectSession(any(), any(), any()) } answers { thirdArg<(Throwable) -> Unit>()(IllegalStateException()) }
        val viewModel = viewModel()

        viewModel.onRejectClicked()
        viewModel.onRejectClicked()

        verify(exactly = 2) { interactor.rejectSession(proposal, any(), any()) }
        verify(exactly = 0) { router.openOperationSuccessAndPopUpToNearestRelatedScreen(any(), any(), any(), any()) }
    }

    @Test
    fun requiredNetworksOpenAsAReadOnlyList() {
        val viewModel = viewModel()

        viewModel.onRequiredNetworksClicked()
        viewModel.onClose()

        verify(timeout = 5_000) { router.openSelectMultipleChains(listOf("eip155:1"), listOf("1"), true) }
        verify { router.back() }
    }

    @Test
    fun withoutAProposalEveryActionGoesBack() {
        WCDelegate.sessionProposalEvent = null
        val viewModel = viewModel()

        viewModel.onApproveClick()
        viewModel.onRejectClicked()
        viewModel.onOptionalNetworksClicked()
        viewModel.onRequiredNetworksClicked()

        verify(exactly = 5) { router.back() }
        assertEquals(SessionProposalViewState.default, viewModel.state.value)
        coVerify(exactly = 0) { interactor.checkChainsSupported(any()) }
    }

    private fun SessionProposalViewModel.awaitState(predicate: (SessionProposalViewState) -> Boolean) = runBlocking {
        withTimeout(10_000) { state.first(predicate) }
    }

    private fun chain(id: String, name: String): Chain = mockk {
        every { this@mockk.id } returns id
        every { this@mockk.name } returns name
        every { isEthereumChain } returns true
    }

    private fun viewModel() = SessionProposalViewModel(
        accountListingMixin = accountListingMixin,
        walletConnectInteractor = interactor,
        walletConnectRouter = router,
        resourceManager = resourceManager,
        accountRepository = accountRepository
    )
}
