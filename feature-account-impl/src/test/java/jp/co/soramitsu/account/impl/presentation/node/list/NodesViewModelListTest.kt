package jp.co.soramitsu.account.impl.presentation.node.list

import androidx.arch.core.executor.testing.InstantTaskExecutorRule
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.SavedStateHandle
import io.mockk.coEvery
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import jp.co.soramitsu.account.api.domain.interfaces.NodesSettingsScenario
import jp.co.soramitsu.account.impl.presentation.AccountRouter
import jp.co.soramitsu.account.impl.presentation.VmTestFixtures
import jp.co.soramitsu.account.impl.presentation.node.details.NodeDetailsPayload
import jp.co.soramitsu.account.impl.presentation.node.mixin.api.NodeListingMixin
import jp.co.soramitsu.account.impl.presentation.node.model.NodeHeaderModel
import jp.co.soramitsu.account.impl.presentation.node.model.NodeModel
import jp.co.soramitsu.feature_account_impl.R
import jp.co.soramitsu.runtime.storage.NodesSettingsStorage
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.cancel
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test

/** The node list around selection: editing, chain name, custom-node detection, navigation and failures. */
@OptIn(ExperimentalCoroutinesApi::class)
class NodesViewModelListTest {
    @get:Rule val instant = InstantTaskExecutorRule()

    private val dispatcher = StandardTestDispatcher()
    private val scenario = mockk<NodesSettingsScenario>()
    private val router = mockk<AccountRouter>(relaxed = true)
    private val settings = mockk<NodesSettingsStorage>(relaxed = true)
    private val nodes = MutableLiveData<List<Any>>()
    private val defaultNode = NodeModel("Default", "wss://default", isDefault = true, isActive = true)
    private val customNode = NodeModel("Mine", "wss://mine", isDefault = false, isActive = false)
    private lateinit var viewModel: NodesViewModel

    @Before fun setUp() {
        Dispatchers.setMain(dispatcher)
        coEvery { scenario.getChain("kusama") } returns VmTestFixtures.chain(id = "kusama", name = "Kusama")
        every { settings.getIsAutoSelectNodes("kusama") } returns true
        val mixin = mockk<NodeListingMixin> { every { groupedNodeModelsLiveData("kusama") } returns nodes }
        viewModel = NodesViewModel(
            router, mixin, VmTestFixtures.resourceManager(), scenario, settings,
            SavedStateHandle(mapOf(NodesFragment.CHAIN_ID_KEY to "kusama"))
        )
    }

    @After fun tearDown() {
        viewModel.coroutineContext.cancel()
        Dispatchers.resetMain()
    }

    @Test
    fun `the chain name loads and custom nodes are detected`() = runTest(dispatcher) {
        viewModel.chainName.observeForever { }
        viewModel.hasCustomNodeModelsLiveData.observeForever { }
        runCurrent()

        assertEquals("Kusama", viewModel.chainName.value)
        nodes.value = listOf(NodeHeaderModel("Default"), defaultNode)
        assertEquals(false, viewModel.hasCustomNodeModelsLiveData.value)
        nodes.value = listOf(NodeHeaderModel("Custom"), customNode, NodeHeaderModel("Default"), defaultNode)
        assertEquals(true, viewModel.hasCustomNodeModelsLiveData.value)
        assertEquals(true, viewModel.supportsAutoSelection.value)
    }

    @Test
    fun `edit toggles and the toolbar action follows`() {
        viewModel.toolbarAction.observeForever { }
        viewModel.editClicked()
        assertEquals(true, viewModel.editMode.value)
        assertEquals("s${R.string.common_done}", viewModel.toolbarAction.value)
        viewModel.editClicked()
        assertEquals(false, viewModel.editMode.value)
        assertEquals("s${R.string.common_edit}", viewModel.toolbarAction.value)
    }

    @Test
    fun `turning auto selection off is stored for the chain`() = runTest(dispatcher) {
        runCurrent()
        assertEquals(true, viewModel.autoSelectedNodeFlow.value)
        viewModel.autoSelectedNodeFlow.value = false
        runCurrent()
        verify { settings.setIsAutoSelectNodes("kusama", false) }
    }

    @Test
    fun `info, add and delete route to their screens or confirmation`() {
        viewModel.infoClicked(customNode)
        viewModel.addNodeClicked()
        viewModel.deleteNodeClicked(customNode)

        verify { router.openNodeDetails(NodeDetailsPayload("kusama", "wss://mine")) }
        verify { router.openAddNode("kusama") }
        assertEquals(customNode, viewModel.deleteNodeEvent.value!!.getContentIfNotHandled())
    }

    @Test
    fun `a refused mutation shows the general error and starts nothing`() = runTest(dispatcher) {
        every { scenario.admitMutation("kusama") } throws IllegalStateException("busy")
        viewModel.selectNodeClicked(defaultNode)
        assertEquals("s${R.string.common_error_general_message}", viewModel.errorLiveData.value!!.getContentIfNotHandled())
        viewModel.confirmNodeDeletion(customNode)
        assertEquals("s${R.string.common_error_general_message}", viewModel.errorLiveData.value!!.getContentIfNotHandled())
        assertEquals(false, viewModel.selectionPending.value)
    }

    @Test
    fun `after back nothing else navigates`() {
        viewModel.backClicked()
        viewModel.backClicked()
        viewModel.infoClicked(customNode)
        viewModel.addNodeClicked()
        viewModel.selectNodeClicked(customNode)

        verify(exactly = 1) { router.back() }
        verify(exactly = 0) { router.openAddNode(any()) }
        verify(exactly = 0) { router.openNodeDetails(any()) }
        assertTrue(viewModel.selectionPending.value == false)
    }

    @Test
    fun `an unreadable chain shows the general error`() = runTest(dispatcher) {
        coEvery { scenario.getChain("kusama") } throws IllegalStateException("no chain")
        val failing = NodesViewModel(
            router, mockk<NodeListingMixin> { every { groupedNodeModelsLiveData("kusama") } returns nodes },
            VmTestFixtures.resourceManager(), scenario, settings, SavedStateHandle(mapOf(NodesFragment.CHAIN_ID_KEY to "kusama"))
        )
        runCurrent()
        assertEquals("s${R.string.common_error_general_message}", failing.errorLiveData.value!!.getContentIfNotHandled())
        failing.coroutineContext.cancel()
    }
}
