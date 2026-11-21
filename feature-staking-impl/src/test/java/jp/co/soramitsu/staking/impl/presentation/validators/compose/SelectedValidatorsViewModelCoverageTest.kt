package jp.co.soramitsu.staking.impl.presentation.validators.compose

import androidx.lifecycle.viewModelScope
import io.mockk.coEvery
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import java.math.BigDecimal
import java.math.BigInteger
import jp.co.soramitsu.common.list.GroupedList
import jp.co.soramitsu.common.presentation.LoadingState
import jp.co.soramitsu.common.resources.ResourceManager
import jp.co.soramitsu.fearless_utils.extensions.toHexString
import jp.co.soramitsu.feature_staking_impl.R
import jp.co.soramitsu.runtime.ext.addressOf
import jp.co.soramitsu.staking.api.domain.model.NominatedValidator
import jp.co.soramitsu.staking.api.domain.model.RootIdentity
import jp.co.soramitsu.staking.api.domain.model.Validator
import jp.co.soramitsu.staking.impl.StakingLaneFixtures
import jp.co.soramitsu.staking.impl.domain.StakingInteractor
import jp.co.soramitsu.staking.impl.presentation.StakingRouter
import jp.co.soramitsu.staking.impl.presentation.common.SelectedValidatorsFlowState
import jp.co.soramitsu.staking.impl.presentation.common.StakingPoolSharedStateProvider
import jp.co.soramitsu.staking.impl.presentation.common.StakingPoolState
import jp.co.soramitsu.staking.impl.presentation.validators.parcel.ValidatorDetailsParcelModel
import jp.co.soramitsu.staking.impl.scenarios.StakingPoolInteractor
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.job
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import kotlinx.coroutines.withTimeout
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
class SelectedValidatorsViewModelCoverageTest {

    private val owned = mutableListOf<SelectedValidatorsViewModel>()

    private val chainAsset = StakingLaneFixtures.chainAsset()
    private val chain = StakingLaneFixtures.chain(chainAsset)
    private val poolId = BigInteger.valueOf(5)
    private val selection = listOf(ByteArray(32) { 1 })

    private val provider = StakingPoolSharedStateProvider()
    private val poolInteractor = mockk<StakingPoolInteractor>(relaxed = true)
    private val resources = mockk<ResourceManager>(relaxed = true)
    private val router = mockk<StakingRouter>(relaxed = true)
    private val interactor = mockk<StakingInteractor>(relaxed = true)
    private val detailsCache = MutableStateFlow<Map<String, ValidatorDetailsParcelModel>>(emptyMap())

    private val active = validator(1, "Active one")
    private val elected = validator(2, "Elected one")
    private val inactive = validator(3, "Inactive one")
    private val waiting = validator(4, "Waiting one")

    private fun validator(seed: Int, name: String): Validator {
        val accountId = ByteArray(32) { seed.toByte() }
        return Validator(
            address = chain.addressOf(accountId),
            slashed = false,
            accountIdHex = accountId.toHexString(),
            prefs = null,
            electedInfo = Validator.ElectedInfo(
                totalStake = StakingLaneFixtures.planks(100),
                ownStake = StakingLaneFixtures.planks(10),
                nominatorStakes = emptyList(),
                apy = BigDecimal("0.1"),
                isOversubscribed = false
            ),
            identity = RootIdentity(name, null, null, null, null, null, null, null)
        )
    }

    private val withActive: GroupedList<NominatedValidator.Status.Group, NominatedValidator> = linkedMapOf(
        NominatedValidator.Status.Group.Active(1) to listOf(
            NominatedValidator(active, NominatedValidator.Status.Active(StakingLaneFixtures.planks(1), true))
        ),
        NominatedValidator.Status.Group.Elected(1) to listOf(NominatedValidator(elected, NominatedValidator.Status.Elected)),
        NominatedValidator.Status.Group.Inactive(1) to listOf(NominatedValidator(inactive, NominatedValidator.Status.Inactive)),
        NominatedValidator.Status.Group.WaitingForNextEra(16, 1) to listOf(
            NominatedValidator(waiting, NominatedValidator.Status.WaitingForNextEra)
        )
    )

    @Before
    fun setUp() {
        Dispatchers.setMain(UnconfinedTestDispatcher())
        every { resources.getString(any()) } answers { "s${firstArg<Int>()}" }
        every { resources.getString(any(), *anyVararg()) } answers {
            "f${firstArg<Int>()}:${secondArg<Array<Any>>().joinToString()}"
        }
        every { interactor.validatorDetailsCache } returns detailsCache
        coEvery { poolInteractor.nominatedValidatorsFlow(any(), any()) } returns flowOf(withActive)
        provider.mainState.set(
            StakingPoolState(
                chain = chain,
                asset = StakingLaneFixtures.asset(chainAsset),
                chainAsset = chainAsset,
                address = chain.addressOf(StakingLaneFixtures.accountId)
            )
        )
        provider.selectedValidatorsState.set(
            SelectedValidatorsFlowState(selection, canChangeValidators = true, poolName = "Pool", poolId = poolId)
        )
    }

