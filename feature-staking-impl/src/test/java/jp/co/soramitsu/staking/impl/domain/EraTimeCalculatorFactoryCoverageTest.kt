package jp.co.soramitsu.staking.impl.domain

import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import java.math.BigInteger
import jp.co.soramitsu.staking.impl.scenarios.relaychain.StakingRelayChainScenarioRepository
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Era of 6 sessions of 10 slots, 6 s blocks. Session 20 started at slot 300 (genesis slot 100) and is 4 slots in; the
 * era started at session 18, so 24 of its 60 slots have passed and 36 slots (216 s) remain.
 */
class EraTimeCalculatorFactoryCoverageTest {

    private val remainingMillis = BigInteger.valueOf(216_000)
    private val eraMillis = BigInteger.valueOf(360_000)
    private val sessionMillis = BigInteger.valueOf(60_000)

    private fun calculator(startTimeStamp: BigInteger) = EraTimeCalculator(
        startTimeStamp = startTimeStamp,
        sessionLength = BigInteger.TEN,
        eraLength = BigInteger.valueOf(6),
        blockCreationTime = BigInteger.valueOf(6_000),
        currentSessionIndex = BigInteger.valueOf(20),
        currentSlot = BigInteger.valueOf(304),
        genesisSlot = BigInteger.valueOf(100),
        eraStartSessionIndex = BigInteger.valueOf(18),
        activeEra = BigInteger.valueOf(5)
    )

    private fun assertWithin(expectedBeforeDelta: BigInteger, start: BigInteger, before: Long, after: Long, actual: BigInteger) {
        val lowest = expectedBeforeDelta - (after.toBigInteger() - start)
        val highest = expectedBeforeDelta - (before.toBigInteger() - start)
        assertTrue("$actual not in [$lowest, $highest]", actual in lowest..highest)
    }

    @Test
    fun `remaining time of the active era subtracts the time spent on requests`() {
        val start = BigInteger.ZERO
        val before = System.currentTimeMillis()
        val result = calculator(start).calculate()
        val after = System.currentTimeMillis()
        assertWithin(remainingMillis, start, before, after, result)
    }

    @Test
    fun `a later destination era adds the full eras left in between`() {
        val start = BigInteger.ZERO
        val before = System.currentTimeMillis()
        // Era 8 starts after the active era 5 ends and eras 6 and 7 pass.
        val result = calculator(start).calculate(BigInteger.valueOf(8))
        val after = System.currentTimeMillis()
        assertWithin(remainingMillis + eraMillis * BigInteger.TWO, start, before, after, result)
    }

    @Test
    fun `time till an era is set stops one session before the era starts`() {
        val start = BigInteger.ZERO
        val before = System.currentTimeMillis()
        val result = calculator(start).calculateTillEraSet(BigInteger.valueOf(6))
        val after = System.currentTimeMillis()
        assertWithin(remainingMillis - sessionMillis, start, before, after, result)
    }

    @Test
    fun `factory reads every relay clock value for the chain and the active era`() = runTest {
        val chainId = "relay"
        val repository = mockk<StakingRelayChainScenarioRepository>()
        coEvery { repository.sessionLength(chainId) } returns BigInteger.TEN
        coEvery { repository.eraLength(chainId) } returns BigInteger.valueOf(6)
        coEvery { repository.blockCreationTime(chainId) } returns BigInteger.valueOf(6_000)
        coEvery { repository.currentSessionIndex(chainId) } returns BigInteger.valueOf(20)
        coEvery { repository.currentSlot(chainId) } returns BigInteger.valueOf(304)
        coEvery { repository.genesisSlot(chainId) } returns BigInteger.valueOf(100)
        coEvery { repository.getActiveEraIndex(chainId) } returns BigInteger.valueOf(5)
        coEvery { repository.eraStartSessionIndex(chainId, BigInteger.valueOf(5)) } returns BigInteger.valueOf(18)

        val before = System.currentTimeMillis()
        val result = EraTimeCalculatorFactory(repository).create(chainId).calculate()
        val after = System.currentTimeMillis()

        val elapsed = (after - before).toBigInteger()
        assertTrue(result <= remainingMillis)
        assertTrue(result >= remainingMillis - elapsed)
        coVerify(exactly = 1) { repository.eraStartSessionIndex(chainId, BigInteger.valueOf(5)) }
    }
}
