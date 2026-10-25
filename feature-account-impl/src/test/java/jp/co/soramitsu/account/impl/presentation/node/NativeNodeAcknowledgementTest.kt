package jp.co.soramitsu.account.impl.presentation.node

import androidx.arch.core.executor.testing.InstantTaskExecutorRule
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.SavedStateHandle
import io.mockk.coEvery
import io.mockk.coVerify
import jp.co.soramitsu.runtime.multiNetwork.NodeMutation
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import jp.co.soramitsu.account.api.domain.interfaces.NodesSettingsScenario
import jp.co.soramitsu.account.impl.presentation.AccountRouter
import jp.co.soramitsu.account.impl.presentation.node.list.NodesFragment
import jp.co.soramitsu.account.impl.presentation.node.list.NodesViewModel
import jp.co.soramitsu.account.impl.presentation.node.mixin.api.NodeListingMixin
import jp.co.soramitsu.account.impl.presentation.node.model.NodeModel
import jp.co.soramitsu.common.resources.ResourceManager
import jp.co.soramitsu.runtime.storage.NodesSettingsStorage
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
class NativeNodeAcknowledgementTest {
    @get:Rule val instant = InstantTaskExecutorRule()
    private val dispatcher = StandardTestDispatcher()
    private val scenario = mockk<NodesSettingsScenario>()
    private val router = mockk<AccountRouter>(relaxed = true)
    private val row = NodeModel("Native RPC", "https://rpc.example.invalid", false, false)
    private lateinit var viewModel: NodesViewModel

    @Before fun setup() {
        Dispatchers.setMain(dispatcher)
        every { scenario.admitMutation(any()) } answers {
            mockk<NodeMutation>(relaxed = true) { every { isLatest } returns true }
        }

        val chain = mockk<Chain>(relaxed = true)
        every { chain.id } returns "solana-mainnet"
        coEvery { scenario.getChain("solana-mainnet") } returns chain
        val mixin = mockk<NodeListingMixin>()
        every { mixin.groupedNodeModelsLiveData(any()) } returns MutableLiveData(emptyList())
        val settings = mockk<NodesSettingsStorage>(relaxed = true)
        viewModel = NodesViewModel(router, mixin, mockk<ResourceManager>(relaxed = true), scenario, settings,
            SavedStateHandle(mapOf(NodesFragment.CHAIN_ID_KEY to "solana-mainnet")))
    }
    @After fun teardown() {
        viewModel.coroutineContext.cancel()
        Dispatchers.resetMain()
    }

    @Test fun `node action is admitted synchronously before its coroutine starts`() = runTest(dispatcher) {
        coEvery { scenario.selectNode(any(), any()) } returns Unit
        viewModel.selectNodeClicked(row)
        verify(exactly = 1) { scenario.admitMutation("solana-mainnet") }
        coVerify(exactly = 0) { scenario.selectNode(any(), any()) }
        runCurrent()
        coVerify(exactly = 1) { scenario.selectNode(any(), any()) }
    }

    @Test fun `newer command suppresses old acknowledgement navigation`() = runTest(dispatcher) {
        var latest = true
        val admitted = mockk<NodeMutation>(relaxed = true)
        every { admitted.isLatest } answers { latest }
        every { scenario.admitMutation(any()) } returns admitted
        val held = CompletableDeferred<Unit>()
        coEvery { scenario.selectNode(any(), any()) } coAnswers { held.await() }
        viewModel.selectNodeClicked(row)
        runCurrent()
        latest = false
        held.complete(Unit)
        runCurrent()
        verify(exactly = 0) { router.openMain() }
        verify(exactly = 1) { admitted.cancel() }
    }

    @Test fun `back revokes order before a held cancellation handler resumes`() = runTest(dispatcher) {
        var cancelled = false
        val admitted = mockk<NodeMutation>(relaxed = true)
        every { admitted.isLatest } returns true
        every { admitted.cancel() } answers { cancelled = true }
        every { scenario.admitMutation(any()) } returns admitted
        val held = CompletableDeferred<Unit>()
        coEvery { scenario.selectNode(any(), any()) } coAnswers {
            try { held.await() } finally { org.junit.Assert.assertTrue(cancelled) }
        }
        viewModel.selectNodeClicked(row)
        runCurrent()
        viewModel.backClicked()
        runCurrent()
        verify(exactly = 0) { router.openMain() }
    }

    @Test fun `delete is tracked and cannot race another click in the same screen`() = runTest(dispatcher) {
        val held = CompletableDeferred<Unit>()
        coEvery { scenario.deleteNode(any(), any()) } coAnswers { held.await() }
        viewModel.confirmNodeDeletion(row)
        viewModel.selectNodeClicked(row)
        runCurrent()
        verify(exactly = 1) { scenario.admitMutation("solana-mainnet") }
        coVerify(exactly = 1) { scenario.deleteNode(any(), any()) }
        coVerify(exactly = 0) { scenario.selectNode(any(), any()) }
        held.complete(Unit)
        runCurrent()
    }

    @Test fun `native node list enables manual selection without an unsupported websocket auto switch`() = runTest(dispatcher) {
        runCurrent()
        org.junit.Assert.assertEquals(false, viewModel.supportsAutoSelection.value)
        org.junit.Assert.assertEquals(false, viewModel.autoSelectedNodeFlow.value)
    }

    @Test fun `selection failure remains visible and retry navigates only after success`() = runTest(dispatcher) {
        coEvery { scenario.selectNode(any(), any()) } throws IllegalStateException("persistence failed")
        viewModel.selectNodeClicked(row)
        runCurrent()
        verify(exactly = 0) { router.openMain() }
        assertNotNull(viewModel.errorLiveData.value)
        coEvery { scenario.selectNode(any(), any()) } returns Unit
        viewModel.selectNodeClicked(row)
        runCurrent()
        verify(exactly = 1) { router.openMain() }
    }

    @Test fun `back suppresses navigation from an already issued late node acknowledgement`() = runTest(dispatcher) {
        val held = CompletableDeferred<Unit>()
        coEvery { scenario.selectNode(any(), any()) } coAnswers { withContext(NonCancellable) { held.await() } }
        viewModel.selectNodeClicked(row)
        runCurrent()
        viewModel.backClicked()
        held.complete(Unit)
        runCurrent()
        verify(exactly = 1) { router.back() }
        verify(exactly = 0) { router.openMain() }
    }

    @Test fun `repeated selection while persistence is pending issues one mutation`() = runTest(dispatcher) {
        val held = CompletableDeferred<Unit>()
        coEvery { scenario.selectNode(any(), any()) } coAnswers { held.await() }
        viewModel.selectNodeClicked(row)
        viewModel.selectNodeClicked(row)
        runCurrent()
        coVerify(exactly = 1) { scenario.selectNode(any(), any()) }
        verify(exactly = 0) { router.openMain() }
        held.complete(Unit)
        runCurrent()
        verify(exactly = 1) { router.openMain() }
    }
}
