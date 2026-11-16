package jp.co.soramitsu.staking.impl.presentation.pools

import androidx.arch.core.executor.testing.InstantTaskExecutorRule
import androidx.lifecycle.SavedStateHandle
import io.mockk.coEvery
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import java.math.BigInteger
import jp.co.soramitsu.common.resources.ClipboardManager
import jp.co.soramitsu.common.resources.ResourceManager
import jp.co.soramitsu.fearless_utils.extensions.toHexString
import jp.co.soramitsu.feature_staking_impl.R
import jp.co.soramitsu.runtime.ext.addressOf
import jp.co.soramitsu.staking.api.domain.model.Identity
import jp.co.soramitsu.staking.api.domain.model.NominationPoolState
import jp.co.soramitsu.staking.api.domain.model.PoolInfo
import jp.co.soramitsu.staking.api.domain.model.RootIdentity
import jp.co.soramitsu.staking.impl.StakingLaneFixtures
import jp.co.soramitsu.staking.impl.presentation.StakingRouter
import jp.co.soramitsu.staking.impl.presentation.common.StakingPoolSharedStateProvider
import jp.co.soramitsu.staking.impl.presentation.common.StakingPoolState
import jp.co.soramitsu.staking.impl.presentation.pools.compose.PoolInfoScreenViewState
import jp.co.soramitsu.staking.impl.presentation.pools.compose.PoolStatusViewState
import jp.co.soramitsu.staking.impl.scenarios.StakingPoolInteractor
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
class PoolInfoViewModelCoverageTest {

    @get:Rule
    val instant = InstantTaskExecutorRule()

    private val chainAsset = StakingLaneFixtures.chainAsset()
    private val chain = StakingLaneFixtures.chain(chainAsset)
    private val asset = StakingLaneFixtures.asset(chainAsset)
    private val userId = StakingLaneFixtures.accountId
    private val depositorId = ByteArray(32) { 1 }
    private val nominatorId = ByteArray(32) { 2 }
    private val togglerId = ByteArray(32) { 4 }
    private val validatorId = ByteArray(32) { 6 }

    private val provider = StakingPoolSharedStateProvider()
    private val resources = mockk<ResourceManager>(relaxed = true)
    private val router = mockk<StakingRouter>(relaxed = true)
    private val poolInteractor = mockk<StakingPoolInteractor>(relaxed = true)
    private val clipboard = mockk<ClipboardManager>(relaxed = true)

