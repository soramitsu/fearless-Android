package jp.co.soramitsu.staking.impl.domain.recommendations.settings.filters

import java.math.BigDecimal
import java.math.BigInteger
import jp.co.soramitsu.feature_staking_impl.R
import jp.co.soramitsu.staking.api.domain.model.CandidateCapacity
import jp.co.soramitsu.staking.api.domain.model.CandidateInfoStatus
import jp.co.soramitsu.staking.api.domain.model.Collator
import jp.co.soramitsu.staking.api.domain.model.IndividualExposure
import jp.co.soramitsu.staking.api.domain.model.RootIdentity
import jp.co.soramitsu.staking.api.domain.model.Validator
import jp.co.soramitsu.staking.api.domain.model.ValidatorPrefs
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** Validator and collator filters, their schema models and list filtering. */
class BlockProducerFiltersCoverageTest {

    private val identity = RootIdentity("Name", null, null, null, null, null, null, null)

    private fun validator(
        slashed: Boolean = false,
        prefs: ValidatorPrefs? = ValidatorPrefs(BigDecimal("0.1"), false),
        nominators: Int? = 2,
        identity: RootIdentity? = this.identity
    ) = Validator(
        address = "address",
        slashed = slashed,
        accountIdHex = "00",
        prefs = prefs,
        electedInfo = nominators?.let {
            Validator.ElectedInfo(
                BigInteger.TEN, BigInteger.ONE, List(it) { IndividualExposure(ByteArray(32), BigInteger.ONE) }, BigDecimal.ONE, false
            )
        },
        identity = identity
    )

    private fun collator(capacity: CandidateCapacity = CandidateCapacity.Partial, lowestTop: Long = 5, identity: RootIdentity? = this.identity) = Collator(
        address = "collator",
        bond = BigInteger.TEN,
        delegationCount = BigInteger.ONE,
        totalCounted = BigInteger.TEN,
        lowestTopDelegationAmount = BigInteger.valueOf(lowestTop),
        highestBottomDelegationAmount = BigInteger.ONE,
        lowestBottomDelegationAmount = BigInteger.ONE,
        topCapacity = capacity,
        bottomCapacity = CandidateCapacity.Empty,
        request = null,
        status = CandidateInfoStatus.ACTIVE,
        identity = identity,
        apy = null
    )

    @Test
    fun `validator identity, slashing and election filters`() {
        assertTrue(BlockProducerFilters.ValidatorFilter.HasIdentity.shouldInclude(validator()))
        assertFalse(BlockProducerFilters.ValidatorFilter.HasIdentity.shouldInclude(validator(identity = null)))
        assertTrue(BlockProducerFilters.ValidatorFilter.NotSlashedFilter.shouldInclude(validator()))
        assertFalse(BlockProducerFilters.ValidatorFilter.NotSlashedFilter.shouldInclude(validator(slashed = true)))
        assertTrue(BlockProducerFilters.ValidatorFilter.ElectedFilter.shouldInclude(validator()))
        assertFalse(BlockProducerFilters.ValidatorFilter.ElectedFilter.shouldInclude(validator(nominators = null)))
    }

    @Test
    fun `blocked and full commission validators are excluded`() {
        assertTrue(BlockProducerFilters.ValidatorFilter.HasBlocked.shouldInclude(validator()))
        assertFalse(BlockProducerFilters.ValidatorFilter.HasBlocked.shouldInclude(validator(prefs = ValidatorPrefs(BigDecimal.ZERO, true))))
        assertFalse(BlockProducerFilters.ValidatorFilter.HasBlocked.shouldInclude(validator(prefs = null)))
        assertTrue(BlockProducerFilters.ValidatorFilter.HundredPercentCommissionFilter.shouldInclude(validator()))
        assertTrue(BlockProducerFilters.ValidatorFilter.HundredPercentCommissionFilter.shouldInclude(validator(prefs = null)))
        assertFalse(BlockProducerFilters.ValidatorFilter.HundredPercentCommissionFilter.shouldInclude(validator(prefs = ValidatorPrefs(BigDecimal.ONE, false))))
    }

