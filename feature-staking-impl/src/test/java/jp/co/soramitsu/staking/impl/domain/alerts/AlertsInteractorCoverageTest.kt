package jp.co.soramitsu.staking.impl.domain.alerts

import io.mockk.coEvery
import io.mockk.every
import io.mockk.mockk
import java.math.BigDecimal
import java.math.BigInteger
import jp.co.soramitsu.account.api.domain.interfaces.AccountRepository
import jp.co.soramitsu.account.api.domain.model.MetaAccount
import jp.co.soramitsu.fearless_utils.extensions.toHexString
import jp.co.soramitsu.runtime.state.SingleAssetSharedState
import jp.co.soramitsu.staking.api.data.StakingSharedState
import jp.co.soramitsu.staking.api.domain.model.IndividualExposure
import jp.co.soramitsu.staking.api.domain.model.LegacyExposure
import jp.co.soramitsu.staking.api.domain.model.Nominations
import jp.co.soramitsu.staking.api.domain.model.StakingState
import jp.co.soramitsu.staking.api.domain.model.ValidatorPrefs
import jp.co.soramitsu.staking.impl.StakingLaneFixtures
import jp.co.soramitsu.staking.impl.data.repository.StakingConstantsRepository
import jp.co.soramitsu.staking.impl.scenarios.relaychain.StakingRelayChainScenarioRepository
import jp.co.soramitsu.wallet.impl.domain.interfaces.WalletRepository
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** Bonded 50 and redeemable 5 tokens (StakingLaneFixtures.asset), active era 10. */
class AlertsInteractorCoverageTest {

    private val chainAsset = StakingLaneFixtures.chainAsset()
    private val chain = StakingLaneFixtures.chain(chainAsset)
    private val stashId = ByteArray(32) { 9 }
    private val validatorId = ByteArray(32) { 4 }
    private val validatorHex = validatorId.toHexString()
    private val activeEra = BigInteger.TEN

    private inner class Fixture(
        exposures: Map<String, LegacyExposure>,
        legacySchema: Boolean = true,
        rewardedLimit: Int? = null,
        minimumActiveStake: BigInteger? = BigInteger.ONE
    ) {
        val repository = mockk<StakingRelayChainScenarioRepository>()
        val interactor: AlertsInteractor

        init {
            every { repository.legacyElectedExposuresInActiveEra(chain.id) } returns flowOf(exposures)
            every { repository.observeActiveEraIndex(chain.id) } returns flowOf(activeEra)
            coEvery { repository.minimumNominatorBond(any()) } returns BigInteger.ONE
            coEvery { repository.minimumActiveStake(chain.id) } returns minimumActiveStake
            coEvery { repository.isLegacyErasStakersSchema(chain.id) } returns legacySchema
            coEvery { repository.getRemoteAccountNominations(chain.id, any()) } returns null
            val constants = mockk<StakingConstantsRepository>()
            coEvery { constants.maxRewardedNominatorPerValidator(chain.id) } returns rewardedLimit
            val shared = mockk<StakingSharedState>()
            every { shared.assetWithChain } returns flowOf(SingleAssetSharedState.AssetWithChain(chain, chainAsset))
            val wallet = mockk<MetaAccount>()
            every { wallet.id } returns 7L
            val accounts = mockk<AccountRepository>()
            coEvery { accounts.getSelectedMetaAccount() } returns wallet
            val wallets = mockk<WalletRepository>()
            every { wallets.assetFlow(any(), any(), any(), any()) } returns flowOf(StakingLaneFixtures.asset(chainAsset))
            interactor = AlertsInteractor(repository, constants, shared, wallets, accounts)
        }

        suspend fun alerts(state: StakingState) = interactor.getAlertsFlow(state).first()
    }

    private fun nominator(submittedInEra: Long) = StakingState.Stash.Nominator(
        chain, stashId, stashId, stashId,
        Nominations(listOf(validatorId), BigInteger.valueOf(submittedInEra), false)
    )

    private fun exposure(vararg others: IndividualExposure) =
        mapOf(validatorHex to LegacyExposure(StakingLaneFixtures.planks(1_000), StakingLaneFixtures.planks(500), others.toList()))

