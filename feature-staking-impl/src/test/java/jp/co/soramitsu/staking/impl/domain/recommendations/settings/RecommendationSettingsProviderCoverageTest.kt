package jp.co.soramitsu.staking.impl.domain.recommendations.settings

import java.math.BigInteger
import jp.co.soramitsu.staking.impl.domain.recommendations.settings.filters.BlockProducerFilters
import jp.co.soramitsu.staking.impl.domain.recommendations.settings.filters.Filters
import jp.co.soramitsu.staking.impl.domain.recommendations.settings.filters.Sorting
import jp.co.soramitsu.staking.impl.domain.recommendations.settings.filters.toFiltersSchema
import jp.co.soramitsu.staking.impl.domain.recommendations.settings.filters.toSortingSchema
import jp.co.soramitsu.staking.impl.domain.recommendations.settings.sortings.BlockProducersSorting
import jp.co.soramitsu.staking.impl.presentation.validators.change.custom.settings.SettingsSchema
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test

/** Recommendation settings for validators and collators: defaults and the custom settings a schema selects. */
class RecommendationSettingsProviderCoverageTest {

    private val allFilters = Filters.values().toList()

    private fun schema(filters: Set<Filters>, sorting: Sorting?) = SettingsSchema(
        filters = allFilters.toFiltersSchema(filters),
        sortings = if (sorting == null) {
            Sorting.values().map { SettingsSchema.Sorting(0, false, it) }
        } else {
            Sorting.values().toList().toSortingSchema(sorting)
        }
    )

    @Test
    fun `relay chain defaults limit the validators per nominator`() = runTest {
        val provider = RecommendationSettingsProvider.RelayChain(maximumRewardedNominators = 512, maximumValidatorsPerNominator = 16)

        val defaults = provider.defaultSettings()

        assertEquals(listOf<BlockProducerFilters<*>>(BlockProducerFilters.ValidatorFilter.HasBlocked), defaults.alwaysEnabledFilters)
        assertEquals(4, defaults.customEnabledFilters.size)
        assertTrue(defaults.customEnabledFilters.contains(BlockProducerFilters.ValidatorFilter.ElectedFilter))
        assertEquals(5, defaults.allFilters.size)
        assertSame(BlockProducersSorting.ValidatorSorting.APYSorting, defaults.sorting)
        assertEquals(16, defaults.limit)
        assertTrue(defaults.postProcessors.isEmpty())
    }

    @Test
    fun `relay chain custom settings start empty and are observed`() = runTest {
        val provider = RecommendationSettingsProvider.RelayChain(null, 16)

        val initial = provider.observeRecommendationSettings().first()

        assertEquals(provider.defaultSelectCustomSettings(), initial)
        assertTrue(initial.allFilters.isEmpty())
        assertNull(initial.limit)
        assertEquals(initial, provider.currentSettings())
    }

    @Test
    fun `relay chain schema selects its filters and sorting`() = runTest {
        val provider = RecommendationSettingsProvider.RelayChain(512, 16)

        provider.settingsChanged(schema(allFilters.toSet(), Sorting.TotalStake), BigInteger.ZERO)
        val settings = provider.currentSettings()

        assertEquals(3, settings.customEnabledFilters.size)
        assertTrue(settings.customEnabledFilters.contains(BlockProducerFilters.ValidatorFilter.HasIdentity))
        assertTrue(settings.customEnabledFilters.contains(BlockProducerFilters.ValidatorFilter.NotSlashedFilter))
        assertTrue(settings.customEnabledFilters.any { it is BlockProducerFilters.ValidatorFilter.NotOverSubscribedFilter })
        assertSame(BlockProducersSorting.ValidatorSorting.TotalStakeSorting, settings.sorting)
        assertSame(settings, provider.observeRecommendationSettings().first())
    }

