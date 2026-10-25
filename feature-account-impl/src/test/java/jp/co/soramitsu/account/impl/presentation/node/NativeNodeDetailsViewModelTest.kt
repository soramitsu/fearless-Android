package jp.co.soramitsu.account.impl.presentation.node

import androidx.arch.core.executor.testing.InstantTaskExecutorRule
import androidx.lifecycle.Observer
import androidx.lifecycle.SavedStateHandle
import jp.co.soramitsu.common.model.UniversalWalletRegistry
import com.google.gson.JsonObject
import io.mockk.coEvery
import io.mockk.coVerify
import jp.co.soramitsu.runtime.multiNetwork.NodeMutation
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import jp.co.soramitsu.account.api.domain.interfaces.NodesSettingsScenario
import jp.co.soramitsu.account.impl.domain.NativeNodeConnectionCheck
import jp.co.soramitsu.account.impl.presentation.AccountRouter
import jp.co.soramitsu.account.impl.presentation.node.details.NodeDetailsFragment
import jp.co.soramitsu.account.impl.presentation.node.details.NodeDetailsPayload
import jp.co.soramitsu.account.impl.presentation.node.details.NodeDetailsViewModel
import jp.co.soramitsu.common.data.network.solana.SolanaRpcApi
import jp.co.soramitsu.core.models.ChainNode
import jp.co.soramitsu.runtime.multiNetwork.chain.model.Chain
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.cancel
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import kotlinx.coroutines.withContext
import org.junit.After
import org.junit.Assert.assertNotNull
import org.junit.Before
import org.junit.Rule
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class NativeNodeDetailsViewModelTest {
    @get:Rule val instant = InstantTaskExecutorRule()
    private val dispatcher = StandardTestDispatcher()
    private val scenario = mockk<NodesSettingsScenario>()
    private val router = mockk<AccountRouter>(relaxed = true)
    private val original = "https://rpc.example.invalid"
    private lateinit var viewModel: NodeDetailsViewModel
    private var probe: suspend () -> Unit = {}
    private var genesisHash = UniversalWalletRegistry.SOLANA_MAINNET_GENESIS_HASH
    private val endpoints = mutableListOf<String>()
    private val api = object : SolanaRpcApi {
        override suspend fun postJsonRpc(url: String, body: JsonObject): JsonObject {
            endpoints += url
            org.junit.Assert.assertEquals("getGenesisHash", body["method"].asString)
            probe()
            return JsonObject().apply {
                addProperty("jsonrpc", "2.0"); addProperty("id", 1)
                addProperty("result", genesisHash)
            }
        }
    }
    private val nodeObserver = Observer<ChainNode> { }
    private val chainObserver = Observer<Chain> { }

    @Before fun setup() {
        Dispatchers.setMain(dispatcher)
        every { scenario.admitMutation(any()) } answers {
            mockk<NodeMutation>(relaxed = true) { every { isLatest } returns true }
        }

        val chain = mockk<Chain>(relaxed = true)
        every { chain.id } returns "solana-mainnet"
        coEvery { scenario.getChain("solana-mainnet") } returns chain
        coEvery { scenario.getNode(any()) } returns ChainNode(original, "RPC", false, false)
        coEvery { scenario.updateNode(any(), any(), any(), any()) } returns Result.success(Unit)
        viewModel = NodeDetailsViewModel(scenario, router, mockk(relaxed = true), mockk(relaxed = true),
            SavedStateHandle(mapOf(NodeDetailsFragment.PAYLOAD_KEY to NodeDetailsPayload("solana-mainnet", original))), NativeNodeConnectionCheck(api))
        viewModel.nodeModelLiveData.observeForever(nodeObserver)
        viewModel.chainInfoLiveData.observeForever(chainObserver)
    }
    @After fun teardown() {
        viewModel.nodeModelLiveData.removeObserver(nodeObserver)
        viewModel.chainInfoLiveData.removeObserver(chainObserver)
        viewModel.coroutineContext.cancel()
        Dispatchers.resetMain()
    }

    @Test fun `wrong cluster is never persisted by the editor`() = runTest(dispatcher) {
        genesisHash = UniversalWalletRegistry.SOLANA_DEVNET_GENESIS_HASH
        runCurrent(); viewModel.updateClicked("New RPC", "https://second.example.invalid"); runCurrent()
        coVerify(exactly = 0) { scenario.updateNode(any(), any(), any(), any()) }
        verify(exactly = 0) { router.back() }
        org.junit.Assert.assertEquals(true, viewModel.updateButtonEnabled.value)
        genesisHash = UniversalWalletRegistry.SOLANA_MAINNET_GENESIS_HASH
        viewModel.updateClicked("New RPC", "https://second.example.invalid"); runCurrent()
        coVerify(exactly = 1) { scenario.updateNode(any(), any(), any(), any()) }
    }

    @Test fun `native editor cannot bypass HTTPS policy`() = runTest(dispatcher) {
        runCurrent(); viewModel.updateClicked("RPC", "http://remote.example.invalid"); runCurrent()
        coVerify(exactly = 0) { scenario.updateNode(any(), any(), any(), any()) }
        verify(exactly = 0) { router.back() }
        assertNotNull(viewModel.errorLiveData.value)
    }

    @Test fun `editor write exception stays visible for retry`() = runTest(dispatcher) {
        coEvery { scenario.updateNode(any(), any(), any(), any()) } throws IllegalStateException("write failed")
        runCurrent(); viewModel.updateClicked("Renamed", original); runCurrent()
        verify(exactly = 0) { router.back() }
        assertNotNull(viewModel.errorLiveData.value)
        coEvery { scenario.updateNode(any(), any(), any(), any()) } returns Result.success(Unit)
        viewModel.updateClicked("Renamed", original); runCurrent()
        verify(exactly = 1) { router.back() }
    }

    @Test fun `editor back fences an already issued late write acknowledgement`() = runTest(dispatcher) {
        val held = CompletableDeferred<Unit>()
        coEvery { scenario.updateNode(any(), any(), any(), any()) } coAnswers {
            withContext(NonCancellable) { held.await() }; Result.success(Unit)
        }
        runCurrent(); viewModel.updateClicked("Renamed", original); runCurrent()
        viewModel.backClicked(); held.complete(Unit); runCurrent()
        verify(exactly = 1) { router.back() }
    }

    @Test fun `editor repeated taps issue one pending write`() = runTest(dispatcher) {
        val held = CompletableDeferred<Unit>()
        coEvery { scenario.updateNode(any(), any(), any(), any()) } coAnswers { held.await(); Result.success(Unit) }
        runCurrent(); viewModel.updateClicked("Renamed", original); viewModel.updateClicked("Renamed", original); runCurrent()
        coVerify(exactly = 1) { scenario.updateNode(any(), any(), any(), any()) }
        held.complete(Unit); runCurrent()
        verify(exactly = 1) { router.back() }
    }

    @Test fun `changed native endpoint is probed before its acknowledged save`() = runTest(dispatcher) {
        val held = CompletableDeferred<Unit>()
        probe = { held.await() }
        runCurrent(); viewModel.updateClicked("Updated", "https://new.example.invalid"); runCurrent()
        org.junit.Assert.assertEquals(listOf("https://new.example.invalid"), endpoints)
        org.junit.Assert.assertEquals(false, viewModel.updateButtonEnabled.value)
        coVerify(exactly = 0) { scenario.updateNode(any(), any(), any(), any()) }
        held.complete(Unit); runCurrent()
        coVerify(exactly = 1) { scenario.updateNode(any(), any(), "Updated", "https://new.example.invalid") }
        verify(exactly = 1) { router.back() }
    }

    @Test fun `endpoint that becomes active during probe is not overwritten by stale editor`() = runTest(dispatcher) {
        val held = CompletableDeferred<Unit>()
        probe = { held.await() }
        runCurrent(); viewModel.updateClicked("Updated", "https://new.example.invalid"); runCurrent()
        coEvery { scenario.getNode(any()) } returns ChainNode(original, "RPC", true, false)
        held.complete(Unit); runCurrent()
        coVerify(exactly = 0) { scenario.updateNode(any(), any(), any(), any()) }
        verify(exactly = 0) { router.back() }
        assertNotNull(viewModel.errorLiveData.value)
    }

    @Test fun `back during editor probe prevents a late mutation`() = runTest(dispatcher) {
        val held = CompletableDeferred<Unit>()
        probe = { withContext(NonCancellable) { held.await() } }
        runCurrent(); viewModel.updateClicked("Updated", "https://new.example.invalid"); runCurrent()
        viewModel.backClicked(); held.complete(Unit); runCurrent()
        coVerify(exactly = 0) { scenario.updateNode(any(), any(), any(), any()) }
        verify(exactly = 1) { router.back() }
    }
}
