package jp.co.soramitsu.walletconnect.impl.presentation.requestpreview

import androidx.arch.core.executor.testing.InstantTaskExecutorRule
import androidx.lifecycle.SavedStateHandle
import co.jp.soramitsu.feature_walletconnect_impl.R
import co.jp.soramitsu.walletconnect.domain.WalletConnectInteractor
import co.jp.soramitsu.walletconnect.domain.WalletConnectRouter
import co.jp.soramitsu.walletconnect.model.WalletConnectValidation
import co.jp.soramitsu.walletconnect.model.WalletConnectVerifyContext
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import jp.co.soramitsu.account.api.domain.interfaces.AccountRepository
import jp.co.soramitsu.account.api.domain.model.MetaAccount
import jp.co.soramitsu.common.address.AddressIconGenerator
import jp.co.soramitsu.common.compose.component.SigningSummaryViewState
import jp.co.soramitsu.common.data.network.config.FeatureToggleConfig
import jp.co.soramitsu.common.data.network.config.ProductFeatureToggleStore
import jp.co.soramitsu.common.data.storage.pin.WalletSessionLock
import jp.co.soramitsu.common.resources.ResourceManager
import jp.co.soramitsu.fearless_utils.extensions.fromHex
import jp.co.soramitsu.walletconnect.impl.presentation.DappSigningPausedException
import jp.co.soramitsu.walletconnect.impl.presentation.TEST_TOPIC
import jp.co.soramitsu.walletconnect.impl.presentation.awaitErrorDialog
import jp.co.soramitsu.walletconnect.impl.presentation.caip2id
import jp.co.soramitsu.walletconnect.impl.presentation.ethereumChain
import jp.co.soramitsu.walletconnect.impl.presentation.featureToggleStore
import jp.co.soramitsu.walletconnect.impl.presentation.sessionRequest
import jp.co.soramitsu.walletconnect.impl.presentation.summary.WalletConnectRequestDecoder
import jp.co.soramitsu.walletconnect.impl.presentation.summary.WalletConnectRequestSummaries
import jp.co.soramitsu.walletconnect.impl.presentation.summary.WalletConnectSummaryPresenter
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import kotlinx.coroutines.withTimeout
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class RequestPreviewViewModelTest {

    @get:Rule
    val instantTasks = InstantTaskExecutorRule()

    private val evmAddress = "0x8ba1f109551bd432803012645ac136ddd64dba72"
    private val shown = personalSign(id = 5, messageHex = "0x68656c6c6f")
    private val newer = personalSign(id = 9, messageHex = "0x6f74686572")

    private val interactor = mockk<WalletConnectInteractor>(relaxed = true)
    private val router = mockk<WalletConnectRouter>(relaxed = true)
    private val resourceManager = mockk<ResourceManager>()
    private val accountRepository = mockk<AccountRepository>()

    @Before
    fun setUp() {
        Dispatchers.setMain(UnconfinedTestDispatcher())
        every { resourceManager.getString(any()) } answers { "string:${firstArg<Int>()}" }
        every { interactor.getPendingListOfSessionRequests(TEST_TOPIC) } returns listOf(shown, newer)
        every { interactor.getVerifyContext(any(), any()) } returns null
        coEvery { interactor.getChains() } returns listOf(ethereumChain)
        val wallet = mockk<MetaAccount>(relaxed = true) {
            every { id } returns 1L
            every { name } returns "Wallet"
            every { chainAccounts } returns emptyMap()
            every { substrateAccountId } returns null
            every { ethereumAddress } returns evmAddress.fromHex()
        }
        coEvery { accountRepository.allMetaAccounts() } returns listOf(wallet)
        WalletSessionLock.unlock()
    }

    @After
    fun tearDown() {
        WalletSessionLock.lock()
        Dispatchers.resetMain()
    }

    @Test
    fun `a locked wallet signs nothing`() {
        val viewModel = viewModel(requestId = shown.request.id)
        WalletSessionLock.lock()

        viewModel.onSignClick()

        coVerify(exactly = 0) { interactor.handleSignAction(any(), any(), any(), any(), any(), any()) }
    }

    @Test
    fun `preview shows and signs the request it was opened for, not the newest one`() {
        val viewModel = viewModel(requestId = shown.request.id)

        viewModel.onTableItemClick(RAW_DATA_ITEM)
        viewModel.onSignClick()

        verify { router.openRawData("hello") }
        coVerify { interactor.handleSignAction(ethereumChain, TEST_TOPIC, shown, any(), any(), any()) }
        coVerify(exactly = 0) { interactor.handleSignAction(any(), any(), newer, any(), any(), any()) }
    }

    @Test
    fun `a signed message opens the success sheet saying so`() {
        coEvery { interactor.handleSignAction(any(), any(), any(), any(), any(), any()) } coAnswers {
            arg<(String?, String?) -> Unit>(4).invoke(null, null)
        }
        val viewModel = viewModel(requestId = shown.request.id)

        viewModel.onSignClick()

        verify(timeout = 10_000) {
            router.openOperationSuccessAndPopUpToNearestRelatedScreen(
                operationHash = null,
                chainId = null,
                customMessage = "string:${R.string.walletconnect_message_signed_text}",
                customTitle = "string:${R.string.walletconnect_message_signed_title}"
            )
        }
    }

    @Test
    fun `a broadcast transaction keeps the sheet's own transaction sent text`() {
        val send = sessionRequest(
            id = 5,
            method = "eth_sendTransaction",
            params = """[{"from":"$evmAddress","to":"$evmAddress","data":"0x"}]""",
            chainId = ethereumChain.caip2id
        )
        every { interactor.getPendingListOfSessionRequests(TEST_TOPIC) } returns listOf(send)
        coEvery { interactor.handleSignAction(any(), any(), any(), any(), any(), any()) } coAnswers {
            arg<(String?, String?) -> Unit>(4).invoke("0xhash", ethereumChain.id)
        }
        val summaries = mockk<WalletConnectRequestSummaries>()
        coEvery { summaries.summary(any(), any()) } returns SigningSummaryViewState("send", emptyList(), emptyList(), false)
        val viewModel = viewModel(requestId = send.request.id, summaries = summaries)

        viewModel.onSignClick()

        verify(timeout = 10_000) {
            router.openOperationSuccessAndPopUpToNearestRelatedScreen(
                operationHash = "0xhash",
                chainId = ethereumChain.id,
                customMessage = null,
                customTitle = null
            )
        }
    }

    @Test
    fun `sign is refused once the request is no longer pending`() {
        val viewModel = viewModel(requestId = shown.request.id)
        every { interactor.getPendingListOfSessionRequests(TEST_TOPIC) } returns listOf(newer)

        viewModel.onSignClick()
        val dialog = viewModel.awaitErrorDialog { it.message == "string:${R.string.common_undefined_error_message}" }
        dialog.positiveClick()

        coVerify(exactly = 0) { interactor.handleSignAction(any(), any(), any(), any(), any(), any()) }
        verify { router.back() }
    }

    @Test
    fun `sign is refused when the request changed after it was shown`() {
        val viewModel = viewModel(requestId = shown.request.id)
        val changed = personalSign(id = shown.request.id, messageHex = "0x6f74686572")
        every { interactor.getPendingListOfSessionRequests(TEST_TOPIC) } returns listOf(changed, newer)

        viewModel.onSignClick()
        viewModel.awaitErrorDialog { it.message == "string:${R.string.common_undefined_error_message}" }

        coVerify(exactly = 0) { interactor.handleSignAction(any(), any(), any(), any(), any(), any()) }
    }

    @Test
    fun `sign waits for the decoded summary to reach the approval screen`() {
        val pending = CompletableDeferred<SigningSummaryViewState>()
        val summaries = mockk<WalletConnectRequestSummaries>()
        coEvery { summaries.summary(any(), any()) } coAnswers { pending.await() }
        val model = viewModel(shown.request.id, summaries = summaries, awaitSummary = false)
        model.onSignClick()
        coVerify(exactly = 0) { interactor.handleSignAction(any(), any(), any(), any(), any(), any()) }

        pending.complete(SigningSummaryViewState("hello", emptyList(), emptyList(), false))
        runBlocking { withTimeout(10_000) { model.state.first { it.summary != null } } }
        model.onSignClick()
        coVerify { interactor.handleSignAction(ethereumChain, TEST_TOPIC, shown, any(), any(), any()) }
    }

    @Test
    fun `a paused request says why and is never signed`() {
        val toggles = featureToggleStore().apply { rememberRemoteVetoes(FeatureToggleConfig(evmDappSigningEnabled = false)) }
        val model = viewModel(requestId = shown.request.id, featureToggles = toggles)

        model.onSignClick()

        assertEquals("string:${R.string.signing_refused_paused}", model.state.value.summary?.headline)
        coVerify(exactly = 0) { interactor.handleSignAction(any(), any(), any(), any(), any(), any()) }
    }

    @Test
    fun `a pause that arrives while the request is shown closes it with the reason`() {
        coEvery { interactor.handleSignAction(any(), any(), any(), any(), any(), any()) } coAnswers {
            arg<(Exception) -> Unit>(3).invoke(DappSigningPausedException())
        }
        val model = viewModel(requestId = shown.request.id)

        model.onSignClick()
        val dialog = model.awaitErrorDialog { it.message == "string:${R.string.signing_refused_paused}" }
        dialog.positiveClick()

        verify { router.back() }
    }

    @Test
    fun `a request from a site Verify lists as a scam is never signed`() {
        every { resourceManager.getString(any(), *anyVararg()) } answers { "string:${firstArg<Int>()}" }
        every { interactor.getVerifyContext(TEST_TOPIC, shown.request.id) } returns WalletConnectVerifyContext(
            WalletConnectValidation.INVALID,
            "https://drainer.example",
            "https://app.example.org",
            isScam = true
        )
        val model = viewModel(requestId = shown.request.id)

        model.onSignClick()
        val dialog = model.awaitErrorDialog { it.message == "string:${R.string.walletconnect_scam_blocked}" }
        dialog.positiveClick()

        assertTrue(model.state.value.isBlocked)
        verify { router.back() }
        coVerify(exactly = 0) { interactor.handleSignAction(any(), any(), any(), any(), any(), any()) }
    }

    @Test
    fun `the host row shows the verified origin and the warning names a mismatch`() {
        every { resourceManager.getString(any(), *anyVararg()) } answers { "string:${firstArg<Int>()}" }
        every { interactor.getVerifyContext(TEST_TOPIC, shown.request.id) } returns WalletConnectVerifyContext(
            WalletConnectValidation.INVALID,
            "https://app.examp1e.org",
            "https://app.example.org",
            isScam = false
        )

        val state = viewModel(requestId = shown.request.id).state.value

        assertTrue(state.tableItems.any { it.value == "app.examp1e.org" })
        assertEquals("string:${R.string.walletconnect_origin_mismatch}", state.originWarning)
    }

    @Test
    fun `preview opened without a request id closes instead of picking one`() {
        val viewModel = viewModel(requestId = null)

        viewModel.onSignClick()

        verify { router.back() }
        coVerify(exactly = 0) { interactor.handleSignAction(any(), any(), any(), any(), any(), any()) }
    }

    private fun personalSign(id: Long, messageHex: String) = sessionRequest(
        id = id,
        method = "personal_sign",
        params = """["$messageHex", "$evmAddress"]""",
        chainId = ethereumChain.caip2id
    )

    private fun viewModel(
        requestId: Long?,
        featureToggles: ProductFeatureToggleStore = featureToggleStore(),
        summaries: WalletConnectRequestSummaries = WalletConnectRequestSummaries(
            WalletConnectRequestDecoder { null }, WalletConnectSummaryPresenter(resourceManager), featureToggles
        ),
        awaitSummary: Boolean = true
    ) = RequestPreviewViewModel(
        savedStateHandle = SavedStateHandle(
            mapOf(
                RequestPreviewFragment.PAYLOAD_TOPIC_KEY to TEST_TOPIC,
                RequestPreviewFragment.PAYLOAD_REQUEST_ID_KEY to requestId
            )
        ),
        walletConnectInteractor = interactor,
        walletConnectRouter = router,
        resourceManager = resourceManager,
        accountRepository = accountRepository,
        addressIconGenerator = mockk<AddressIconGenerator>(relaxed = true),
        requestSummaries = summaries
    ).also { model ->
        if (requestId != null && awaitSummary) runBlocking { withTimeout(10_000) { model.state.first { it.summary != null } } }
    }

    private companion object {
        const val RAW_DATA_ITEM = 1
    }
}
