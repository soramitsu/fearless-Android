package jp.co.soramitsu.tonconnect.impl.presentation.tonconnectiondetails

import androidx.arch.core.executor.testing.InstantTaskExecutorRule
import androidx.lifecycle.SavedStateHandle
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkObject
import io.mockk.unmockkObject
import io.mockk.verify
import jp.co.soramitsu.account.api.domain.interfaces.AccountInteractor
import jp.co.soramitsu.account.api.domain.interfaces.AccountRepository
import jp.co.soramitsu.common.resources.ResourceManager
import jp.co.soramitsu.feature_tonconnect_impl.R
import jp.co.soramitsu.tonconnect.api.domain.TonConnectInteractor
import jp.co.soramitsu.tonconnect.api.domain.TonConnectRouter
import jp.co.soramitsu.tonconnect.api.model.AppEntity
import jp.co.soramitsu.tonconnect.api.model.JsonBuilder
import jp.co.soramitsu.tonconnect.impl.presentation.featureToggleStore
import jp.co.soramitsu.tonconnect.impl.presentation.pauseTonSigning
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.json.JSONObject
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Rule
import org.junit.Test

/** A connection that asks for `ton_proof` needs a signature, so the TON dApp signing switch covers it. */
@OptIn(ExperimentalCoroutinesApi::class)
class TonConnectionProofPauseTest {
    @get:Rule val instantTask = InstantTaskExecutorRule()
    private val dispatcher = StandardTestDispatcher()
    private val accountInteractor = mockk<AccountInteractor> {
        every { lightMetaAccountsFlow() } returns flowOf(emptyList())
    }
    private val interactor = mockk<TonConnectInteractor>()
    private val router = mockk<TonConnectRouter>(relaxed = true)
    private val resources = mockk<ResourceManager> {
        every { getString(any()) } answers { "text-${firstArg<Int>()}" }
    }
    private val repository = mockk<AccountRepository>()
    private val featureToggles = featureToggleStore()
    private val app = AppEntity("https://app.example", "Dapp", "https://app.example/icon.png", null, null)
    private val viewModels = mutableListOf<TonConnectionDetailsViewModel>()

    @Before
    fun setup() {
        Dispatchers.setMain(dispatcher)
        coEvery { interactor.getChain() } returns mockk(relaxed = true)
        coEvery { interactor.requestProof(any(), any(), any()) } returns mockk()
        coEvery { repository.getSelectedLightMetaAccount() } returns mockk { every { id } returns 7L }
        coEvery { repository.getMetaAccount(7L) } returns mockk(relaxed = true) {
            every { tonPublicKey } returns ByteArray(32) { 1 }
        }
        // The plain JVM has no org.json; the reply's content is not what these tests check.
        mockkObject(JsonBuilder)
        every { JsonBuilder.connectEventSuccess(any(), any(), any()) } returns mockk<JSONObject> {
            every { this@mockk.toString() } returns "connected"
        }
    }

    @After
    fun tearDown() {
        viewModels.forEach { it.coroutineContext.cancel() }
        unmockkObject(JsonBuilder)
        Dispatchers.resetMain()
    }

    @Test
    fun proofIsSignedWhenTonSigningIsNotPaused() = runTest(dispatcher) {
        val vm = create(proofPayload = "payload")
        runCurrent()
        vm.onApproveClick()
        runCurrent()
        assertNull(vm.errorDialogStateLiveData.value)
        coVerify(exactly = 1) { interactor.requestProof(7L, app, "payload") }
        verify { router.backWithResult(TonConnectionDetailsFragment.TON_CONNECT_RESULT_KEY to "connected") }
    }

    @Test
    fun proofRequestWhileTonSigningIsPausedSaysWhyAndSignsNoProof() = runTest(dispatcher) {
        featureToggles.pauseTonSigning()
        val vm = create(proofPayload = "payload")
        runCurrent()
        assertEquals("text-${R.string.signing_refused_paused}", vm.errorDialogStateLiveData.value?.peekContent()?.message)
        vm.onApproveClick()
        runCurrent()
        coVerify(exactly = 0) { interactor.requestProof(any(), any(), any()) }
        verify(exactly = 0) { router.backWithResult(any()) }
    }

    @Test
    fun pauseThatArrivesBeforeApprovalSignsNoProof() = runTest(dispatcher) {
        val vm = create(proofPayload = "payload")
        runCurrent()
        assertNull(vm.errorDialogStateLiveData.value)
        featureToggles.pauseTonSigning()
        vm.onApproveClick()
        runCurrent()
        coVerify(exactly = 0) { interactor.requestProof(any(), any(), any()) }
        assertEquals("text-${R.string.signing_refused_paused}", vm.errorDialogStateLiveData.value?.peekContent()?.message)
    }

    @Test
    fun connectionWithoutProofSignsNothingSoIsNotPaused() = runTest(dispatcher) {
        featureToggles.pauseTonSigning()
        val vm = create(proofPayload = null)
        runCurrent()
        assertNull(vm.errorDialogStateLiveData.value)
        vm.onApproveClick()
        runCurrent()
        verify { router.backWithResult(TonConnectionDetailsFragment.TON_CONNECT_RESULT_KEY to "connected") }
    }

    private fun create(proofPayload: String?) = TonConnectionDetailsViewModel(
        accountInteractor = accountInteractor,
        tonConnectInteractor = interactor,
        tonConnectRouter = router,
        resourceManager = resources,
        accountRepository = repository,
        iconGenerator = mockk(relaxed = true),
        featureToggles = featureToggles,
        savedStateHandle = SavedStateHandle(
            mapOf(
                TonConnectionDetailsFragment.TON_CONNECTION_APP_KEY to app,
                TonConnectionDetailsFragment.TON_PROOF_PAYLOAD_KEY to proofPayload
            )
        )
    ).also(viewModels::add)
}
