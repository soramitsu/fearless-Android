package jp.co.soramitsu.account.impl.presentation.node.mixin.impl

import androidx.arch.core.executor.testing.InstantTaskExecutorRule
import io.mockk.every
import io.mockk.mockk
import jp.co.soramitsu.account.api.domain.interfaces.NodesSettingsScenario
import jp.co.soramitsu.account.impl.presentation.VmTestFixtures
import jp.co.soramitsu.account.impl.presentation.node.model.NodeHeaderModel
import jp.co.soramitsu.account.impl.presentation.node.model.NodeModel
import jp.co.soramitsu.core.models.ChainNode
import jp.co.soramitsu.feature_account_impl.R
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Rule
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class NodeListingProviderTest {
    @get:Rule val instantTaskRule = InstantTaskExecutorRule()

    private val scenario = mockk<NodesSettingsScenario>()
    private val defaultHeader = NodeHeaderModel("s${R.string.connection_management_default_title}")
    private val customHeader = NodeHeaderModel("s${R.string.connection_management_custom_title}")

    @Before fun setUp() = Dispatchers.setMain(UnconfinedTestDispatcher())

    @After fun tearDown() = Dispatchers.resetMain()

    private suspend fun listing(nodes: List<ChainNode>): List<Any> {
        every { scenario.nodesFlow("kusama") } returns flowOf(nodes)
        val live = NodeListingProvider(scenario, VmTestFixtures.resourceManager()).groupedNodeModelsLiveData("kusama")
        live.observeForever { }
        VmTestFixtures.awaitReal { live.value != null }
        return live.value!!
    }

    @Test
    fun `custom nodes come first under their own header, then the default ones`() = runTest {
        val items = listing(
            listOf(
                ChainNode("wss://default-1", "Default 1", isActive = true, isDefault = true),
                ChainNode("wss://mine", "Mine", isActive = false, isDefault = false),
                ChainNode("wss://default-2", "Default 2", isActive = false, isDefault = true)
            )
        )

        assertEquals(
            listOf(
                customHeader,
                NodeModel("Mine", "wss://mine", isDefault = false, isActive = false),
                defaultHeader,
                NodeModel("Default 1", "wss://default-1", isDefault = true, isActive = true),
                NodeModel("Default 2", "wss://default-2", isDefault = true, isActive = false)
            ),
            items
        )
    }

    @Test
    fun `without custom nodes only the default header is shown`() = runTest {
        val items = listing(listOf(ChainNode("wss://default-1", "Default 1", isActive = true, isDefault = true)))

        assertEquals(listOf(defaultHeader, NodeModel("Default 1", "wss://default-1", isDefault = true, isActive = true)), items)
    }
}
