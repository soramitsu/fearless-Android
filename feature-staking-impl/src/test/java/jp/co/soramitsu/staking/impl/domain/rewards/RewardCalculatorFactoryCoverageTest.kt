package jp.co.soramitsu.staking.impl.domain.rewards

import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import java.math.BigDecimal
import java.math.BigInteger
import jp.co.soramitsu.core.models.Asset
import jp.co.soramitsu.fearless_utils.extensions.toHexString
import jp.co.soramitsu.runtime.multiNetwork.chain.model.reefChainId
import jp.co.soramitsu.runtime.multiNetwork.chain.model.soraMainChainId
import jp.co.soramitsu.runtime.multiNetwork.chain.model.ternoaChainId
import jp.co.soramitsu.staking.api.domain.api.StakingRepository
import jp.co.soramitsu.staking.api.domain.model.LegacyExposure
import jp.co.soramitsu.staking.api.domain.model.ValidatorPrefs
import jp.co.soramitsu.staking.impl.StakingLaneFixtures
import jp.co.soramitsu.staking.impl.data.network.blockhain.bindings.EraRewardPoints
import jp.co.soramitsu.staking.impl.scenarios.relaychain.StakingRelayChainScenarioRepository
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test

class RewardCalculatorFactoryCoverageTest {

    private val first = ByteArray(32) { 1 }
    private val second = ByteArray(32) { 2 }
    private val withoutPrefs = ByteArray(32) { 3 }
    private val firstHex = first.toHexString()
    private val secondHex = second.toHexString()

    private inner class Fixture(chainId: String) {
        val relay = mockk<StakingRelayChainScenarioRepository>()
        val staking = mockk<StakingRepository>()
        val sora = mockk<SoraStakingRewardsScenario>()
        val factory = RewardCalculatorFactory(relay, staking, sora, mockk(), mockk())

        init {
            coEvery { relay.getLegacyActiveElectedValidatorsExposures(chainId) } returns mapOf(
                firstHex to LegacyExposure(BigInteger.valueOf(1_000), BigInteger.valueOf(400), emptyList()),
                secondHex to LegacyExposure(BigInteger.valueOf(2_000), BigInteger.valueOf(500), emptyList()),
                withoutPrefs.toHexString() to LegacyExposure(BigInteger.valueOf(3_000), BigInteger.valueOf(600), emptyList())
            )
            coEvery { relay.getValidatorPrefs(chainId, any()) } returns mapOf(
                firstHex to ValidatorPrefs(BigDecimal("0.1"), false),
                secondHex to ValidatorPrefs(BigDecimal("0.2"), false),
                withoutPrefs.toHexString() to null
            )
            coEvery { relay.getErasValidatorRewards(chainId) } returns mapOf(
                BigInteger.ONE to BigInteger.valueOf(20),
                BigInteger.TWO to null
            )
            coEvery { relay.retrieveEraPointsDistribution(chainId) } returns mapOf(
                BigInteger.ONE to EraRewardPoints(
                    BigInteger.valueOf(100),
                    listOf(EraRewardPoints.Individual(first, BigInteger.valueOf(60)), EraRewardPoints.Individual(second, BigInteger.valueOf(40)))
                )
            )
            coEvery { staking.getTotalIssuance(chainId) } returns BigInteger.valueOf(100_000)
            coEvery { sora.mainAssetToRewardAssetRate() } returns BigInteger.valueOf(2)
        }
    }

    private fun asset(chainId: String, staking: Asset.StakingType = Asset.StakingType.RELAYCHAIN) =
        StakingLaneFixtures.chainAsset(chainId = chainId, symbol = "UNIT", precision = 0, staking = staking)

    @Test
    fun `relay chain targets pair elected exposures with prefs and skip validators without prefs`() = runTest {
        val chainId = "relay-chain"
        val f = Fixture(chainId)
        val calculator = f.factory.create(asset(chainId)) as ManualRewardCalculator
        val targets = calculator.validators.associateBy { it.accountIdHex }
        assertEquals(setOf(firstHex, secondHex), targets.keys)
        assertEquals(BigInteger.valueOf(1_000), targets.getValue(firstHex).totalStake)
        assertEquals(BigInteger.valueOf(400), targets.getValue(firstHex).ownStake)
        assertEquals(BigDecimal("0.2"), targets.getValue(secondHex).commission)
        assertEquals(BigInteger.valueOf(100_000), calculator.totalIssuance)
        coVerify { f.relay.getValidatorPrefs(chainId, match { it.size == 3 }) }
    }