    private fun other(seed: Int) = IndividualExposure(ByteArray(32) { seed.toByte() }, StakingLaneFixtures.planks(100))

    @Test
    fun `an inactive settled nomination asks to change validators and to redeem`() = runTest {
        val alerts = Fixture(exposure(other(1))).alerts(nominator(submittedInEra = 5))

        assertEquals(2, alerts.size)
        val redeem = alerts[0] as Alert.RedeemTokens
        assertEquals(0, BigDecimal("5").compareTo(redeem.amount))
        assertEquals(Alert.ChangeValidators, alerts[1])
    }

    @Test
    fun `an inactive nomination of only oversubscribed validators reports oversubscription`() = runTest {
        val alerts = Fixture(exposure(other(1), other(2)), rewardedLimit = 1).alerts(nominator(submittedInEra = 5))

        assertTrue(alerts.contains(Alert.AllValidatorsAreOversubscribed))
        assertFalse(alerts.contains(Alert.ChangeValidators))
    }

    @Test
    fun `an active nomination raises no validator alert`() = runTest {
        val own = IndividualExposure(stashId, StakingLaneFixtures.planks(50))
        val alerts = Fixture(exposure(own, other(1))).alerts(nominator(submittedInEra = 5))

        assertFalse(alerts.contains(Alert.ChangeValidators))
        assertFalse(alerts.contains(Alert.WaitingForNextEra))
        assertEquals(1, alerts.size)
    }

    @Test
    fun `an inactive pending nomination waits for the next era instead of changing validators`() = runTest {
        val alerts = Fixture(exposure(other(1))).alerts(nominator(submittedInEra = 10))

        assertTrue(alerts.contains(Alert.WaitingForNextEra))
        assertFalse(alerts.contains(Alert.ChangeValidators))
    }

    @Test
    fun `current schema waits on any pending nomination and skips unknown remote nominations`() = runTest {
        val f = Fixture(exposure(other(1)), legacySchema = false)

        val pending = f.alerts(nominator(submittedInEra = 11))
        assertTrue(pending.contains(Alert.WaitingForNextEra))
        assertFalse(pending.contains(Alert.ChangeValidators))

        val settled = f.alerts(nominator(submittedInEra = 3))
        assertFalse(settled.contains(Alert.WaitingForNextEra))
        assertFalse(settled.contains(Alert.ChangeValidators))
    }

    @Test
    fun `a bond below the active minimum asks to bond more and hides the waiting alert`() = runTest {
        val f = Fixture(exposure(other(1)), minimumActiveStake = StakingLaneFixtures.planks(60))

        val alerts = f.alerts(nominator(submittedInEra = 10))

        val bondMore = alerts.filterIsInstance<Alert.BondMoreTokens>().single()
        // 60 tokens with the 1.15 safety margin.
        assertEquals(69.0, bondMore.minimalStake.toDouble(), 1e-9)
        assertFalse(alerts.contains(Alert.WaitingForNextEra))
    }

    @Test
    fun `without a chain minimum the lowest active stake decides and an unstaked stash is asked to set validators`() = runTest {
        val lowest = IndividualExposure(ByteArray(32) { 1 }, StakingLaneFixtures.planks(70))
        val f = Fixture(exposure(lowest), minimumActiveStake = null)

        val alerts = f.alerts(StakingState.Stash.None(chain, stashId, stashId, stashId))

        assertTrue(alerts.contains(Alert.SetValidators))
        val bondMore = alerts.filterIsInstance<Alert.BondMoreTokens>().single()
        assertEquals(70 * 1.15, bondMore.minimalStake.toDouble(), 1e-9)
    }

    @Test
    fun `validators are never asked to bond more`() = runTest {
        val f = Fixture(exposure(other(1)), minimumActiveStake = StakingLaneFixtures.planks(60))
        val validator = StakingState.Stash.Validator(chain, stashId, stashId, stashId, ValidatorPrefs(BigDecimal("0.05"), false))

        val alerts = f.alerts(validator)

        assertEquals(1, alerts.size)
        assertTrue(alerts.single() is Alert.RedeemTokens)
    }
}
