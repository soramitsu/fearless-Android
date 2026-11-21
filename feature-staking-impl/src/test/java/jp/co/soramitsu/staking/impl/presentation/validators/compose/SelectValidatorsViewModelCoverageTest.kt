package jp.co.soramitsu.staking.impl.presentation.validators.compose

import io.mockk.coEvery
import io.mockk.every
import io.mockk.mockk
import io.mockk.slot
import io.mockk.verify
import java.math.BigDecimal
import java.math.BigInteger
import jp.co.soramitsu.common.AlertViewState
import jp.co.soramitsu.common.presentation.LoadingState
import jp.co.soramitsu.common.resources.ResourceManager
import jp.co.soramitsu.fearless_utils.extensions.fromHex
import jp.co.soramitsu.fearless_utils.extensions.toHexString
import jp.co.soramitsu.feature_staking_impl.R
import jp.co.soramitsu.runtime.ext.addressOf
import jp.co.soramitsu.staking.api.domain.model.RootIdentity
import jp.co.soramitsu.staking.api.domain.model.Validator
import jp.co.soramitsu.staking.api.domain.model.ValidatorPrefs
import jp.co.soramitsu.staking.impl.StakingLaneFixtures
import jp.co.soramitsu.staking.impl.domain.StakingInteractor
import jp.co.soramitsu.staking.impl.domain.recommendations.ValidatorRecommendator
import jp.co.soramitsu.staking.impl.domain.recommendations.ValidatorRecommendatorFactory
import jp.co.soramitsu.staking.impl.domain.recommendations.settings.RecommendationSettingsProvider
import jp.co.soramitsu.staking.impl.domain.recommendations.settings.RecommendationSettingsProviderFactory
import jp.co.soramitsu.staking.impl.domain.recommendations.settings.SettingsStorage
import jp.co.soramitsu.staking.impl.presentation.StakingRouter
import jp.co.soramitsu.staking.impl.presentation.common.SelectValidatorFlowState
import jp.co.soramitsu.staking.impl.presentation.common.SelectValidatorFlowState.ValidatorSelectMode
import jp.co.soramitsu.staking.impl.presentation.common.StakingPoolSharedStateProvider
import jp.co.soramitsu.staking.impl.presentation.common.StakingPoolState
import jp.co.soramitsu.staking.impl.presentation.pools.compose.SelectableListItemState
import jp.co.soramitsu.staking.impl.presentation.validators.parcel.ValidatorDetailsParcelModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
class SelectValidatorsViewModelCoverageTest {

    private val chainAsset = StakingLaneFixtures.chainAsset()
    private val chain = StakingLaneFixtures.chain(chainAsset)

    private val router = mockk<StakingRouter>(relaxed = true)
    private val recommendatorFactory = mockk<ValidatorRecommendatorFactory>(relaxed = true)
    private val settingsFactory = mockk<RecommendationSettingsProviderFactory>(relaxed = true)
    private val resources = mockk<ResourceManager>(relaxed = true)
    private val provider = StakingPoolSharedStateProvider()
    private val settingsStorage = SettingsStorage()
    private val interactor = mockk<StakingInteractor>(relaxed = true)
    private val detailsCache = MutableStateFlow<Map<String, ValidatorDetailsParcelModel>>(emptyMap())

    private val alpha = validator("01", "Alpha", BigDecimal("0.15"))
    private val beta = validator("02", "Beta", BigDecimal("0.10"))

    private fun validator(byte: String, name: String, apy: BigDecimal) = Validator(
        address = "$byte-address",
        slashed = false,
        accountIdHex = byte.repeat(32),
        prefs = ValidatorPrefs(BigDecimal("0.05"), false),
        electedInfo = Validator.ElectedInfo(
            totalStake = StakingLaneFixtures.planks(100),
            ownStake = StakingLaneFixtures.planks(10),
            nominatorStakes = emptyList(),
            apy = apy,
            isOversubscribed = false
        ),
        identity = RootIdentity(name, null, null, null, null, null, null, null)
    )

