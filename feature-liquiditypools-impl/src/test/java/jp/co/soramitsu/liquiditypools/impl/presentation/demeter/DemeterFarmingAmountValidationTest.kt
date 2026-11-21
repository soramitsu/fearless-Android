package jp.co.soramitsu.liquiditypools.impl.presentation.demeter

import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import jp.co.soramitsu.liquiditypools.domain.DemeterFarmingBasicPool
import jp.co.soramitsu.liquiditypools.domain.DemeterFarmingPool
import jp.co.soramitsu.liquiditypools.domain.interfaces.DemeterFarmingInteractor
import jp.co.soramitsu.liquiditypools.impl.PoolsPrecisionFixtures.valToken
import jp.co.soramitsu.liquiditypools.impl.PoolsPrecisionFixtures.walletAsset
import jp.co.soramitsu.liquiditypools.impl.PoolsPrecisionFixtures.xor
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
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test
import java.math.BigDecimal

/**
 * Bug 29: Deposit and Withdraw parsed the amount inside the launched coroutine and threw
 * IllegalArgumentException outside any Result handling, so an empty, zero or "." amount crashed the app.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class DemeterFarmingAmountValidationTest {

    private val dispatcher = StandardTestDispatcher()
    private val farm = DemeterFarmingBasicPool(
        tokenBase = walletAsset(xor, BigDecimal.ONE),
        tokenTarget = walletAsset(valToken, BigDecimal.ONE),
        tokenReward = walletAsset(valToken, BigDecimal.ONE),
        apr = 12.0,
        tvl = BigDecimal.TEN,
        fee = 0.4
    )
    private val position = DemeterFarmingPool(
        tokenBase = farm.tokenBase,
        tokenTarget = farm.tokenTarget,
        tokenReward = farm.tokenReward,
        apr = 12.0,
        amount = BigDecimal.TEN,
        amountReward = BigDecimal.ONE
    )
    private val interactor = mockk<DemeterFarmingInteractor>(relaxed = true) {
        coEvery { getFarmedPools(soraMainChainId) } returns listOf(position)
        coEvery { getAvailablePools(soraMainChainId) } returns listOf(farm)
        coEvery { mutationCapabilityReason(soraMainChainId, any()) } returns null
        coEvery { deposit(soraMainChainId, farm, any()) } returns Result.success("0xdeposit")
        coEvery { withdraw(soraMainChainId, position, any()) } returns Result.success("0xwithdraw")
    }

    @Before
    fun setUp() = Dispatchers.setMain(dispatcher)

    @After
    fun tearDown() = Dispatchers.resetMain()

    @Test
    fun shouldShowAnErrorAndSubmitNothing_whenTheDepositAmountIsNotPositive() = runTest(dispatcher) {
        val viewModel = DemeterFarmingViewModel(interactor)
        advanceUntilIdle()

        for (amount in INVALID_AMOUNTS) {
            viewModel.deposit(farm, amount)
            advanceUntilIdle()

            assertEquals("deposit of '$amount'", INVALID_AMOUNT_MESSAGE, viewModel.state.value.message)
            assertNull("deposit of '$amount' left an action running", viewModel.state.value.actionKey)
        }
        coVerify(exactly = 0) { interactor.deposit(any(), any(), any()) }
    }

    @Test
    fun shouldShowAnErrorAndSubmitNothing_whenTheWithdrawAmountIsNotPositive() = runTest(dispatcher) {
        val viewModel = DemeterFarmingViewModel(interactor)
        advanceUntilIdle()

        for (amount in INVALID_AMOUNTS) {
            viewModel.withdraw(position, amount)
            advanceUntilIdle()

            assertEquals("withdraw of '$amount'", INVALID_AMOUNT_MESSAGE, viewModel.state.value.message)
            assertNull("withdraw of '$amount' left an action running", viewModel.state.value.actionKey)
        }
        coVerify(exactly = 0) { interactor.withdraw(any(), any(), any()) }
    }

    @Test
    fun shouldSubmitTheExactAmount_whenTheAmountIsPositive() = runTest(dispatcher) {
        val viewModel = DemeterFarmingViewModel(interactor)
        advanceUntilIdle()

        viewModel.deposit(farm, " 2.5 ")
        advanceUntilIdle()
        viewModel.withdraw(position, "0.000000000000000001")
        advanceUntilIdle()

        coVerify(exactly = 1) { interactor.deposit(soraMainChainId, farm, BigDecimal("2.5")) }
        coVerify(exactly = 1) { interactor.withdraw(soraMainChainId, position, BigDecimal("0.000000000000000001")) }
        assertNull(viewModel.state.value.actionKey)
    }

    private companion object {
        const val INVALID_AMOUNT_MESSAGE = "Enter a positive amount"
        val INVALID_AMOUNTS = listOf("", "0", ".", "0.000", " ")
    }
}