    @Test
    fun `relay chain with explicit validators still builds a manual calculator from all elected targets`() = runTest {
        val chainId = "relay-chain"
        val f = Fixture(chainId)
        val calculator = f.factory.createWithValidators(asset(chainId), listOf(first)) as ManualRewardCalculator
        assertEquals(2, calculator.validators.size)
    }

    @Test
    fun `parachain assets use the shared subquery calculator`() = runTest {
        val chainId = "para-chain"
        val f = Fixture(chainId)
        val parachain = asset(chainId, Asset.StakingType.PARACHAIN)
        assertSame(f.factory.createSubquery(), f.factory.create(parachain))
        assertSame(f.factory.createSubquery(), f.factory.createWithValidators(parachain, listOf(first)))
    }

    @Test
    fun `unsupported staking assets fail for both factory entries`() = runTest {
        val chainId = "plain-chain"
        val f = Fixture(chainId)
        val unsupported = asset(chainId, Asset.StakingType.UNSUPPORTED)
        assertTrue(runCatching { f.factory.create(unsupported) }.exceptionOrNull() is IllegalStateException)
        assertTrue(runCatching { f.factory.createWithValidators(unsupported, emptyList()) }.exceptionOrNull() is IllegalStateException)
    }

    @Test
    fun `ternoa assets average the present era payouts`() = runTest {
        val f = Fixture(ternoaChainId)
        val created = f.factory.create(asset(ternoaChainId))
        val withValidators = f.factory.createWithValidators(asset(ternoaChainId), listOf(first))
        assertTrue(created is TernoaRewardCalculator)
        assertTrue(withValidators is TernoaRewardCalculator)
        // Payout 20 shared by two validators: the first earns 10 / 1000 a day less 10% commission.
        val expected = (Math.pow(1.009, 365.0) - 1) * 100
        assertEquals(expected, created.calculateMaxAPY(ternoaChainId).toDouble(), 1e-6)
    }

    @Test
    fun `ternoa calculator refuses other chains`() = runTest {
        val f = Fixture("relay-chain")
        val failure = runCatching { f.factory.createTernoa(asset("relay-chain"), emptyList()) }.exceptionOrNull()
        assertTrue(failure is IllegalArgumentException)
    }

    @Test
    fun `reef assets read era payouts and reward points`() = runTest {
        val f = Fixture(reefChainId)
        val created = f.factory.create(asset(reefChainId))
        val withValidators = f.factory.createWithValidators(asset(reefChainId), listOf(second))
        assertTrue(created is ReefRewardCalculator)
        assertTrue(withValidators is ReefRewardCalculator)
        coVerify(exactly = 2) { f.relay.retrieveEraPointsDistribution(reefChainId) }
        coVerify(exactly = 2) { f.relay.getErasValidatorRewards(reefChainId) }
    }

    @Test
    fun `explicit reef calculation targets are kept`() = runTest {
        val f = Fixture(reefChainId)
        val target = RewardCalculationTarget(firstHex, BigInteger.valueOf(1_000), BigInteger.valueOf(400), emptyList(), BigDecimal.ZERO)
        val calculator = f.factory.createReef(asset(reefChainId), listOf(target), listOf(firstHex))
        // One unit staked with the first validator: 60 of 100 points of a payout of 20, over 1000 staked, yearly.
        val expected = 20.0 * 0.6 / 1_000 * DAYS_IN_YEAR
        assertEquals(expected, calculator.getApyFor(first).toDouble(), 1e-9)
    }

    @Test
    fun `sora validator selection becomes the calculation targets`() = runTest {
        val f = Fixture(soraMainChainId)
        val calculator = f.factory.createWithValidators(asset(soraMainChainId), listOf(second))
        assertTrue(calculator is SoraRewardCalculator)
        coVerify(exactly = 1) { f.sora.mainAssetToRewardAssetRate() }
        coVerify(exactly = 1) { f.relay.retrieveEraPointsDistribution(soraMainChainId) }
    }
}