    @Before
    fun setUp() {
        Dispatchers.setMain(UnconfinedTestDispatcher())
        every { resources.getString(any()) } answers { "s${firstArg<Int>()}" }
        every { resources.getString(any(), *anyVararg()) } answers { "f${firstArg<Int>()}" }
        every { interactor.validatorDetailsCache } returns detailsCache
        coEvery { settingsFactory.createRelayChain(any()) } returns RecommendationSettingsProvider.RelayChain(256, 16)
        coEvery { recommendatorFactory.create(any()) } returns ValidatorRecommendator(listOf(alpha, beta))
        provider.mainState.set(
            StakingPoolState(
                chain = chain,
                asset = StakingLaneFixtures.asset(chainAsset),
                chainAsset = chainAsset,
                address = chain.addressOf(StakingLaneFixtures.accountId)
            )
        )
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    private fun waitUntil(condition: () -> Boolean) {
        val deadline = System.currentTimeMillis() + 5_000
        while (!condition() && System.currentTimeMillis() < deadline) Thread.sleep(10)
        assertTrue("condition not reached", condition())
    }

    private fun viewModel(mode: ValidatorSelectMode): SelectValidatorsViewModel {
        provider.selectValidatorsState.set(
            SelectValidatorFlowState(
                selectedValidators = listOf(alpha.accountIdHex.fromHex()),
                poolName = "Pool",
                poolId = BigInteger.valueOf(5),
                selectMode = mode
            )
        )
        return SelectValidatorsViewModel(router, recommendatorFactory, settingsFactory, resources, provider, settingsStorage, interactor)
    }

    private fun SelectValidatorsViewModel.awaitSelected(count: Int): ValidatorsListViewState {
        waitUntil { (state.value.listState as? LoadingState.Loaded)?.data?.selectedItems?.size == count }
        return (state.value.listState as LoadingState.Loaded).data
    }

    @Test
    fun customModeStartsWithPoolSelectionAndConfirmsChanges() {
        val vm = viewModel(ValidatorSelectMode.CUSTOM)
        val list = vm.awaitSelected(1)

        assertEquals("s${R.string.staking_select_custom}", vm.state.value.toolbarTitle)
        assertTrue(vm.state.value.isCustom)
        assertEquals(listOf(alpha.accountIdHex, beta.accountIdHex), list.segments.single().items.map { it.id })

        vm.onSelected(list.segments.single().items[1])
        vm.awaitSelected(2)
        vm.onChooseClick()

        val stored = provider.selectValidatorsState.get()!!.selectedValidators.map { it.toHexString(false) }
        assertEquals(listOf(alpha.accountIdHex, beta.accountIdHex), stored)
        verify { router.openConfirmSelectValidators() }
    }

    @Test
    fun deselectingAndOversubscribedSelection() {
        val vm = viewModel(ValidatorSelectMode.CUSTOM)
        val alphaItem = vm.awaitSelected(1).segments.single().items[0]
        val alert = slot<AlertViewState>()
        every { router.openAlert(capture(alert)) } returns Unit

        vm.onSelected(alphaItem)
        vm.awaitSelected(0)
        vm.onSelected(
            SelectableListItemState(
                id = beta.accountIdHex,
                title = "Beta",
                isSelected = false,
                additionalStatuses = listOf(SelectableListItemState.SelectableListItemAdditionalStatus.OVERSUBSCRIBED)
            )
        )

        assertEquals("s${R.string.alert_oversubscribed_alert_title}", alert.captured.title)
        assertEquals(listOf(beta.accountIdHex), vm.awaitSelected(1).selectedItems.map { it.id })
    }

    @Test
    fun recommendedModeSelectsRecommendationsAndIgnoresTaps() {
        val vm = viewModel(ValidatorSelectMode.RECOMMENDED)
        val list = vm.awaitSelected(2)

        vm.onSelected(list.segments.single().items[0])

        assertEquals("s${R.string.staking_select_suggested}", vm.state.value.toolbarTitle)
        assertTrue(!vm.state.value.isCustom)
        assertEquals(2, vm.awaitSelected(2).selectedItems.size)
    }

    @Test
    fun infoSearchOptionsAndBackCallbacks() {
        val vm = viewModel(ValidatorSelectMode.CUSTOM)
        val alphaItem = vm.awaitSelected(1).segments.single().items[0]

        vm.onInfoClick(alphaItem)
        vm.onInfoClick(alphaItem.copy(id = "ff".repeat(32)))
        vm.onSearchQueryInput("beta")
        vm.onOptionsClick()
        vm.onNavigationClick()

        assertEquals(setOf(alpha.accountIdHex), detailsCache.value.keys)
        verify(exactly = 1) { router.openValidatorDetails(alpha.accountIdHex) }
        waitUntil {
            (vm.state.value.listState as? LoadingState.Loaded)?.data?.segments?.singleOrNull()?.items?.map { it.id } == listOf(beta.accountIdHex)
        }
        assertEquals("beta", vm.state.value.searchQuery)
        verify { router.openValidatorsSettings() }
        verify { router.back() }
    }
}
