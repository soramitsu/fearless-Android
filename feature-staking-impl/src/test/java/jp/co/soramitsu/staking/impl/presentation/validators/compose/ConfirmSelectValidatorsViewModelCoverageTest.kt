package jp.co.soramitsu.staking.impl.presentation.validators.compose

import androidx.arch.core.executor.testing.InstantTaskExecutorRule
import androidx.lifecycle.viewModelScope
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import io.mockk.slot
import io.mockk.verify
import java.math.BigInteger
import jp.co.soramitsu.common.AlertViewState
import jp.co.soramitsu.common.resources.ResourceManager
import jp.co.soramitsu.feature_staking_impl.R
import jp.co.soramitsu.runtime.ext.addressOf
import jp.co.soramitsu.staking.impl.StakingLaneFixtures
import jp.co.soramitsu.staking.impl.presentation.StakingRouter
import jp.co.soramitsu.staking.impl.presentation.common.SelectValidatorFlowState
import jp.co.soramitsu.staking.impl.presentation.common.StakingPoolSharedStateProvider
import jp.co.soramitsu.staking.impl.presentation.common.StakingPoolState
import jp.co.soramitsu.staking.impl.scenarios.StakingPoolInteractor
import jp.co.soramitsu.wallet.api.domain.ExistentialDepositUseCase
import jp.co.soramitsu.wallet.impl.domain.interfaces.WalletInteractor
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.job
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import kotlinx.coroutines.withTimeout
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Rule
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class ConfirmSelectValidatorsViewModelCoverageTest {

    @get:Rule
    val instant = InstantTaskExecutorRule()

    private val owned = mutableListOf<ConfirmSelectValidatorsViewModel>()

    private val chainAsset = StakingLaneFixtures.chainAsset()
    private val chain = StakingLaneFixtures.chain(chainAsset)
    private val address = chain.addressOf(StakingLaneFixtures.accountId)
    private val poolId = BigInteger.valueOf(5)
    private val first = ByteArray(32) { 1 }
    private val second = ByteArray(32) { 2 }

    private val wallet = mockk<WalletInteractor>(relaxed = true)
    private val minimum = object : ExistentialDepositUseCase {
        override suspend fun invoke(chainAsset: jp.co.soramitsu.core.models.Asset): Result<BigInteger> =
            Result.success(BigInteger.ZERO)
    }
    private val provider = StakingPoolSharedStateProvider()
    private val pool = mockk<StakingPoolInteractor>(relaxed = true)
    private val resources = mockk<ResourceManager>(relaxed = true)
    private val router = mockk<StakingRouter>(relaxed = true)

    @Before
    fun setUp() {
        Dispatchers.setMain(UnconfinedTestDispatcher())
        every { resources.getString(any()) } answers { "s${firstArg<Int>()}" }
        every { resources.getString(any(), *anyVararg()) } answers { "f${firstArg<Int>()}" }
        every { wallet.assetFlow(any(), any()) } returns emptyFlow()
        coEvery { pool.estimateNominateFee(any(), any(), *anyVararg()) } returns BigInteger.TEN.pow(9)
        coEvery { pool.nominate(any(), any(), *anyVararg()) } returns Result.success("0xhash")
        coEvery { pool.getAccountName(any()) } returns "Wallet"
        provider.mainState.set(
            StakingPoolState(
                chain = chain,
                asset = StakingLaneFixtures.asset(chainAsset),
                chainAsset = chainAsset,
                address = address
            )
        )
        selectValidators(listOf(first, second))
    }

    @After
    fun tearDown() {
        runBlocking { withTimeout(5_000) { owned.forEach { it.viewModelScope.coroutineContext.job.cancelAndJoin() } } }
        Dispatchers.resetMain()
    }

    private fun selectValidators(validators: List<ByteArray>) {
        provider.selectValidatorsState.set(SelectValidatorFlowState(selectedValidators = validators, poolName = "Pool", poolId = poolId))
    }

    private fun viewModel() = ConfirmSelectValidatorsViewModel(wallet, minimum, provider, pool, resources, router).also(owned::add)

    private fun ConfirmSelectValidatorsViewModel.awaitFee() = runBlocking {
        withTimeout(5_000) { feeViewStateFlow.first { it.value != null } }
    }

    @Test
    fun tableShowsTheAccountPoolValidatorCountAndFee() {
        val vm = viewModel()
        vm.awaitFee()

        val items = runBlocking { withTimeout(5_000) { vm.tableItemsFlow.first { it.size == 4 && it.last().value != null } } }

        assertEquals("Wallet", items[0].value)
        assertEquals(address, items[0].additionalValue)
        assertEquals("s${R.string.pool_staking_selected_pool}", items[1].title)
        assertEquals("Pool", items[1].value)
        assertEquals("s${R.string.staking_confirm_selected_validators}", items[2].title)
        assertEquals("2", items[2].value)
        coVerify { pool.estimateNominateFee(address, poolId, *anyVararg()) }
    }

    @Test
    fun confirmNominatesTheSelectedValidatorsAndReturnsToMain() {
        val vm = viewModel()
        vm.awaitFee()

        vm.onConfirm()

        verify(timeout = 5_000) { router.returnToMain() }
        coVerify { pool.nominate(poolId, address, *anyVararg()) }
        verify { router.openOperationSuccess("0xhash", chainAsset.chainId, null, null) }
    }

    @Test
    fun aRejectedNominationShowsAnAlert() {
        coEvery { pool.nominate(any(), any(), *anyVararg()) } returns Result.failure(IllegalStateException("rejected"))
        val vm = viewModel()
        vm.awaitFee()
        val alert = slot<AlertViewState>()
        every { router.openAlert(capture(alert)) } returns Unit

        vm.onConfirm()

        verify(timeout = 5_000) { router.openAlert(any<AlertViewState>()) }
        assertEquals("rejected", alert.captured.message)
        verify(exactly = 0) { router.returnToMain() }
    }

    @Test
    fun withoutSelectedValidatorsNothingIsSubmitted() {
        selectValidators(emptyList())
        val vm = viewModel()

        vm.onConfirm()

        verify(timeout = 5_000) { router.openAlert(any<AlertViewState>()) }
        assertNull(vm.feeViewStateFlow.value.value)
        coVerify(exactly = 0) { pool.nominate(any(), any(), *anyVararg()) }
    }

    @Test
    fun navigationGoesBack() {
        viewModel().onNavigationClick()

        verify { router.back() }
    }
}
