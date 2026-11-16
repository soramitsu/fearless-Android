package jp.co.soramitsu.staking.impl.domain.validations.payout

import java.math.BigDecimal
import jp.co.soramitsu.common.validation.DefaultFailureLevel
import jp.co.soramitsu.common.validation.ValidationStatus
import jp.co.soramitsu.staking.impl.StakingLaneFixtures
import jp.co.soramitsu.wallet.impl.domain.model.Token
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test

class ProfitablePayoutValidationCoverageTest {

    private val validation = ProfitablePayoutValidation()
    private val xorAsset = StakingLaneFixtures.chainAsset(symbol = "XOR", precision = 18)
    private val valAsset = StakingLaneFixtures.chainAsset(symbol = "VAL", precision = 18)

    private fun plain(fee: String, reward: String) =
        MakePayoutPayload("origin", BigDecimal(fee), BigDecimal(reward), StakingLaneFixtures.token(), emptyList())

    private fun sora(fee: String, reward: String, utility: Token, rewardToken: Token) =
        SoraPayoutsPayload("origin", BigDecimal(fee), BigDecimal(reward), utility, rewardToken, emptyList())

    private fun assertUnprofitable(status: ValidationStatus<PayoutValidationFailure>) {
        val notValid = status as ValidationStatus.NotValid
        assertEquals(DefaultFailureLevel.WARNING, notValid.level)
        assertSame(PayoutValidationFailure.UnprofitablePayout, notValid.reason)
    }

    @Test
    fun `a payout is profitable when its fee is below the reward`() = runTest {
        assertTrue(validation.validate(plain("1", "10")) is ValidationStatus.Valid)
    }

    @Test
    fun `a payout whose fee reaches the reward is a warning`() = runTest {
        assertUnprofitable(validation.validate(plain("10", "10")))
        assertUnprofitable(validation.validate(plain("11", "10")))
    }

    @Test
    fun `sora payouts compare fee and reward in fiat`() = runTest {
        val utility = StakingLaneFixtures.token(xorAsset, fiatRate = BigDecimal("2"))
        val reward = StakingLaneFixtures.token(valAsset, fiatRate = BigDecimal("1"))

        // Fee 1 XOR is 2 fiat, reward 3 VAL is 3 fiat.
        assertTrue(validation.validate(sora("1", "3", utility, reward)) is ValidationStatus.Valid)
        // Fee 2 XOR is 4 fiat, reward 3 VAL is 3 fiat: a token-amount comparison would have passed.
        assertUnprofitable(validation.validate(sora("2", "3", utility, reward)))
    }

    @Test
    fun `sora payouts without prices are allowed`() = runTest {
        val utility = StakingLaneFixtures.token(xorAsset, fiatRate = BigDecimal("2"))
        val unpriced = StakingLaneFixtures.token(valAsset)

        assertTrue(validation.validate(sora("100", "1", utility, unpriced)) is ValidationStatus.Valid)
        assertTrue(validation.validate(sora("100", "1", unpriced, utility)) is ValidationStatus.Valid)
    }
}
