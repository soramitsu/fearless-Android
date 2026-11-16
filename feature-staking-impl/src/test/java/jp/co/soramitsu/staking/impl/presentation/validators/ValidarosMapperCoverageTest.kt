package jp.co.soramitsu.staking.impl.presentation.validators

import io.mockk.every
import io.mockk.mockk
import java.math.BigDecimal
import jp.co.soramitsu.common.resources.ResourceManager
import jp.co.soramitsu.feature_staking_impl.R
import jp.co.soramitsu.staking.api.domain.model.RootIdentity
import jp.co.soramitsu.staking.api.domain.model.Validator
import jp.co.soramitsu.staking.api.domain.model.ValidatorPrefs
import jp.co.soramitsu.staking.impl.StakingLaneFixtures
import jp.co.soramitsu.staking.impl.domain.recommendations.settings.RecommendationSettings
import jp.co.soramitsu.staking.impl.domain.recommendations.settings.sortings.BlockProducersSorting
import jp.co.soramitsu.staking.impl.presentation.common.SelectValidatorFlowState.ValidatorSelectMode
import jp.co.soramitsu.staking.impl.presentation.pools.compose.SelectableListItemState.SelectableListItemAdditionalStatus
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class ValidarosMapperCoverageTest {

    private val resources = mockk<ResourceManager>(relaxed = true)
    private val asset = StakingLaneFixtures.asset()

    private val alpha = Validator(
        address = "alpha-address",
        slashed = false,
        accountIdHex = "01".repeat(32),
        prefs = ValidatorPrefs(BigDecimal("0.05"), false),
        electedInfo = Validator.ElectedInfo(
            totalStake = StakingLaneFixtures.planks(1000),
            ownStake = StakingLaneFixtures.planks(10),
            nominatorStakes = emptyList(),
            apy = BigDecimal("0.15"),
            isOversubscribed = false
        ),
        identity = RootIdentity("Alpha", null, null, null, null, null, null, null)
    )

    private val beta = Validator(
        address = "beta-address",
        slashed = false,
        accountIdHex = "02".repeat(32),
        prefs = null,
        electedInfo = Validator.ElectedInfo(
            totalStake = StakingLaneFixtures.planks(500),
            ownStake = StakingLaneFixtures.planks(5),
            nominatorStakes = emptyList(),
            apy = BigDecimal("0.10"),
            isOversubscribed = true
        ),
        identity = null
    )

    private val gamma = Validator(
        address = "gamma-address",
        slashed = false,
        accountIdHex = "03".repeat(32),
        prefs = ValidatorPrefs(BigDecimal("0.1"), true),
        electedInfo = null,
        identity = RootIdentity("Gamma", null, null, null, null, null, null, null)
    )

    private val all = listOf(alpha, beta, gamma)

    @Before
    fun setUp() {
        every { resources.getString(any()) } answers { "s${firstArg<Int>()}" }
        every { resources.getString(any(), *anyVararg()) } answers { "f${firstArg<Int>()}" }
    }

    private fun settings(sorting: BlockProducersSorting<Validator>) = RecommendationSettings(
        alwaysEnabledFilters = emptyList(),
        customEnabledFilters = emptyList(),
        postProcessors = emptyList(),
        sorting = sorting
    )

    private fun build(
        query: String = "",
        mode: ValidatorSelectMode = ValidatorSelectMode.CUSTOM,
        selected: Collection<String> = setOf(alpha.accountIdHex),
        settings: RecommendationSettings<Validator>? = null
    ) = buildSegmentedValidatorsListState(resources, all, settings, query, mode, selected, asset)

    @Test
    fun customModeSplitsElectedAndWaitingValidators() {
        val state = build()

        assertEquals(2, state.segments.size)
        val elected = state.segments[0]
        assertEquals("f${R.string.staking_your_elected_format}", elected.title)
        assertEquals(R.drawable.ic_elected_validator, elected.iconRes)
        assertEquals("s${R.string.staking_rewards_apy}", elected.sortingValue)
        assertEquals(listOf(alpha.accountIdHex, beta.accountIdHex), elected.items.map { it.id })
        val waiting = state.segments[1]
        assertEquals("f${R.string.staking_your_not_elected_format}", waiting.title)
        assertEquals(R.drawable.ic_validator_waiting, waiting.iconRes)
        assertNull(waiting.sortingValue)
        assertEquals(listOf(alpha.accountIdHex), state.selectedItems.map { it.id })
        assertTrue(!state.isEmpty)
    }

    @Test
    fun electedItemsShowIdentityStakeAndWarnings() {
        val (alphaItem, betaItem) = build().segments[0].items

        assertEquals("Alpha", alphaItem.title)
        assertEquals("f${R.string.staking_validator_total_stake_token}", alphaItem.subtitle)
        assertEquals("s${R.string.staking_rewards_apy} 15%", alphaItem.caption?.text)
        assertTrue(alphaItem.isSelected)
        assertTrue(alphaItem.additionalStatuses.isEmpty())
        assertEquals("beta-address", betaItem.title)
        assertEquals(listOf(SelectableListItemAdditionalStatus.WARNING), betaItem.additionalStatuses)
        assertTrue(!betaItem.isSelected)
    }

    @Test
    fun waitingItemShowsCommissionAndBlockedWarning() {
        val gammaItem = build().segments[1].items.single()

        assertEquals("Gamma", gammaItem.title)
        assertNull(gammaItem.subtitle)
        assertEquals("s${R.string.common_commission} 10%", gammaItem.caption?.text)
        assertEquals(listOf(SelectableListItemAdditionalStatus.WARNING), gammaItem.additionalStatuses)
    }

    @Test
    fun recommendedModeSelectsEverything() {
        val state = build(mode = ValidatorSelectMode.RECOMMENDED, selected = emptySet())

        assertEquals(3, state.selectedItems.size)
        assertTrue(state.segments.all { segment -> segment.items.all { it.isSelected } })
    }

    @Test
    fun searchMatchesIdentityOrAddressIgnoringCase() {
        val byName = build(query = "ALP")
        assertEquals(1, byName.segments.size)
        assertEquals(listOf(alpha.accountIdHex), byName.segments.single().items.map { it.id })

        val byAddress = build(query = "gamma-ADD")
        assertEquals(listOf(gamma.accountIdHex), byAddress.segments.single().items.map { it.id })

        val none = build(query = "zzz")
        assertTrue(none.isEmpty)
        assertTrue(none.selectedItems.isEmpty())
    }

    @Test
    fun sortingSelectsSegmentCaption() {
        assertEquals(
            "s${R.string.staking_validator_total_stake}",
            build(settings = settings(BlockProducersSorting.ValidatorSorting.TotalStakeSorting)).segments[0].sortingValue
        )
        assertEquals(
            "s${R.string.staking_rewards_apy}",
            build(settings = settings(BlockProducersSorting.ValidatorSorting.APYSorting)).segments[0].sortingValue
        )
        val ownStake = build(settings = settings(BlockProducersSorting.ValidatorSorting.ValidatorOwnStakeSorting)).segments[0]
        assertEquals("s${R.string.staking_filter_title_own_stake}", ownStake.sortingValue)
        val caption = ownStake.items.first().caption!!.text
        assertTrue(caption, caption.startsWith("s${R.string.staking_filter_title_own_stake} ") && caption.contains("10") && caption.contains("DOT"))
    }

    @Test
    fun slashedOrBlockedElectedValidatorIsFlagged() {
        val slashed = Validator(alpha.address, true, alpha.accountIdHex, alpha.prefs, alpha.electedInfo, alpha.identity)
        val blocked = Validator(alpha.address, false, alpha.accountIdHex, ValidatorPrefs(BigDecimal.ONE, true), alpha.electedInfo, null)

        val slashedItem = slashed.toModel(false, null, asset, resources)
        val blockedItem = blocked.toModel(true, BlockProducersSorting.ValidatorSorting.TotalStakeSorting, asset, resources)

        assertEquals(listOf(SelectableListItemAdditionalStatus.WARNING), slashedItem.additionalStatuses)
        assertEquals(listOf(SelectableListItemAdditionalStatus.WARNING), blockedItem.additionalStatuses)
        assertEquals("alpha-address", blockedItem.title)
        assertTrue(blockedItem.isSelected)
        assertEquals("s${R.string.staking_rewards_apy} 15%", blockedItem.caption?.text)
    }

    @Test
    fun notElectedWithoutPrefsHasNoWarning() {
        val plain = Validator("plain", false, "04".repeat(32), null, null, null)

        val item = plain.toNotElectedModel(false, resources)

        assertEquals("plain", item.title)
        assertTrue(item.additionalStatuses.isEmpty())
        assertTrue(!item.isSelected)
        assertNull(item.subtitle)
    }
}
