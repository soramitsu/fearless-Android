package jp.co.soramitsu.walletconnect.impl.presentation.chainschooser

import androidx.arch.core.executor.testing.InstantTaskExecutorRule
import androidx.lifecycle.SavedStateHandle
import co.jp.soramitsu.walletconnect.model.ChainChooseResult
import co.jp.soramitsu.walletconnect.model.ChainChooseState
import io.mockk.coEvery
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import jp.co.soramitsu.account.api.domain.interfaces.AccountInteractor
import jp.co.soramitsu.account.api.domain.model.MetaAccount
import jp.co.soramitsu.runtime.multiNetwork.chain.model.Chain
import jp.co.soramitsu.wallet.impl.domain.ChainInteractor
import jp.co.soramitsu.wallet.impl.presentation.WalletRouter
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.flowOf
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

@OptIn(ExperimentalCoroutinesApi::class)
class ChainChooseViewModelTest {
    @get:Rule val executor = InstantTaskExecutorRule()
    private val dispatcher = StandardTestDispatcher()
    private val ethereum = chain("1", "Ethereum")
    private val polygon = chain("137", "Polygon")
    private val chains = mockk<ChainInteractor> { every { getChainsFlow() } returns flowOf(listOf(polygon, ethereum)) }
    private val account = mockk<MetaAccount> {
        every { ethereumPublicKey } returns byteArrayOf(1)
        every { chainAccounts } returns emptyMap()
    }
    private val accounts = mockk<AccountInteractor> { coEvery { selectedMetaAccount() } returns account }
    private val router = mockk<WalletRouter>(relaxed = true)
    private val models = mutableListOf<ChainChooseViewModel>()

    @Before
    fun installMain() {
        Dispatchers.setMain(dispatcher)
    }

    @After
    fun clearMain() {
        runTest(dispatcher) {
            models.forEach { it.cancel() }
            models.forEach { it.coroutineContext[Job]?.join() }
        }
        Dispatchers.resetMain()
    }

    @Test
    fun requestedChainsAreSortedAndSearchIsCaseInsensitive() = runTest(dispatcher) {
        val model = model()
        runCurrent()
        assertEquals(listOf("Ethereum", "Polygon"), model.state.value.chains?.map { it.title })
        assertEquals(listOf("eip155:137"), model.state.value.chains?.filter { it.isSelected }?.map { it.caip2id })
        model.onSearchInput("POL")
        runCurrent()
        assertEquals(listOf("Polygon"), model.state.value.chains?.map { it.title })
        assertEquals("POL", model.state.value.searchQuery)
    }

    @Test
    fun selectionTogglesAndDoneReturnsExactlySelectedIdentifiers() = runTest(dispatcher) {
        val model = model()
        runCurrent()
        val items = model.state.value.chains!!
        model.onChainSelected(null)
        model.onChainSelected(items[1])
        model.onChainSelected(items[0])
        runCurrent()
        model.onDoneClicked()
        runCurrent()
        verify { router.backWithResult(ChainChooseFragment.RESULT to ChainChooseResult(setOf("eip155:1"))) }
    }

    @Test
    fun selectAllIncludesHiddenSearchResultsAndSecondClickClearsSelection() = runTest(dispatcher) {
        val model = model()
        runCurrent()
        model.onSearchInput("POL")
        model.onSelectAllClicked()
        runCurrent()
        model.onSearchInput("")
        runCurrent()
        assertTrue(model.state.value.chains!!.all { it.isSelected })
        model.onSelectAllClicked()
        runCurrent()
        assertTrue(model.state.value.chains!!.none { it.isSelected })
    }

    @Test
    fun walletWithoutEthereumRootCanSelectOnlyExplicitChainAccounts() = runTest(dispatcher) {
        every { account.ethereumPublicKey } returns null
        every { account.chainAccounts } returns mapOf("137" to mockk { every { chain } returns polygon })
        val model = model(viewMode = true)
        runCurrent()
        assertEquals(listOf("Polygon"), model.state.value.chains?.map { it.title })
        assertTrue(model.state.value.isViewMode)
    }

    private fun model(viewMode: Boolean = false) = ChainChooseViewModel(
        router, chains,
        SavedStateHandle(mapOf(ChainChooseFragment.KEY_STATE_ID to ChainChooseState(listOf("eip155:1", "eip155:137"), listOf("eip155:137"), viewMode))),
        accounts
    ).also { models += it }

    private fun chain(id: String, name: String): Chain = mockk {
        every { this@mockk.id } returns id
        every { this@mockk.name } returns name
        every { icon } returns ""
        every { isEthereumBased } returns true
        every { isEthereumChain } returns true
    }
}
