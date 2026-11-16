package jp.co.soramitsu.liquiditypools.impl.presentation.demeter

import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import jp.co.soramitsu.liquiditypools.domain.DemeterFarmingBasicPool
import jp.co.soramitsu.liquiditypools.domain.DemeterFarmingPool
import jp.co.soramitsu.liquiditypools.domain.DemeterMutationAction
import jp.co.soramitsu.liquiditypools.domain.interfaces.DemeterFarmingInteractor
import jp.co.soramitsu.liquiditypools.impl.LiquidityFixtures.pswap
import jp.co.soramitsu.liquiditypools.impl.LiquidityFixtures.valToken
import jp.co.soramitsu.liquiditypools.impl.LiquidityFixtures.walletAsset
import jp.co.soramitsu.liquiditypools.impl.LiquidityFixtures.xor
import jp.co.soramitsu.runtime.multiNetwork.chain.model.soraMainChainId
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.math.BigDecimal

@OptIn(ExperimentalCoroutinesApi::class)
class DemeterFarmingViewModelTest {

    private val dispatcher = StandardTestDispatcher()
    private val lowYield = basic(apr = 5.0)
    private val highYield = basic(apr = 40.0)
    private val position = DemeterFarmingPool(walletAsset(xor), walletAsset(valToken), walletAsset(pswap), 40.0, BigDecimal.TEN, BigDecimal.ONE)
    private val interactor = mockk<DemeterFarmingInteractor>(relaxed = true) {
        coEvery { getFarmedPools(soraMainChainId) } returns listOf(position)
        coEvery { getAvailablePools(soraMainChainId) } returns listOf(lowYield, highYield)
        coEvery { mutationCapabilityReason(soraMainChainId, any()) } returns null
    }

    @Before
    fun setUp() = Dispatchers.setMain(dispatcher)

    @After
    fun tearDown() = Dispatchers.resetMain()

    @Test
    fun `opening loads positions and farms with the best yield first`() = runTest(dispatcher) {
        val viewModel = DemeterFarmingViewModel(interactor)
        assertTrue(viewModel.state.value.loading)

        advanceUntilIdle()

        val state = viewModel.state.value
        assertFalse(state.loading)
        assertEquals(listOf(position), state.positions)
        assertEquals(listOf(highYield, lowYield), state.availablePools)
        assertNull(state.depositCapabilityReason)
        coVerify(exactly = 0) { interactor.refresh(any()) }
    }

    @Test
    fun `a manual refresh drops cached farms first and a failure disables every action`() = runTest(dispatcher) {
        val viewModel = DemeterFarmingViewModel(interactor)
        advanceUntilIdle()
        coEvery { interactor.getAvailablePools(soraMainChainId) } throws IllegalStateException("SORA node unavailable")

        viewModel.refresh()
        advanceUntilIdle()

        coVerify(exactly = 1) { interactor.refresh(soraMainChainId) }
        val state = viewModel.state.value
        assertTrue(state.positions.isEmpty())
        assertEquals("SORA node unavailable", state.depositCapabilityReason)
        assertEquals("SORA node unavailable", state.withdrawCapabilityReason)
        assertEquals("SORA node unavailable", state.claimCapabilityReason)
    }

    @Test
    fun `deposit submits the entered amount and reports the transaction`() = runTest(dispatcher) {
        coEvery { interactor.deposit(soraMainChainId, highYield, BigDecimal("2.5")) } returns Result.success("0xdep")
        val viewModel = DemeterFarmingViewModel(interactor)
        advanceUntilIdle()

        viewModel.deposit(highYield, " 2.5 ")
        advanceUntilIdle()

        assertNull(viewModel.state.value.actionKey)
        coVerify { interactor.deposit(soraMainChainId, highYield, BigDecimal("2.5")) }
        coVerify(exactly = 1) { interactor.refresh(soraMainChainId) }
        assertFalse(viewModel.state.value.loading)
    }

    @Test
    fun `withdraw and claim report failures from the network`() = runTest(dispatcher) {
        coEvery { interactor.withdraw(soraMainChainId, position, BigDecimal.ONE) } returns
            Result.failure(IllegalStateException("Position changed"))
        coEvery { interactor.claimRewards(soraMainChainId, position) } returns Result.failure(IllegalStateException())
        val viewModel = DemeterFarmingViewModel(interactor)
        advanceUntilIdle()

        viewModel.withdraw(position, "1")
        advanceUntilIdle()
        assertEquals("Position changed", viewModel.state.value.message)

        viewModel.claim(position)
        advanceUntilIdle()
        assertEquals("Transaction failed", viewModel.state.value.message)
        assertNull(viewModel.state.value.actionKey)
    }

    @Test
    fun `an action switched off at submit time is recorded with its reason`() = runTest(dispatcher) {
        val viewModel = DemeterFarmingViewModel(interactor)
        advanceUntilIdle()
        coEvery { interactor.mutationCapabilityReason(soraMainChainId, DemeterMutationAction.Claim) } returns "Claims paused"

        viewModel.claim(position)
        advanceUntilIdle()

        assertEquals("Claims paused", viewModel.state.value.claimCapabilityReason)
        assertEquals("Claims paused", viewModel.state.value.message)
        coVerify(exactly = 0) { interactor.claimRewards(any(), any()) }

        coEvery { interactor.mutationCapabilityReason(soraMainChainId, DemeterMutationAction.Deposit) } returns "Deposits paused"
        coEvery { interactor.mutationCapabilityReason(soraMainChainId, DemeterMutationAction.Withdraw) } returns "Withdrawals paused"
        viewModel.deposit(highYield, "1")
        viewModel.withdraw(position, "1")
        advanceUntilIdle()

        assertEquals("Deposits paused", viewModel.state.value.depositCapabilityReason)
        assertEquals("Withdrawals paused", viewModel.state.value.withdrawCapabilityReason)
        coVerify(exactly = 0) { interactor.deposit(any(), any(), any()) }
    }

    @Test
    fun `an action already known to be unavailable does nothing`() = runTest(dispatcher) {
        coEvery { interactor.mutationCapabilityReason(soraMainChainId, DemeterMutationAction.Deposit) } returns "Deposits paused"
        coEvery { interactor.mutationCapabilityReason(soraMainChainId, DemeterMutationAction.Withdraw) } returns "Withdrawals paused"
        val viewModel = DemeterFarmingViewModel(interactor)
        advanceUntilIdle()

        viewModel.deposit(highYield, "1")
        viewModel.withdraw(position, "1")
        advanceUntilIdle()

        assertEquals("Deposits paused", viewModel.state.value.depositCapabilityReason)
        assertEquals("Withdrawals paused", viewModel.state.value.withdrawCapabilityReason)
        coVerify(exactly = 0) { interactor.deposit(any(), any(), any()) }
        coVerify(exactly = 0) { interactor.withdraw(any(), any(), any()) }
    }

    private fun basic(apr: Double) =
        DemeterFarmingBasicPool(walletAsset(xor), walletAsset(valToken), walletAsset(pswap), apr, BigDecimal.TEN, 0.5)
}