    @Test
    fun `relay chain sortings map to validator sortings`() {
        val provider = RecommendationSettingsProvider.RelayChain(512, 16)
        val expected = mapOf(
            Sorting.ValidatorsOwnStake to BlockProducersSorting.ValidatorSorting.ValidatorOwnStakeSorting,
            Sorting.EstimatedRewards to BlockProducersSorting.ValidatorSorting.APYSorting,
            Sorting.TotalStake to BlockProducersSorting.ValidatorSorting.TotalStakeSorting
        )
        for ((sorting, validatorSorting) in expected) {
            provider.settingsChanged(schema(emptySet(), sorting), BigInteger.ZERO)
            assertSame(validatorSorting, provider.currentSettings().sorting)
            assertTrue(provider.currentSettings().customEnabledFilters.isEmpty())
        }
    }

    @Test
    fun `relay chain keeps its settings for an unsupported or missing sorting`() {
        val provider = RecommendationSettingsProvider.RelayChain(512, 16)
        provider.settingsChanged(schema(setOf(Filters.HavingOnChainIdentity), Sorting.TotalStake), BigInteger.ZERO)
        val before = provider.currentSettings()

        provider.settingsChanged(schema(emptySet(), Sorting.MinimumBond), BigInteger.ZERO)
        provider.settingsChanged(schema(emptySet(), null), BigInteger.ZERO)

        assertSame(before, provider.currentSettings())
    }

    @Test
    fun `parachain defaults limit the delegations`() {
        val provider = RecommendationSettingsProvider.Parachain(maxTopDelegationPerCandidate = 300, maxDelegationsPerDelegator = 100)

        val defaults = provider.defaultSettings()
        val custom = provider.defaultSelectCustomSettings()

        assertTrue(defaults.allFilters.isEmpty())
        assertSame(BlockProducersSorting.CollatorSorting.APYSorting, defaults.sorting)
        assertEquals(100, defaults.limit)
        assertNull(custom.limit)
        assertEquals(custom, provider.currentSettings())
    }

    @Test
    fun `parachain schema selects collator filters with the bond amount`() {
        val provider = RecommendationSettingsProvider.Parachain(300, 100)

        provider.settingsChanged(schema(allFilters.toSet(), Sorting.Delegations), BigInteger.TEN)
        val settings = provider.currentSettings()

        assertEquals(3, settings.customEnabledFilters.size)
        assertTrue(settings.customEnabledFilters.contains(BlockProducerFilters.CollatorFilter.HavingOnChainIdentity))
        assertTrue(settings.customEnabledFilters.contains(BlockProducerFilters.CollatorFilter.NotOverSubscribed))
        assertTrue(settings.customEnabledFilters.any { it is BlockProducerFilters.CollatorFilter.WithRelevantBond })
        assertSame(BlockProducersSorting.CollatorSorting.DelegationsSorting, settings.sorting)
    }

    @Test
    fun `parachain sortings map to collator sortings`() {
        val provider = RecommendationSettingsProvider.Parachain(300, 100)
        val expected = mapOf(
            Sorting.EstimatedRewards to BlockProducersSorting.CollatorSorting.APYSorting,
            Sorting.EffectiveAmountBonded to BlockProducersSorting.CollatorSorting.EffectiveAmountBondedSorting,
            Sorting.CollatorsOwnStake to BlockProducersSorting.CollatorSorting.CollatorsOwnStakeSorting,
            Sorting.Delegations to BlockProducersSorting.CollatorSorting.DelegationsSorting,
            Sorting.MinimumBond to BlockProducersSorting.CollatorSorting.MinimumBondSorting
        )
        for ((sorting, collatorSorting) in expected) {
            provider.settingsChanged(schema(emptySet(), sorting), BigInteger.ZERO)
            assertSame(collatorSorting, provider.currentSettings().sorting)
        }
    }

    @Test
    fun `custom settings replace filters and sorting only`() {
        val provider = RecommendationSettingsProvider.RelayChain(512, 16)
        val filters = listOf<BlockProducerFilters<jp.co.soramitsu.staking.api.domain.model.Validator>>(BlockProducerFilters.ValidatorFilter.NotSlashedFilter)

        provider.setCustomSettings(filters, BlockProducersSorting.ValidatorSorting.ValidatorOwnStakeSorting)

        assertEquals(filters, provider.currentSettings().customEnabledFilters)
        assertSame(BlockProducersSorting.ValidatorSorting.ValidatorOwnStakeSorting, provider.currentSettings().sorting)
        assertNull(provider.currentSettings().limit)
    }
}
