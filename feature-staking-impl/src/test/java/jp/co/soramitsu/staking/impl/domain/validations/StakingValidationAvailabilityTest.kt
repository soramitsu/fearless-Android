package jp.co.soramitsu.staking.impl.domain.validations

import java.math.BigDecimal
import java.math.BigInteger
import jp.co.soramitsu.core.models.Asset as ChainAsset
import jp.co.soramitsu.common.validation.ValidationStatus
import jp.co.soramitsu.staking.impl.domain.model.NetworkInfo
import jp.co.soramitsu.staking.impl.domain.validations.setup.*
import jp.co.soramitsu.staking.impl.domain.validations.unbond.ControllerCanPayFeeValidation
import jp.co.soramitsu.staking.impl.scenarios.StakingScenarioInteractor
import jp.co.soramitsu.wallet.impl.domain.model.Asset
import jp.co.soramitsu.wallet.impl.domain.model.AssetAmountState
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import org.junit.Assert.*
import org.junit.Test
import org.mockito.kotlin.*

class StakingValidationAvailabilityTest {
    private val config = mock<ChainAsset> {
        on { chainId } doReturn "relay"
        on { id } doReturn "native"
        on { precision } doReturn 0
    }
    private fun payload(bonded: AssetAmountState<BigInteger>, entered: BigDecimal? = null) = SetupStakingPayload(
        entered, BigDecimal.ZERO,
        Asset.createEmpty(config, 7, byteArrayOf(1), minSupportedVersion = null).copy(bondedInPlanksState = bonded),
        "controller", true
    )
    private fun scenario(minimum: BigInteger): StakingScenarioInteractor {
        val info = mock<NetworkInfo.RelayChain> {
            on { shouldUseMinimumStakeMultiplier } doReturn false
        }
        return mock {
            onBlocking { observeNetworkInfoState() } doReturn flowOf(info)
            onBlocking { getMinimumStake(config) } doReturn minimum
        }
    }

    @Test fun unknownExistingBondNeverPassesEvenWhenTheMinimumCouldBeZero() = runTest {
        val scenario = mock<StakingScenarioInteractor>()
        for (state in listOf(AssetAmountState.Missing, AssetAmountState.Unavailable("damaged"))) {
            val result = MinimumAmountValidation(scenario).validate(payload(state))
            assertTrue(result is ValidationStatus.NotValid)
            assertEquals(SetupStakingValidationFailure.BalanceUnavailable, (result as ValidationStatus.NotValid).reason)
        }
        verifyNoInteractions(scenario)
    }

    @Test fun knownZeroAndExplicitNewBondKeepTheirMinimumThresholdBehavior() = runTest {
        val zeroMinimum = scenario(BigInteger.ZERO)
        assertTrue(MinimumAmountValidation(zeroMinimum).validate(payload(AssetAmountState.Known(BigInteger.ZERO))) is ValidationStatus.Valid)
        val minimum = scenario(BigInteger.TEN)
        assertTrue(MinimumAmountValidation(minimum).validate(payload(AssetAmountState.Missing, BigDecimal.TEN)) is ValidationStatus.Valid)
        val tooSmall = MinimumAmountValidation(minimum).validate(payload(AssetAmountState.Known(BigInteger.ONE)))
        assertTrue(tooSmall is ValidationStatus.NotValid)
        assertTrue((tooSmall as ValidationStatus.NotValid).reason is SetupStakingValidationFailure.TooSmallAmount)
    }

    @Test fun minimumLookupCancellationRetainsTheOriginalReason() = runTest {
        val scenario = scenario(BigInteger.ZERO)
        val reason = CancellationException("selection changed")
        whenever(scenario.getMinimumStake(config)).thenThrow(reason)
        assertSame(reason, runCatching {
            MinimumAmountValidation(scenario).validate(payload(AssetAmountState.Known(BigInteger.TEN)))
        }.exceptionOrNull())
    }

    @Test fun missingControllerBalanceCannotAuthorizeEvenAZeroFee() = runTest {
        val validation = ControllerCanPayFeeValidation<Unit, String>({ BigDecimal.ZERO }, { null }, { "unavailable" })
        val result = validation.validate(Unit)
        assertTrue(result is ValidationStatus.NotValid)
        assertEquals("unavailable", (result as ValidationStatus.NotValid).reason)
    }

    @Test fun controllerFeeComparisonRetainsItsStrictKnownBalanceBoundary() = runTest {
        for ((available, fee, valid) in listOf(
            Triple(BigDecimal.ZERO, BigDecimal.ZERO, false),
            Triple(BigDecimal.ONE, BigDecimal.ONE, false),
            Triple(BigDecimal.TEN, BigDecimal.ONE, true),
            Triple(BigDecimal.ONE, BigDecimal("-1"), false)
        )) {
            val validation = ControllerCanPayFeeValidation<Unit, String>({ fee }, { available }, { "cannot-pay" })
            assertEquals(valid, validation.validate(Unit) is ValidationStatus.Valid)
        }
    }

    @Test fun controllerLookupFailureAndCancellationAreNotChangedToNumericZero() = runTest {
        for (failure in listOf(IllegalStateException("missing read"), CancellationException("selection changed"))) {
            val validation = ControllerCanPayFeeValidation<Unit, String>({ BigDecimal.ZERO }, { throw failure }, { "cannot-pay" })
            assertSame(failure, runCatching { validation.validate(Unit) }.exceptionOrNull())
        }
    }
}
