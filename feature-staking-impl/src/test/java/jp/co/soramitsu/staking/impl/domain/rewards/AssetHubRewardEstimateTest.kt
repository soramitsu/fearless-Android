package jp.co.soramitsu.staking.impl.domain.rewards

import jp.co.soramitsu.core.models.Asset
import jp.co.soramitsu.core.models.Ecosystem
import jp.co.soramitsu.fearless_utils.extensions.toHexString
import jp.co.soramitsu.runtime.assethub.AssetHubStaking
import jp.co.soramitsu.runtime.multiNetwork.chain.model.Chain
import jp.co.soramitsu.staking.api.domain.api.IdentityRepository
import jp.co.soramitsu.staking.api.domain.api.StakingRepository
import jp.co.soramitsu.staking.api.domain.model.LegacyExposure
import jp.co.soramitsu.staking.api.domain.model.ValidatorPrefs
import jp.co.soramitsu.staking.impl.data.repository.StakingConstantsRepository
import jp.co.soramitsu.staking.impl.domain.validators.ValidatorProvider
import jp.co.soramitsu.staking.impl.domain.validators.ValidatorSource
import jp.co.soramitsu.staking.impl.scenarios.relaychain.StakingRelayChainScenarioRepository
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.BeforeClass
import org.junit.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.doReturn
import org.mockito.kotlin.mock
import org.mockito.kotlin.never
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever
import java.math.BigDecimal
import java.math.BigInteger

/**
 * An Asset Hub estimate annualizes the observed reward budget of completed eras. The estimate is display only:
 * reward history that cannot be read gives a zero estimate, never a failed staking or validator screen.
 */
class AssetHubRewardEstimateTest {
    private val validator = ByteArray(32) { 7 }
    private val target = RewardCalculationTarget(
        accountIdHex = validator.toHexString(),
        totalStake = BigInteger.valueOf(1000),
        nominatorStakes = emptyList(),
        ownStake = BigInteger.valueOf(1000),
        commission = BigDecimal("0.1")
    )

    private inner class Fixture(val chainId: String = AssetHubStaking.POLKADOT) {
        val relay = mock<StakingRelayChainScenarioRepository>()
        val staking = mock<StakingRepository>()
        val factory = RewardCalculatorFactory(relay, staking, mock(), mock(), mock())

        suspend fun setUp() {
            // A 6-hour era on the relay clock: 600 blocks per session, 6 sessions, 6 s blocks.
            whenever(relay.sessionLength(chainId)).thenReturn(600.toBigInteger())
            whenever(relay.eraLength(chainId)).thenReturn(6.toBigInteger())
            whenever(relay.blockCreationTime(chainId)).thenReturn(6000.toBigInteger())
            whenever(relay.getActiveEraIndex(chainId)).thenReturn(10.toBigInteger())
            // The active era 10 is not complete and must not count.
            whenever(relay.getErasValidatorRewards(chainId)).thenReturn(
                mapOf(8.toBigInteger() to BigInteger.ONE, 9.toBigInteger() to 3.toBigInteger(), 10.toBigInteger() to 1000.toBigInteger())
            )
        }

        suspend fun calculator() = factory.createManual(chainId, listOf(target))
    }

    @Test
    fun `completed era rewards are annualized on the relay clock`() = runTest {
        val f = Fixture().apply { setUp() }
        // Mean budget 2 per 6-hour era is 2920 a year; 2920 / 1000 staked, less 10% commission.
        assertEquals(2.628, f.calculator().getApyFor(validator).toDouble(), 1e-9)
        verify(f.staking, never()).getTotalIssuance(any())
    }

    @Test
    fun `unreadable reward history gives a zero estimate`() = runTest {
        val f = Fixture(AssetHubStaking.KUSAMA).apply { setUp() }
        whenever(f.relay.getErasValidatorRewards(AssetHubStaking.KUSAMA)).thenThrow(IllegalStateException("node unreachable"))
        val calculator = f.calculator()
        assertEquals(0, calculator.getApyFor(validator).signum())
        assertEquals(0, calculator.calculateAvgAPY().signum())
        assertEquals(0, calculator.calculateReturns(BigDecimal.TEN, 365, true, AssetHubStaking.KUSAMA).gainAmount.signum())
        verify(f.staking, never()).getTotalIssuance(any())
    }

    @Test
    fun `history without a completed era gives a zero estimate`() = runTest {
        val f = Fixture().apply { setUp() }
        whenever(f.relay.getErasValidatorRewards(AssetHubStaking.POLKADOT)).thenReturn(mapOf(10.toBigInteger() to 1000.toBigInteger()))
        assertEquals(0, f.calculator().getApyFor(validator).signum())
    }

    @Test
    fun `cancelled reward history stays cancelled`() = runTest {
        val f = Fixture().apply { setUp() }
        whenever(f.relay.getErasValidatorRewards(AssetHubStaking.POLKADOT)).thenAnswer { throw CancellationException("left") }
        assertTrue(runCatching { f.calculator() }.exceptionOrNull() is CancellationException)
    }

    @Test
    fun `validators still load when Hub reward history cannot be read`() = runTest {
        val f = Fixture().apply { setUp() }
        whenever(f.relay.getErasValidatorRewards(AssetHubStaking.POLKADOT)).thenThrow(IllegalStateException("node unreachable"))
        val hex = validator.toHexString()
        whenever(f.relay.getAllValidatorPrefs(AssetHubStaking.POLKADOT)).thenReturn(mapOf(hex to ValidatorPrefs(BigDecimal("0.1"), false)))
        whenever(f.relay.getLegacyActiveElectedValidatorsExposures(AssetHubStaking.POLKADOT))
            .thenReturn(mapOf(hex to LegacyExposure(BigInteger.valueOf(1000), BigInteger.valueOf(1000), emptyList())))
        whenever(f.relay.getSlashes(any(), any())).thenReturn(emptyMap())
        val identities = mock<IdentityRepository>()
        whenever(identities.getIdentitiesFromIds(any(), any())).thenReturn(emptyMap())
        val constants = mock<StakingConstantsRepository>()
        whenever(constants.maxRewardedNominatorPerValidator(AssetHubStaking.POLKADOT)).thenReturn(512)
        val native = mock<Asset> {
            on { id } doReturn "native"
            on { isUtility } doReturn true
        }
        val chain = mock<Chain> {
            on { id } doReturn AssetHubStaking.POLKADOT
            on { addressPrefix } doReturn 0
            on { ecosystem } doReturn Ecosystem.Substrate
            on { assets } doReturn listOf(native)
        }

        val validators = ValidatorProvider(f.relay, identities, f.factory, constants).getValidators(chain, ValidatorSource.Elected)

        assertEquals(hex, validators.single().accountIdHex)
        assertEquals(0, validators.single().electedInfo!!.apy.signum())
    }

    @Test
    fun `other networks keep the issuance model`() = runTest {
        val f = Fixture("validator-contract")
        whenever(f.staking.getTotalIssuance("validator-contract")).thenReturn(BigInteger.valueOf(4000))
        assertTrue(f.calculator().getApyFor(validator).signum() > 0)
        verify(f.relay, never()).getErasValidatorRewards(any())
    }

    private companion object {
        @JvmStatic
        @BeforeClass
        fun enableByteBuddyForJava21TestRuntime() {
            System.setProperty("net.bytebuddy.experimental", "true")
        }
    }
}
