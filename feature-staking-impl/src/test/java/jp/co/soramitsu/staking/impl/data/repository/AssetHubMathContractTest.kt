package jp.co.soramitsu.staking.impl.data.repository

import jp.co.soramitsu.staking.impl.data.network.blockhain.bindings.bondedEraSession
import jp.co.soramitsu.staking.impl.domain.rewards.ManualRewardCalculator
import jp.co.soramitsu.staking.impl.domain.rewards.RewardCalculationTarget
import jp.co.soramitsu.staking.impl.domain.rewards.annualStakingRewards
import jp.co.soramitsu.staking.impl.scenarios.relaychain.assetHubUnbondingDuration
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.math.BigDecimal
import java.math.BigInteger

class AssetHubMathContractTest {
    @Test fun `pure nonslashable nominators use fast unbonding while recent validators retain full duration`() {
        val full = 28.toBigInteger()
        val fast = 2.toBigInteger()
        val active = 100.toBigInteger()
        assertEquals(fast, assetHubUnbondingDuration(full, fast, false, active, null))
        assertEquals(full, assetHubUnbondingDuration(full, fast, true, active, null))
        assertEquals(full, assetHubUnbondingDuration(full, fast, false, active, 73.toBigInteger()))
        assertEquals(fast, assetHubUnbondingDuration(full, fast, false, active, 72.toBigInteger()))
        assertEquals(full, assetHubUnbondingDuration(full, fast, false, active, 101.toBigInteger()))
    }

    @Test fun `invalid unbonding constants are rejected`() {
        assertTrue(
            runCatching {
            assetHubUnbondingDuration(2.toBigInteger(), 28.toBigInteger(), false, BigInteger.ONE, null)
        }.isFailure
        )
        assertTrue(
            runCatching {
            assetHubUnbondingDuration(BigInteger.ZERO, BigInteger.ZERO, false, BigInteger.ONE, null)
        }.isFailure
        )
    }

    @Test fun `bonded era lookup rejects absent duplicate and malformed entries`() {
        val era = BigInteger.TEN
        assertEquals(60.toBigInteger(), bondedEraSession(listOf(listOf(era, 60.toBigInteger())), era))
        listOf(emptyList<Any>(), listOf(listOf(era)), listOf(listOf(era, BigInteger.ONE), listOf(era, BigInteger.TWO))).forEach {
            assertTrue(runCatching { bondedEraSession(it, era) }.isFailure)
        }
    }

    @Test fun `reward annualization uses actual era period for Polkadot and Kusama`() {
        val rewards = listOf(80.toBigInteger(), 120.toBigInteger())
        assertEquals(0, annualStakingRewards(rewards, 86_400_000.toBigInteger()).compareTo(BigDecimal("36500")))
        assertEquals(0, annualStakingRewards(rewards, 21_600_000.toBigInteger()).compareTo(BigDecimal("146000")))
        assertTrue(runCatching { annualStakingRewards(emptyList(), BigInteger.ONE) }.isFailure)
        assertTrue(runCatching { annualStakingRewards(rewards, BigInteger.ZERO) }.isFailure)
        assertTrue(runCatching { annualStakingRewards(listOf((-1).toBigInteger()), BigInteger.ONE) }.isFailure)
    }

    @Test fun `observed reward budgets override obsolete inflation and retain commission`() = runTest {
        val target = RewardCalculationTarget("11", 1000.toBigInteger(), BigInteger.ZERO, emptyList(), BigDecimal("0.1"))
        val first = ManualRewardCalculator(listOf(target), BigInteger.ONE, annualStakerRewards = BigDecimal("100"))
        val second = ManualRewardCalculator(listOf(target), BigInteger.TEN.pow(30), annualStakerRewards = BigDecimal("100"))
        assertEquals(0, first.calculateAvgAPY().compareTo(BigDecimal("9")))
        assertEquals(first.calculateAvgAPY(), second.calculateAvgAPY())
        // The shared estimate API converts the annual rate to a Double daily rate.
        val gain = first.calculateReturns(BigDecimal("100"), 365, false, "hub").gainAmount
        assertTrue(gain.subtract(BigDecimal("9")).abs() < BigDecimal("0.000000000001"))
    }

    @Test fun `Perbill share follows two stage SCALE runtime rounding`() {
        assertEquals(2.toBigInteger(), assetHubPerbillShare(10.toBigInteger(), BigInteger.ONE, 4.toBigInteger()))
        assertEquals(BigInteger.ZERO, assetHubPerbillShare(3.toBigInteger(), BigInteger.ONE, 3.toBigInteger()))
        assertEquals(BigInteger.ZERO, assetHubPerbillShare(3.toBigInteger(), BigInteger.ZERO, BigInteger.ZERO))
        assertTrue(runCatching { assetHubPerbillShare(BigInteger.ONE, 4.toBigInteger(), 3.toBigInteger()) }.isFailure)
        assertTrue(runCatching { assetHubPerbillShare((-1).toBigInteger(), BigInteger.ONE, BigInteger.ONE) }.isFailure)
    }

    @Test fun `unsupported exposure key layouts cannot be mistaken for the selected wallet`() {
        assertTrue(runCatching { assetHubExposureKey("0x" + "00".repeat(95)) }.isFailure)
        assertTrue(runCatching { assetHubExposureKey("0x" + "00".repeat(97)) }.isFailure)
    }
}
