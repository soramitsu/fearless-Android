package jp.co.soramitsu.staking.impl.presentation.pools.edit

import androidx.arch.core.executor.testing.InstantTaskExecutorRule
import androidx.lifecycle.viewModelScope
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import java.math.BigInteger
import jp.co.soramitsu.common.AlertViewState
import jp.co.soramitsu.common.compose.component.TitleValueViewState
import jp.co.soramitsu.common.resources.ResourceManager
import jp.co.soramitsu.fearless_utils.ss58.SS58Encoder.toAddress
import jp.co.soramitsu.feature_staking_impl.R
import jp.co.soramitsu.runtime.ext.addressOf
import jp.co.soramitsu.staking.impl.StakingLaneFixtures
import jp.co.soramitsu.staking.impl.presentation.StakingRouter
import jp.co.soramitsu.staking.impl.presentation.common.EditPoolFlowState
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
import org.junit.Before
import org.junit.Rule
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class EditPoolConfirmViewModelCoverageTest {

    @get:Rule
    val instant = InstantTaskExecutorRule()

    private val owned = mutableListOf<EditPoolConfirmViewModel>()

    private val chainAsset = StakingLaneFixtures.chainAsset()
    private val chain = StakingLaneFixtures.chain(chainAsset)
    private val address = chain.addressOf(StakingLaneFixtures.accountId)
    private val prefix = chain.addressPrefix.toShort()
    private val root = ByteArray(32) { 1 }
    private val nominator = ByteArray(32) { 2 }
    private val toggler = ByteArray(32) { 3 }
    private val rootAddress = root.toAddress(prefix)

    private val edited = EditPoolFlowState(
        poolId = BigInteger.valueOf(5),
        initialPoolName = "Old",
        depositor = ByteArray(32) { 9 },
        initialRoot = root,
        initialNominator = nominator,
        initialStateToggler = null,
        newPoolName = "New",
        newStateToggler = toggler
    )

    private val wallet = mockk<WalletInteractor>(relaxed = true)
    private val minimum = object : ExistentialDepositUseCase {
        override suspend fun invoke(chainAsset: jp.co.soramitsu.core.models.Asset): Result<BigInteger> =
            Result.success(BigInteger.ZERO)
    }
    private val resources = mockk<ResourceManager>(relaxed = true)
    private val provider = StakingPoolSharedStateProvider()
    private val pool = mockk<StakingPoolInteractor>(relaxed = true)
    private val router = mockk<StakingRouter>(relaxed = true)

    @Before
    fun setUp() {
        Dispatchers.setMain(UnconfinedTestDispatcher())
        every { resources.getString(any()) } answers { "s${firstArg<Int>()}" }
        every { resources.getString(any(), *anyVararg()) } answers { "f${firstArg<Int>()}" }
        every { wallet.assetFlow(any(), any()) } returns emptyFlow()
        coEvery { pool.estimateEditFee(any(), any()) } returns BigInteger.TEN.pow(9)
        coEvery { pool.edit(any(), any()) } returns Result.success("0xedit")
        coEvery { pool.getAccountName(any()) } answers { if (firstArg<String>() == rootAddress) "Root wallet" else null }
        provider.mainState.set(
            StakingPoolState(
                chain = chain,
                asset = StakingLaneFixtures.asset(chainAsset),
                chainAsset = chainAsset,
                address = address
            )
        )
        provider.editPoolState.set(edited)
    }

    @After
    fun tearDown() {
        runBlocking { withTimeout(5_000) { owned.forEach { it.viewModelScope.coroutineContext.job.cancelAndJoin() } } }
        Dispatchers.resetMain()
    }

    private fun viewModel() = EditPoolConfirmViewModel(wallet, minimum, resources, provider, pool, router).also(owned::add)

    private fun EditPoolConfirmViewModel.awaitTable(size: Int): List<TitleValueViewState> = runBlocking {
        withTimeout(5_000) {
            feeViewStateFlow.first { it.value != null }
            tableItemsFlow.first { it.size == size && it.last().value != null }
        }
    }

    @Test
    fun tableShowsTheNewNameAndTheResultingRoles() {
        val items = viewModel().awaitTable(5)

        assertEquals(TitleValueViewState("s${R.string.pool_staking_pool_name}", "New"), items[0])
        assertEquals(TitleValueViewState("s${R.string.pool_staking_root}", "Root wallet", rootAddress), items[1])
        assertEquals(TitleValueViewState("s${R.string.pool_staking_nominator}", nominator.toAddress(prefix), null), items[2])
        assertEquals(TitleValueViewState("s${R.string.pool_staking_bouncer}", toggler.toAddress(prefix), null), items[3])
        coVerify { pool.estimateEditFee(edited, address) }
    }

    @Test
    fun anUnchangedNameIsLeftOutAndAMissingRoleIsEmpty() {
        provider.editPoolState.set(edited.copy(newPoolName = null, newStateToggler = null))

        val items = viewModel().awaitTable(4)

        assertEquals("s${R.string.pool_staking_root}", items[0].title)
        assertEquals(TitleValueViewState("s${R.string.pool_staking_bouncer}", "", null), items[2])
    }

    @Test
    fun confirmEditsThePoolAndReturnsToManagement() {
        val vm = viewModel()
        vm.awaitTable(5)

        vm.onConfirm()

        verify(timeout = 5_000) { router.returnToManagePoolStake() }
        coVerify { pool.edit(edited, address) }
        verify { router.openOperationSuccess("0xedit", chainAsset.chainId, null, null) }
    }

    @Test
    fun aRejectedEditShowsAnAlert() {
        coEvery { pool.edit(any(), any()) } returns Result.failure(IllegalStateException("rejected"))
        val vm = viewModel()
        vm.awaitTable(5)

        vm.onConfirm()

        verify(timeout = 5_000) { router.openAlert(match<AlertViewState> { it.message == "rejected" }) }
        verify(exactly = 0) { router.returnToManagePoolStake() }
    }

    @Test
    fun backGoesBack() {
        viewModel().onBackClick()

        verify { router.back() }
    }
}
