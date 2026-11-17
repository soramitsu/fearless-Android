package jp.co.soramitsu.success.presentation

import androidx.arch.core.executor.testing.InstantTaskExecutorRule
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.viewModelScope
import io.mockk.coEvery
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import jp.co.soramitsu.common.R
import jp.co.soramitsu.common.compose.component.TitleValueViewState
import jp.co.soramitsu.common.compose.theme.greenText
import jp.co.soramitsu.common.resources.ClipboardManager
import jp.co.soramitsu.common.resources.ResourceManager
import jp.co.soramitsu.core.models.Ecosystem
import jp.co.soramitsu.runtime.multiNetwork.ChainRegistry
import jp.co.soramitsu.runtime.multiNetwork.chain.model.Chain
import jp.co.soramitsu.success.api.presentation.SuccessRouter
import jp.co.soramitsu.wallet.impl.data.network.blockchain.updaters.BalanceUpdateTrigger
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.async
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class SuccessViewModelTest {
    @get:Rule val instant = InstantTaskExecutorRule()

    private val dispatcher = StandardTestDispatcher()
    private val router = mockk<SuccessRouter>(relaxed = true)
    private val chainRegistry = mockk<ChainRegistry>()
    private val resources = mockk<ResourceManager>()
    private val clipboard = mockk<ClipboardManager>(relaxed = true)
    private val models = mutableListOf<SuccessViewModel>()

    @Before fun setUp() {
        Dispatchers.setMain(dispatcher)
        every { resources.getString(any()) } answers { "s:${firstArg<Int>()}" }
    }

    @After fun tearDown() {
        models.forEach { it.viewModelScope.cancel() }
        Dispatchers.resetMain()
    }

    private fun chain(
        explorers: List<Chain.Explorer>,
        ecosystem: Ecosystem = Ecosystem.Substrate,
        ethereum: Boolean = false
    ): Chain = mockk {
        every { this@mockk.explorers } returns explorers
        every { this@mockk.ecosystem } returns ecosystem
        every { isEthereumChain } returns ethereum
    }

    private fun TestScope.model(
        chain: Chain,
        hash: String? = HASH,
        title: String? = null,
        message: String? = null
    ): SuccessViewModel {
        coEvery { chainRegistry.getChain(CHAIN_ID) } returns chain
        return create(CHAIN_ID, hash, title, message)
    }

    private fun TestScope.create(chainId: String?, hash: String?, title: String? = null, message: String? = null): SuccessViewModel {
        val handle = SavedStateHandle(
            mapOf(
                SuccessFragment.KEY_OPERATION_HASH to hash,
                SuccessFragment.KEY_CHAIN_ID to chainId,
                SuccessFragment.KEY_CUSTOM_TITLE to title,
                SuccessFragment.KEY_CUSTOM_MESSAGE to message
            )
        )
        return SuccessViewModel(router, chainRegistry, handle, resources, clipboard)
            .also { models += it; runCurrent() }
    }

    @Test fun firstExplorerThatSupportsExtrinsicsIsOfferedWithTheHashTable() = runTest(dispatcher) {
        val subscanWithoutExtrinsics = Chain.Explorer(Chain.Explorer.Type.SUBSCAN, listOf("account"), "https://scan.test/{type}/{value}")
        val subscan = Chain.Explorer(Chain.Explorer.Type.SUBSCAN, listOf("extrinsic"), "https://polkadot.scan.test/{type}/{value}")
        val vm = model(chain(listOf(subscanWithoutExtrinsics, subscan)))

        val state = vm.state.value
        assertEquals("s:${R.string.common_transaction_sent}", state.title)
        assertEquals("s:${R.string.success_message_transaction_sent}", state.message)
        assertEquals(Chain.Explorer.Type.SUBSCAN to "https://polkadot.scan.test/extrinsic/$HASH", state.explorer)
        val (hashItem, resultItem) = state.tableItems
        assertEquals("s:${R.string.hash}", hashItem.title)
        assertEquals("0x123...cdef0", hashItem.value)
        val click = hashItem.clickState as TitleValueViewState.ClickState.Value
        assertEquals(SuccessViewState.CODE_HASH_CLICK, click.identifier)
        assertEquals("s:${R.string.all_done_alert_result_stub}", resultItem.title)
        assertEquals("s:${R.string.all_done_alert_success_stub}", resultItem.value)
        assertEquals(greenText, resultItem.valueColor)
    }

    @Test fun evmExplorersLinkToTheTransactionPage() = runTest(dispatcher) {
        val evmTypes = listOf(
            Chain.Explorer.Type.ETHERSCAN, Chain.Explorer.Type.OKLINK,
            Chain.Explorer.Type.KLAYTN, Chain.Explorer.Type.ZETA
        )
        evmTypes.forEach { type ->
            val explorer = Chain.Explorer(type, listOf("tx", "address"), "https://evm.test/{type}/{value}")
            val vm = model(chain(listOf(explorer), Ecosystem.Ethereum))
            assertEquals(type to "https://evm.test/tx/$HASH", vm.state.value.explorer)
        }
        val reef = Chain.Explorer(Chain.Explorer.Type.REEF, listOf("extrinsic"), "https://reef.test/{type}/{value}")
        assertEquals(Chain.Explorer.Type.REEF to "https://reef.test/extrinsic/$HASH", model(chain(listOf(reef))).state.value.explorer)
    }

    @Test fun tonTransfersLinkToTonviewerAndOfferNoHashCopy() = runTest(dispatcher) {
        val tonviewer = Chain.Explorer(Chain.Explorer.Type.TONVIEWER, listOf("tonTransaction"), "https://tonviewer.test/{type}/{value}")
        val vm = model(chain(listOf(tonviewer), Ecosystem.Ton))

        assertEquals(Chain.Explorer.Type.TONVIEWER to "https://tonviewer.test/transaction/$HASH", vm.state.value.explorer)
        assertNull(vm.state.value.tableItems.first().clickState)
    }

    @Test fun unknownExplorersAndMissingHashLeaveNoExplorerLink() = runTest(dispatcher) {
        val unknown = Chain.Explorer(Chain.Explorer.Type.UNKNOWN, listOf("extrinsic"), "https://unknown.test/{type}/{value}")
        assertNull(model(chain(listOf(unknown))).state.value.explorer)

        val withoutHash = model(chain(listOf(unknown)), hash = null)
        assertNull(withoutHash.state.value.explorer)
        assertTrue(withoutHash.state.value.tableItems.isEmpty())
    }

    @Test fun customTitleAndMessageReplaceTheDefaults() = runTest(dispatcher) {
        val vm = model(chain(emptyList()), title = "Swap sent", message = "Polkaswap accepted it")
        assertEquals("Swap sent", vm.state.value.title)
        assertEquals("Polkaswap accepted it", vm.state.value.message)
    }

    @Test fun withoutAChainTheHashIsStillCopyableButNoExplorerIsResolved() = runTest(dispatcher) {
        val vm = create(chainId = null, hash = HASH)
        assertEquals(Chain.Explorer.Type.UNKNOWN to "", vm.state.value.explorer)
        assertTrue(vm.state.value.tableItems.first().clickState is TitleValueViewState.ClickState.Value)

        vm.onClose(); runCurrent()
        verify(exactly = 1) { router.back() }
    }

    @Test fun hashClickCopiesTheHashAndOtherCodesDoNothing() = runTest(dispatcher) {
        val vm = model(chain(emptyList()))
        vm.onItemClick(SuccessViewState.CODE_HASH_CLICK + 1)
        verify(exactly = 0) { clipboard.addToClipboard(any(), any()) }

        vm.onItemClick(SuccessViewState.CODE_HASH_CLICK)
        verify(exactly = 1) { clipboard.addToClipboard(HASH, any()) }
        assertEquals("s:${R.string.common_copied}", vm.messageLiveData.value?.peekContent())
    }

    @Test fun explorerAndShareActionsCarryTheResolvedUrl() = runTest(dispatcher) {
        val subscan = Chain.Explorer(Chain.Explorer.Type.SUBSCAN, listOf("extrinsic"), "https://scan.test/{type}/{value}")
        val etherscan = Chain.Explorer(Chain.Explorer.Type.ETHERSCAN, listOf("extrinsic", "tx"), "https://eth.test/{type}/{value}")
        val vm = model(chain(listOf(subscan, etherscan)))

        vm.onExplorerClick(); runCurrent()
        assertEquals("https://scan.test/extrinsic/$HASH", vm.openBrowserEvent.value?.peekContent())
        vm.onShareClick(); runCurrent()
        assertEquals("https://scan.test/extrinsic/$HASH", vm.shareUrlEvent.value?.peekContent())
    }

    @Test fun closingAnEthereumTransferForcesABalanceRefreshBeforeLeaving() = runTest(dispatcher) {
        val vm = model(chain(emptyList(), Ecosystem.Ethereum, ethereum = true))
        val refreshed = async(start = CoroutineStart.UNDISPATCHED) { BalanceUpdateTrigger.observe().first() }

        vm.onClose(); runCurrent()

        assertEquals(CHAIN_ID, refreshed.await())
        verify(exactly = 1) { router.back() }
    }

    @Test fun closingASubstrateTransferOnlyLeaves() = runTest(dispatcher) {
        val vm = model(chain(emptyList()))
        val refreshed = async(start = CoroutineStart.UNDISPATCHED) { BalanceUpdateTrigger.observe().first() }

        vm.onClose(); runCurrent()

        verify(exactly = 1) { router.back() }
        assertTrue(refreshed.isActive)
        refreshed.cancel()
    }

    private companion object {
        const val CHAIN_ID = "chain-id"
        const val HASH = "0x123456789abcdef0"
    }
}