    @After
    fun tearDown() {
        runBlocking { withTimeout(5_000) { owned.forEach { it.viewModelScope.coroutineContext.job.cancelAndJoin() } } }
        Dispatchers.resetMain()
    }

    private fun waitUntil(condition: () -> Boolean) {
        val deadline = System.currentTimeMillis() + 5_000
        while (!condition() && System.currentTimeMillis() < deadline) Thread.sleep(10)
        assertTrue("condition not reached", condition())
    }

    private fun viewModel() = SelectedValidatorsViewModel(provider, poolInteractor, resources, router, interactor).also(owned::add)

    private fun SelectedValidatorsViewModel.awaitLoaded(): SelectedValidatorsScreenViewState {
        waitUntil { state.value is LoadingState.Loaded }
        return (state.value as LoadingState.Loaded).data
    }

    @Test
    fun groupsShowTheirTitlesIconsAndValidators() {
        val loaded = viewModel().awaitLoaded()

        assertTrue(loaded.canChangeValidators)
        val (activeGroup, electedGroup, inactiveGroup, waitingGroup) = loaded.groups
        assertEquals("f${R.string.staking_your_elected_format}:1", activeGroup.title)
        assertEquals(R.drawable.ic_status_success_16, activeGroup.titleIcon)
        assertEquals("s${R.string.staking_your_allocated_description}", activeGroup.description)
        assertEquals(listOf(active.accountIdHex), activeGroup.listState.items.map { it.id })

        assertNull(electedGroup.title)
        assertNull(electedGroup.titleIcon)
        assertEquals("s${R.string.staking_your_not_allocated_description}", electedGroup.description)

        assertEquals("f${R.string.staking_your_not_elected_format}:1", inactiveGroup.title)
        assertNull(inactiveGroup.titleIcon)
        assertEquals("s${R.string.staking_your_inactive_description}", inactiveGroup.description)

        assertEquals("f${R.string.staking_custom_header_validators_title}:1, 16", waitingGroup.title)
        assertEquals(R.drawable.ic_time_24, waitingGroup.titleIcon)
        assertEquals("s${R.string.staking_your_validators_changing_title}", waitingGroup.description)
        assertEquals(listOf(waiting.accountIdHex), waitingGroup.listState.selectedItems.map { it.id })
    }

    @Test
    fun withoutActiveValidatorsTheElectedGroupCarriesTheTitle() {
        coEvery { poolInteractor.nominatedValidatorsFlow(any(), any()) } returns flowOf(
            linkedMapOf<NominatedValidator.Status.Group, List<NominatedValidator>>(
                NominatedValidator.Status.Group.Elected(1) to listOf(NominatedValidator(elected, NominatedValidator.Status.Elected))
            )
        )

        val electedGroup = viewModel().awaitLoaded().groups.single()

        assertEquals("f${R.string.staking_your_elected_format}:1", electedGroup.title)
        assertEquals(R.drawable.ic_status_success_16, electedGroup.titleIcon)
    }

    @Test
    fun changeValidatorsStartsTheSelectionWithThePoolsValidators() {
        val vm = viewModel()

        vm.onChangeValidatorsClick()

        val started = requireNotNull(provider.selectValidatorsState.get())
        assertSame(selection, started.selectedValidators)
        assertEquals("Pool", started.poolName)
        assertEquals(poolId, started.poolId)
        verify { router.openStartSelectValidators() }
    }

    @Test
    fun infoCachesTheValidatorDetailsAndOpensThem() {
        val vm = viewModel()
        val item = vm.awaitLoaded().groups[2].listState.items.single()

        val opened = CompletableDeferred<String>()
        every { router.openValidatorDetails(any()) } answers {
            opened.complete(firstArg())
            Unit
        }

        vm.onInfoClick(item.copy(id = "removed-validator"))
        vm.onInfoClick(item)

        assertEquals(inactive.accountIdHex, runBlocking { withTimeout(10_000) { opened.await() } })
        verify(exactly = 1) { router.openValidatorDetails(any()) }
        assertEquals(inactive.accountIdHex, detailsCache.value[inactive.accountIdHex]?.accountIdHex)
    }

    @Test
    fun backGoesBack() {
        viewModel().onBackClick()

        verify { router.back() }
    }
}
