package jp.co.soramitsu.staking.impl.domain.recommendations.settings.sortings

import java.math.BigDecimal
import java.math.BigInteger
import jp.co.soramitsu.staking.api.domain.model.CandidateCapacity
import jp.co.soramitsu.staking.api.domain.model.CandidateInfoStatus
import jp.co.soramitsu.staking.api.domain.model.Collator
import jp.co.soramitsu.staking.api.domain.model.Validator
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** Every validator and collator sorting orders the largest value first. */
class BlockProducersSortingCoverageTest {

    private fun validator(name: String, total: Long, own: Long, apy: String) = Validator(
        address = name,
        slashed = false,
        accountIdHex = name,
        prefs = null,
        electedInfo = Validator.ElectedInfo(BigInteger.valueOf(total), BigInteger.valueOf(own), emptyList(), BigDecimal(apy), false),
        identity = null
    )

    private fun collator(name: String, value: Long, apy: BigDecimal?) = Collator(
        address = name,
        bond = BigInteger.valueOf(value),
        delegationCount = BigInteger.valueOf(value * 2),
        totalCounted = BigInteger.valueOf(value * 3),
        lowestTopDelegationAmount = BigInteger.valueOf(value * 4),
        highestBottomDelegationAmount = BigInteger.ONE,
        lowestBottomDelegationAmount = BigInteger.ONE,
        topCapacity = CandidateCapacity.Partial,
        bottomCapacity = CandidateCapacity.Empty,
        request = null,
        status = CandidateInfoStatus.ACTIVE,
        identity = null,
        apy = apy
    )

    private val a = validator("a", total = 100, own = 1, apy = "0.10")
    private val b = validator("b", total = 300, own = 3, apy = "0.05")
    private val c = validator("c", total = 200, own = 2, apy = "0.20")

    private fun List<Validator>.orderBy(sorting: BlockProducersSorting<Validator>) = sortedWith(sorting.comparator).map { it.address }

    @Test
    fun `validator sortings order by apy, total stake and own stake`() {
        val validators = listOf(a, b, c)

        assertEquals(listOf("c", "a", "b"), validators.orderBy(BlockProducersSorting.ValidatorSorting.APYSorting))
        assertEquals(listOf("b", "c", "a"), validators.orderBy(BlockProducersSorting.ValidatorSorting.TotalStakeSorting))
        assertEquals(listOf("b", "c", "a"), validators.orderBy(BlockProducersSorting.ValidatorSorting.ValidatorOwnStakeSorting))
    }

    @Test
    fun `sorting a validator that is not elected fails`() {
        val notElected = Validator("n", false, "n", null, null, null)
        for (sorting in listOf(
            BlockProducersSorting.ValidatorSorting.APYSorting,
            BlockProducersSorting.ValidatorSorting.TotalStakeSorting,
            BlockProducersSorting.ValidatorSorting.ValidatorOwnStakeSorting
        )) {
            val failure = runCatching { listOf(a, notElected).sortedWith(sorting.comparator) }.exceptionOrNull()
            assertTrue(failure is IllegalStateException)
        }
    }

    @Test
    fun `collator sortings order by their own value`() {
        val small = collator("small", 1, BigDecimal("0.3"))
        val large = collator("large", 5, null)
        val medium = collator("medium", 3, BigDecimal("0.1"))
        val collators = listOf(small, large, medium)
        val byValue = listOf("large", "medium", "small")

        for (sorting in listOf(
            BlockProducersSorting.CollatorSorting.CollatorsOwnStakeSorting,
            BlockProducersSorting.CollatorSorting.DelegationsSorting,
            BlockProducersSorting.CollatorSorting.EffectiveAmountBondedSorting,
            BlockProducersSorting.CollatorSorting.MinimumBondSorting
        )) {
            assertEquals(byValue, collators.sortedWith(sorting.comparator).map { it.address })
        }
        // A collator without an APY sorts as zero.
        assertEquals(listOf("small", "medium", "large"), collators.sortedWith(BlockProducersSorting.CollatorSorting.APYSorting.comparator).map { it.address })
    }
}