    @Before
    fun setUp() {
        Dispatchers.setMain(UnconfinedTestDispatcher())
        every { resources.getString(any()) } answers { "s${firstArg<Int>()}" }
        provider.mainState.set(StakingPoolState(chain = chain, asset = asset, chainAsset = chainAsset, address = chain.addressOf(userId)))
        val identities: Map<String, Identity?> = mapOf(
            depositorId.toHexString() to RootIdentity("Depositor Name", null, null, null, null, null, null, null),
            nominatorId.toHexString() to null
        )
        coEvery { poolInteractor.getIdentities(any()) } returns identities
        coEvery { poolInteractor.getValidatorsIds(any(), any()) } returns listOf(validatorId)
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    private fun pool(
        state: NominationPoolState = NominationPoolState.Open,
        root: ByteArray? = userId,
        toggler: ByteArray? = null
    ) = PoolInfo(
        poolId = BigInteger.valueOf(5),
        name = "Sun Pool",
        stakedInPlanks = StakingLaneFixtures.planks(12),
        state = state,
        members = BigInteger.valueOf(12),
        depositor = depositorId,
        root = root,
        nominator = nominatorId,
        stateToggler = toggler
    )

    private fun viewModel(poolInfo: PoolInfo): PoolInfoViewModel {
        provider.poolsCache.value = mapOf(5 to poolInfo)
        return PoolInfoViewModel(
            provider,
            SavedStateHandle(mapOf(PoolInfoFragment.POOL_INFO_KEY to 5)),
            resources,
            router,
            poolInteractor,
            clipboard
        )
    }

    @Test
    fun rootSeesNamedRolesValidatorsAndOptions() {
        val vm = viewModel(pool())
        val state = vm.state.value

        assertEquals("5", state.poolId.value)
        assertEquals("Sun Pool", state.name.value)
        assertEquals("12", state.members.value)
        assertTrue(state.staked.value!!, state.staked.value!!.contains("12") && state.staked.value!!.contains("DOT"))
        assertNull(state.staked.additionalValue)
        assertEquals("Depositor Name", state.depositor.text)
        assertEquals(chain.addressOf(userId), state.root?.text)
        assertEquals(chain.addressOf(nominatorId), state.nominator?.text)
        assertNull(state.stateToggler)
        assertEquals("1", state.validators.value)
        assertEquals(NominationPoolState.Open.name, state.state.value)
        assertEquals(PoolStatusViewState.Active, state.poolStatus)
        assertTrue(state.showOptions)
    }

    @Test
    fun rootPublishesSelectedValidatorsForThePool() {
        viewModel(pool())

        val selected = provider.selectedValidatorsState.get()!!
        assertEquals(true, selected.canChangeValidators)
        assertEquals(BigInteger.valueOf(5), selected.poolId)
        assertEquals("Sun Pool", selected.poolName)
        assertEquals(1, selected.selectedValidators.size)
        assertTrue(selected.selectedValidators.first().contentEquals(validatorId))
    }

    @Test
    fun openPoolWithoutValidatorsShowsMissingValidatorsForOtherUsers() {
        coEvery { poolInteractor.getValidatorsIds(any(), any()) } returns emptyList()
        val vm = viewModel(pool(root = depositorId, toggler = togglerId))
        val state = vm.state.value

        assertEquals("0", state.validators.value)
        assertEquals(NominationPoolState.HasNoValidators.name, state.state.value)
        assertEquals(PoolStatusViewState.ValidatorsAreNotSelected, state.poolStatus)
        assertFalse(state.showOptions)
        assertEquals(chain.addressOf(togglerId), state.stateToggler?.text)
        assertEquals(false, provider.selectedValidatorsState.get()?.canChangeValidators)
    }

    @Test
    fun blockedAndDestroyingPoolsAreInactive() {
        assertEquals(PoolStatusViewState.Inactive, viewModel(pool(state = NominationPoolState.Blocked)).state.value.poolStatus)
        assertEquals(PoolStatusViewState.Inactive, viewModel(pool(state = NominationPoolState.Destroying)).state.value.poolStatus)
    }

    @Test
    fun navigationCallbacksReachRouter() {
        val poolInfo = pool()
        val vm = viewModel(poolInfo)

        vm.onCloseClick()
        vm.onTableItemClick(PoolInfoScreenViewState.VALIDATORS_CLICK_STATE_IDENTIFIER)
        vm.onTableItemClick(PoolInfoScreenViewState.VALIDATORS_CLICK_STATE_IDENTIFIER + 1)
        vm.onOptionsClick()

        verify { router.back() }
        verify(exactly = 1) { router.openSelectedValidators() }
        verify { router.openPoolInfoOptions(poolInfo) }
    }

    @Test
    fun roleClicksCopyAddressesAndConfirm() {
        val vm = viewModel(pool(toggler = togglerId))

        vm.onDepositorClick()
        vm.onRootClick()
        vm.onNominatorClick()
        vm.onStateTogglerClick()

        verify { clipboard.addToClipboard(chain.addressOf(depositorId), any()) }
        verify { clipboard.addToClipboard(chain.addressOf(userId), any()) }
        verify { clipboard.addToClipboard(chain.addressOf(nominatorId), any()) }
        verify { clipboard.addToClipboard(chain.addressOf(togglerId), any()) }
        assertEquals("s${R.string.common_copied}", vm.messageLiveData.value?.peekContent())
    }

    @Test
    fun missingOptionalRolesAreNotCopied() {
        val vm = viewModel(pool(root = null))

        vm.onRootClick()
        vm.onStateTogglerClick()

        verify(exactly = 0) { clipboard.addToClipboard(any(), any()) }
        assertNull(vm.state.value.root)
        assertFalse(vm.state.value.showOptions)
    }
}
