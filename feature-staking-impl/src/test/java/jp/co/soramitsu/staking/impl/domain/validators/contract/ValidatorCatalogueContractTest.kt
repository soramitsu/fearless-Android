package jp.co.soramitsu.staking.impl.domain.validators.contract

import jp.co.soramitsu.staking.impl.domain.validators.ValidatorSource
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test
import org.mockito.kotlin.argThat
import org.mockito.kotlin.verify
import java.math.BigDecimal

class ValidatorCatalogueContractTest {
    @Test fun electedCataloguePreservesRepositoryOrderAndAddressIdentity() = runTest {
        val f = ValidatorCatalogueFixture()
        f.defaults()
        val actual = f.elected()
        assertEquals(f.hex.take(2), actual.map { it.accountIdHex })
        assertEquals(f.addresses.take(2), actual.map { it.address })
    }

    @Test fun customCatalogueUsesRequestedOrderAndDoesNotAppendOtherValidators() = runTest {
        val f = ValidatorCatalogueFixture()
        f.defaults()
        val actual = f.provider.getValidators(f.chain, ValidatorSource.Custom(listOf(f.hex[1], f.hex[0])))
        assertEquals(listOf(f.hex[1], f.hex[0]), actual.map { it.accountIdHex })
        verify(f.identities).getIdentitiesFromIds(f.chain, listOf(f.hex[1], f.hex[0]))
    }

    @Test fun emptyCustomSelectionCannotReturnTheEntireCatalogue() = runTest {
        val f = ValidatorCatalogueFixture()
        f.defaults()
        assertTrue(f.provider.getValidators(f.chain, ValidatorSource.Custom(emptyList())).isEmpty())
        verify(f.repository).getSlashes(f.chainId, emptyList())
    }

    @Test fun missingPreferencesRetainsRequestedValidatorWithoutInventingCommission() = runTest {
        val f = ValidatorCatalogueFixture()
        f.defaults()
        val actual = f.provider.getValidators(f.chain, ValidatorSource.Custom(listOf(f.hex[2]))).single()
        assertNull(actual.prefs)
        assertNull(actual.electedInfo)
        assertEquals(f.addresses[2], actual.address)
    }

    @Test fun electedStakeRetainsExactOwnTotalAndNominatorAmounts() = runTest {
        val f = ValidatorCatalogueFixture()
        f.defaults()
        val info = requireNotNull(f.elected().first().electedInfo)
        assertEquals("1200", info.totalStake.toString())
        assertEquals("300", info.ownStake.toString())
        assertEquals(listOf("400", "500"), info.nominatorStakes.map { it.value.toString() })
    }

    @Test fun apyRemainsFractionalInsideTheValidatorDomain() = runTest {
        val f = ValidatorCatalogueFixture()
        f.defaults()
        assertEquals(BigDecimal("0.125"), f.elected().first().electedInfo?.apy)
        verify(f.calculator).getApyFor(argThat { contentEquals(f.ids[0]) })
    }

    @Test fun slashAndIdentityAreJoinedByAccountRatherThanCataloguePosition() = runTest {
        val f = ValidatorCatalogueFixture()
        f.defaults()
        val actual = f.elected()
        assertTrue(actual[0].slashed)
        assertSame(f.identity, actual[0].identity)
        assertFalse(actual[1].slashed)
        assertNull(actual[1].identity)
    }

    @Test fun exactRewardedNominatorCapacityIsNotOversubscribed() = runTest {
        val f = ValidatorCatalogueFixture()
        f.defaults()
        assertFalse(requireNotNull(f.elected().first().electedInfo).isOversubscribed)
    }
}
