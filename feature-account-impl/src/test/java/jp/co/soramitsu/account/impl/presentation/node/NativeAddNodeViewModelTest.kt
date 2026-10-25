package jp.co.soramitsu.account.impl.presentation.node

import androidx.arch.core.executor.testing.InstantTaskExecutorRule
import androidx.lifecycle.Observer
import androidx.lifecycle.SavedStateHandle
import jp.co.soramitsu.common.model.UniversalWalletRegistry
import com.google.gson.JsonObject
import jp.co.soramitsu.common.data.network.solana.SolanaRpcApi
import jp.co.soramitsu.account.impl.domain.NativeNodeConnectionCheck
import io.mockk.coVerify
import io.mockk.coEvery
import jp.co.soramitsu.runtime.multiNetwork.NodeMutation
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import jp.co.soramitsu.account.api.domain.interfaces.NodesSettingsScenario
import jp.co.soramitsu.account.impl.domain.NodeHostValidator
import jp.co.soramitsu.account.impl.presentation.AccountRouter
import jp.co.soramitsu.account.impl.presentation.node.add.AddNodeFragment
import jp.co.soramitsu.account.impl.presentation.node.add.AddNodeViewModel
import jp.co.soramitsu.account.impl.presentation.node.add.LabeledButtonState
import jp.co.soramitsu.common.resources.ResourceManager
import jp.co.soramitsu.common.view.ButtonState
import jp.co.soramitsu.fearless_utils.wsrpc.SocketService
import jp.co.soramitsu.runtime.multiNetwork.chain.model.Chain
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.withContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.cancel
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import javax.inject.Provider

@OptIn(ExperimentalCoroutinesApi::class)
class NativeAddNodeViewModelTest {
    @get:Rule val instant = InstantTaskExecutorRule()
    private val dispatcher = StandardTestDispatcher()
    private val scenario = mockk<NodesSettingsScenario>()
    private val router = mockk<AccountRouter>(relaxed = true)
    private val resource = mockk<ResourceManager>(relaxed = true)
    private val socket = mockk<SocketService>(relaxed = true)
    private lateinit var viewModel: AddNodeViewModel
    private var probe: suspend () -> Unit = {}
    private var genesisHash = UniversalWalletRegistry.SOLANA_MAINNET_GENESIS_HASH
    private val endpoints = mutableListOf<String>()
    private val api = object : SolanaRpcApi {
        override suspend fun postJsonRpc(url: String, body: JsonObject): JsonObject {
            endpoints += url
            assertEquals("getGenesisHash", body["method"].asString)
            assertEquals(0, body["params"].asJsonArray.size())
            probe()
            return JsonObject().apply {
                addProperty("jsonrpc", "2.0"); addProperty("id", 1)
                addProperty("result", genesisHash)
            }
        }
    }
    private val observer = Observer<LabeledButtonState> { }

    @Before fun setup() {
        Dispatchers.setMain(dispatcher)
        every { scenario.admitMutation(any()) } answers {
            mockk<NodeMutation>(relaxed = true) { every { isLatest } returns true }
        }

        val chain = mockk<Chain>(relaxed = true)
        every { chain.id } returns "solana-mainnet"
        coEvery { scenario.getChain("solana-mainnet") } returns chain
        coEvery { scenario.addNode(any(), any(), any(), any()) } returns Result.success(Unit)
        every { socket.start(any()) } throws IllegalStateException("Substrate transport must not run")
        viewModel = AddNodeViewModel(scenario, router, NodeHostValidator(), resource,
            SavedStateHandle(mapOf(AddNodeFragment.CHAIN_ID_KEY to "solana-mainnet")), Provider { socket }, NativeNodeConnectionCheck(api))
        viewModel.addButtonState.observeForever(observer)
        viewModel.nodeNameInputLiveData.value = "Native RPC"
        viewModel.nodeHostInputLiveData.value = "https://rpc.example.invalid"
    }

    @After fun teardown() {
        viewModel.addButtonState.removeObserver(observer)
        viewModel.coroutineContext.cancel()
        Dispatchers.resetMain()
    }

