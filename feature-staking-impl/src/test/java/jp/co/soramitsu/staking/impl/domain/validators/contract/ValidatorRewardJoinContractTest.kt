package jp.co.soramitsu.staking.impl.domain.validators.contract

import jp.co.soramitsu.staking.impl.domain.rewards.RewardCalculationTarget
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.argumentCaptor
import org.mockito.kotlin.eq
import org.mockito.kotlin.isNull
import org.mockito.kotlin.times
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever
import java.math.BigDecimal

class ValidatorRewardJoinContractTest {
    @Test fun oneBeyondRewardedNominatorCapacityIsOversubscribed() = runTest {
        val f = ValidatorCatalogueFixture()
        f.defaults()
        whenever(f.constants.maxRewardedNominatorPerValidator(f.chainId)).thenReturn(1)
        assertTrue(requireNotNull(f.elected().first().electedInfo).isOversubscribed)
    }

    @Test fun unknownRewardedNominatorCapacityDoesNotInventOversubscription() = runTest {
        val f = ValidatorCatalogueFixture()
        f.defaults()
        whenever(f.constants.maxRewardedNominatorPerValidator(f.chainId)).thenReturn(null)
        assertFalse(requireNotNull(f.elected().first().electedInfo).isOversubscribed)
    }

    @Test fun unexposedValidatorHasNoFabricatedElectionOrApy() = runTest {
        val f = ValidatorCatalogueFixture()
        f.defaults()
        assertNull(f.elected()[1].electedInfo)
        verify(f.calculator, times(1)).getApyFor(any())
    }

    @Test fun ordinaryApyFailureFallsBackToZeroForOnlyTheAffectedValidator() = runTest {
        val f = ValidatorCatalogueFixture()
        f.defaults()
        whenever(f.calculator.getApyFor(any())).thenThrow(IllegalStateException("missing reward"))
        val actual = f.elected()
        assertEquals(BigDecimal.ZERO, actual[0].electedInfo?.apy)
        assertEquals(2, actual.size)
        assertSame(f.prefs, actual[0].prefs)
    }

    @Test fun cancelledApyCannotBecomeASuccessfulZeroRewardCatalogue() = runTest {
        val f = ValidatorCatalogueFixture()
        f.defaults()
        val failure = CancellationException("left validator screen")
        whenever(f.calculator.getApyFor(any())).thenThrow(failure)
        assertCatalogueFailure(failure, runCatching { f.elected() }.exceptionOrNull())
    }

    @Test fun calculationTargetsContainOnlyExposedValidatorsWithKnownPreferences() = runTest {
        val f = ValidatorCatalogueFixture()
        f.defaults()
        whenever(f.repository.getLegacyActiveElectedValidatorsExposures(f.chainId))
            .thenReturn(linkedMapOf(f.hex[0] to f.exposure, f.hex[2] to f.exposure))
        f.elected()
        val targets = argumentCaptor<List<RewardCalculationTarget>>()
        verify(f.rewards).createManual(eq(f.chainId), targets.capture(), isNull())
        val target = targets.firstValue.single()
        assertEquals(f.hex[0], target.accountIdHex)
        assertEquals("1200", target.totalStake.toString())
        assertEquals("300", target.ownStake.toString())
        assertEquals(BigDecimal("0.125"), target.commission)
        assertSame(f.exposure.others, target.nominatorStakes)
    }

    @Test fun preferencesFailureCannotReturnAPartialCatalogue() = runTest {
        val f = ValidatorCatalogueFixture()
        f.defaults()
        val failure = IllegalStateException("preferences unavailable")
        whenever(f.repository.getAllValidatorPrefs(f.chainId)).thenThrow(failure)
        assertCatalogueFailure(failure, runCatching { f.elected() }.exceptionOrNull())
    }

    @Test fun exposureFailureCannotReturnAPartialCatalogue() = runTest {
        val f = ValidatorCatalogueFixture()
        f.defaults()
        val failure = IllegalStateException("exposure unavailable")
        whenever(f.repository.getLegacyActiveElectedValidatorsExposures(f.chainId)).thenThrow(failure)
        assertCatalogueFailure(failure, runCatching { f.elected() }.exceptionOrNull())
    }
}
