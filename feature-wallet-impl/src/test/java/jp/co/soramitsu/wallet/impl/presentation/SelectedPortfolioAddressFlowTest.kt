package jp.co.soramitsu.wallet.impl.presentation

import jp.co.soramitsu.wallet.impl.presentation.balance.list.selectedPortfolioAddressFlow
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class SelectedPortfolioAddressFlowTest {
    @Test
    fun `same-network wallet switches recompute and clear the previous address`() = runTest {
        val chain = MutableStateFlow<String?>("ton")
        val wallet = MutableStateFlow(1L)
        val shown = mutableListOf<String>()
        val job = launch(UnconfinedTestDispatcher(testScheduler)) {
            selectedPortfolioAddressFlow(chain, wallet) { "$it-wallet-${wallet.value}" }.collect(shown::add)
        }
        runCurrent()
        wallet.value = 2L
        runCurrent()
        wallet.value = 1L
        runCurrent()
        assertEquals(listOf("", "ton-wallet-1", "", "ton-wallet-2", "", "ton-wallet-1"), shown)
        job.cancel()
    }

    @Test
    fun `late previous-wallet resolution cannot replace the current address`() = runTest {
        val chain = MutableStateFlow<String?>("ton")
        val wallet = MutableStateFlow(1L)
        val shown = mutableListOf<String>()
        val job = launch(UnconfinedTestDispatcher(testScheduler)) {
            selectedPortfolioAddressFlow(chain, wallet) {
                val requestedWallet = wallet.value
                delay(if (requestedWallet == 1L) 1000 else 10)
                "$it-wallet-$requestedWallet"
            }.collect(shown::add)
        }
        runCurrent()
        wallet.value = 2L
        runCurrent()
        advanceTimeBy(1100)
        runCurrent()
        assertEquals("ton-wallet-2", shown.last())
        assertFalse(shown.contains("ton-wallet-1"))
        job.cancel()
    }

    @Test
    fun `network changes resolve the selected network and all-networks clears the chip`() = runTest {
        val chain = MutableStateFlow<String?>("ton")
        val wallet = MutableStateFlow(1L)
        val requests = mutableListOf<String>()
        val shown = mutableListOf<String>()
        val job = launch(UnconfinedTestDispatcher(testScheduler)) {
            selectedPortfolioAddressFlow(chain, wallet) { requests.add(it); "$it-address" }.collect(shown::add)
        }
        runCurrent()
        chain.value = "ethereum"
        runCurrent()
        assertEquals("ethereum-address", shown.last())
        chain.value = null
        runCurrent()
        assertEquals("", shown.last())
        assertEquals(listOf("ton", "ethereum"), requests)
        job.cancel()
    }
}