    @Test fun `wrong cluster is never persisted and the add form remains retryable`() = runTest(dispatcher) {
        genesisHash = UniversalWalletRegistry.SOLANA_DEVNET_GENESIS_HASH
        runCurrent(); viewModel.addNodeClicked(); runCurrent()
        coVerify(exactly = 0) { scenario.addNode(any(), any(), any(), any()) }
        verify(exactly = 0) { router.back() }
        assertEquals(ButtonState.NORMAL, viewModel.addButtonState.value?.state)
        genesisHash = UniversalWalletRegistry.SOLANA_MAINNET_GENESIS_HASH
        viewModel.addNodeClicked(); runCurrent()
        coVerify(exactly = 1) { scenario.addNode(any(), any(), any(), any()) }
        verify(exactly = 1) { router.back() }
    }

    @Test fun `Solana add control accepts the native HTTPS endpoint policy`() = runTest(dispatcher) {
        runCurrent()
        assertEquals(ButtonState.NORMAL, viewModel.addButtonState.value?.state)
    }

    @Test fun `Solana endpoint check never starts a Substrate websocket`() = runTest(dispatcher) {
        runCurrent()
        viewModel.addNodeClicked()
        runCurrent()
        verify(exactly = 0) { socket.start(any()) }
        assertEquals(listOf("https://rpc.example.invalid"), endpoints)
        coVerify(exactly = 1) { scenario.addNode(any(), "solana-mainnet", "Native RPC", "https://rpc.example.invalid") }
        verify(exactly = 1) { router.back() }
    }

    @Test fun `pending native probe blocks duplicate writes and completes after acknowledgement`() = runTest(dispatcher) {
        val held = CompletableDeferred<Unit>()
        probe = { held.await() }
        runCurrent()
        viewModel.addNodeClicked(); viewModel.addNodeClicked(); runCurrent()
        assertEquals(ButtonState.PROGRESS, viewModel.addButtonState.value?.state)
        assertEquals(1, endpoints.size)
        coVerify(exactly = 0) { scenario.addNode(any(), any(), any(), any()) }
        held.complete(Unit); runCurrent()
        coVerify(exactly = 1) { scenario.addNode(any(), any(), any(), any()) }
        verify(exactly = 1) { router.back() }
    }

    @Test fun `failed native persistence keeps the form retryable`() = runTest(dispatcher) {
        coEvery { scenario.addNode(any(), any(), any(), any()) } returns Result.failure(IllegalStateException("write failed"))
        runCurrent(); viewModel.addNodeClicked(); runCurrent()
        assertEquals(ButtonState.NORMAL, viewModel.addButtonState.value?.state)
        assertNotNull(viewModel.errorLiveData.value)
        verify(exactly = 0) { router.back() }
        coEvery { scenario.addNode(any(), any(), any(), any()) } returns Result.success(Unit)
        viewModel.addNodeClicked(); runCurrent()
        coVerify(exactly = 2) { scenario.addNode(any(), any(), any(), any()) }
        verify(exactly = 1) { router.back() }
    }

    @Test fun `back during native probe prevents late insertion and navigation`() = runTest(dispatcher) {
        val held = CompletableDeferred<Unit>()
        probe = { withContext(NonCancellable) { held.await() } }
        runCurrent(); viewModel.addNodeClicked(); runCurrent()
        viewModel.backClicked(); held.complete(Unit); runCurrent()
        coVerify(exactly = 0) { scenario.addNode(any(), any(), any(), any()) }
        verify(exactly = 1) { router.back() }
    }

    @Test fun `back after an issued write preserves acknowledgement truth without repeated navigation`() = runTest(dispatcher) {
        val held = CompletableDeferred<Unit>()
        coEvery { scenario.addNode(any(), any(), any(), any()) } coAnswers {
            withContext(NonCancellable) { held.await() }; Result.success(Unit)
        }
        runCurrent(); viewModel.addNodeClicked(); runCurrent()
        coVerify(exactly = 1) { scenario.addNode(any(), any(), any(), any()) }
        viewModel.backClicked(); held.complete(Unit); runCurrent()
        verify(exactly = 1) { router.back() }
        coVerify(exactly = 1) { scenario.addNode(any(), any(), any(), any()) }
    }

    @Test fun `native timeout never inserts an unverified endpoint`() = runTest(dispatcher) {
        probe = { CompletableDeferred<Unit>().await() }
        runCurrent(); viewModel.addNodeClicked(); runCurrent()
        advanceTimeBy(AddNodeViewModel.NODE_CHECK_TIMEOUT_MILLIS + 1); runCurrent()
        assertEquals(ButtonState.NORMAL, viewModel.addButtonState.value?.state)
        assertNotNull(viewModel.errorLiveData.value)
        coVerify(exactly = 0) { scenario.addNode(any(), any(), any(), any()) }
        verify(exactly = 0) { router.back() }
    }
}