    @Test
    fun `oversubscription compares nominators with the rewarded maximum`() {
        assertTrue(BlockProducerFilters.ValidatorFilter.NotOverSubscribedFilter(null).shouldInclude(validator(nominators = 1_000)))
        assertTrue(BlockProducerFilters.ValidatorFilter.NotOverSubscribedFilter(3).shouldInclude(validator(nominators = 2)))
        assertFalse(BlockProducerFilters.ValidatorFilter.NotOverSubscribedFilter(2).shouldInclude(validator(nominators = 2)))
        val notElected = runCatching { BlockProducerFilters.ValidatorFilter.NotOverSubscribedFilter(2).shouldInclude(validator(nominators = null)) }
        assertTrue(notElected.exceptionOrNull() is IllegalStateException)
    }

    @Test
    fun `collator filters`() {
        assertTrue(BlockProducerFilters.CollatorFilter.HavingOnChainIdentity.shouldInclude(collator()))
        assertFalse(BlockProducerFilters.CollatorFilter.HavingOnChainIdentity.shouldInclude(collator(identity = null)))
        assertTrue(BlockProducerFilters.CollatorFilter.NotOverSubscribed.shouldInclude(collator()))
        assertFalse(BlockProducerFilters.CollatorFilter.NotOverSubscribed.shouldInclude(collator(capacity = CandidateCapacity.Full)))
        assertTrue(BlockProducerFilters.CollatorFilter.WithRelevantBond(BigInteger.valueOf(5)).shouldInclude(collator(lowestTop = 5)))
        assertFalse(BlockProducerFilters.CollatorFilter.WithRelevantBond(BigInteger.valueOf(4)).shouldInclude(collator(lowestTop = 5)))
    }

    @Test
    fun `list filtering keeps items passing every filter`() {
        val kept = validator()
        val slashed = validator(slashed = true)
        val anonymous = validator(identity = null)
        val filters = listOf<BlockProducerFilters<Validator>>(
            BlockProducerFilters.ValidatorFilter.NotSlashedFilter,
            BlockProducerFilters.ValidatorFilter.HasIdentity
        )

        assertEquals(listOf(kept), listOf(kept, slashed, anonymous).applyFilters(filters))
        assertEquals(3, listOf(kept, slashed, anonymous).applyFilters(emptyList()).size)
    }

    @Test
    fun `filter schema models carry titles and selection`() {
        val models = Filters.values().toList().toFiltersSchema(setOf(Filters.NotSlashedFilter))

        assertEquals(Filters.values().toList(), models.map { it.filter })
        assertEquals(listOf(Filters.NotSlashedFilter), models.filter { it.checked }.map { it.filter })
        assertEquals(
            listOf(
                R.string.staking_recommended_feature_3,
                R.string.staking_recommended_feature_2,
                R.string.staking_recommended_feature_4,
                R.string.select_collator_having_relevant_minimum_bond,
                R.string.common_commission
            ),
            models.map { it.title }
        )
    }

    @Test
    fun `sorting schema models carry titles and selection`() {
        val models = Sorting.values().toList().toSortingSchema(Sorting.Delegations)

        assertEquals(listOf(Sorting.Delegations), models.filter { it.checked }.map { it.sorting })
        assertEquals(
            listOf(
                R.string.staking_custom_validators_settings_sort_apy,
                R.string.staking_validator_total_stake_token,
                R.string.staking_filter_title_own_stake_token,
                R.string.collator_staking_sorting_effective_amount_bonded,
                R.string.collator_staking_sorting_own_stake,
                R.string.collator_staking_sorting_delegations,
                R.string.collator_staking_sorting_minimum_bond
            ),
            models.map { it.title }
        )
    }
}
