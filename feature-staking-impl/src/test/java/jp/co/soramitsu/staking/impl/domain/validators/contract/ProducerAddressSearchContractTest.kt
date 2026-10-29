package jp.co.soramitsu.staking.impl.domain.validators.contract

import jp.co.soramitsu.staking.api.domain.model.Validator
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TestRule
import org.mockito.kotlin.any
import org.mockito.kotlin.anyOrNull
import org.mockito.kotlin.eq
import org.mockito.kotlin.verify
import org.mockito.kotlin.verifyNoInteractions
import org.mockito.kotlin.whenever

class ProducerAddressSearchContractTest {
    @get:Rule val environment: TestRule = SearchContractEnvironment()

    @Test fun invalidRelayAddressCannotStartRemoteSearch() = runTest {
        val f = ProducerSearchFixture()
        f.defaults()
        assertNull(f.interactor.getBlockProducerByAddress("invalid"))
        verifyNoInteractions(f.validatorProvider, f.collatorProvider)
    }

    @Test fun invalidParachainAddressCannotStartRemoteSearch() = runTest {
        val f = ProducerSearchFixture(true)
        f.defaults()
        assertNull(f.interactor.getBlockProducerByAddress("0x123"))
        verifyNoInteractions(f.validatorProvider, f.collatorProvider)
    }

    @Test fun relayAddressWithoutValidatorPreferencesIsNotASelectableSearchResult() = runTest {
        val f = ProducerSearchFixture()
        f.defaults()
        val value = Validator(f.relay.addresses[0], false, f.relay.hex[0], null, null, f.relay.identity)
        whenever(f.validatorProvider.getValidatorWithoutElectedInfo(any(), any())).thenReturn(value)
        assertNull(f.interactor.getBlockProducerByAddress(f.relay.addresses[0]))
    }

    @Test fun relayAddressSearchPreservesIdentityAndExactRequestedAddress() = runTest {
        val f = ProducerSearchFixture()
        f.defaults()
        val row = requireNotNull(f.interactor.getBlockProducerByAddress(f.relay.addresses[0]))
        assertEquals("Alice", row.name)
        assertEquals(f.relay.addresses[0], row.address)
        assertEquals("12.5%", row.rewardsPercent)
        verify(f.validatorProvider).getValidatorWithoutElectedInfo(f.chain, f.relay.addresses[0])
    }

    @Test fun parachainAddressSearchResolvesTheMatchingHexCandidate() = runTest {
        val f = ProducerSearchFixture(true)
        f.defaults()
        val row = requireNotNull(f.interactor.getBlockProducerByAddress("0x" + f.para.hex[0]))
        assertEquals(f.para.hex[0], row.address)
        assertEquals("Collator Alpha", row.name)
        assertEquals("12.5%", row.rewardsPercent)
        verify(f.collatorProvider).getCollators(eq(f.chain), anyOrNull())
        verifyNoInteractions(f.validatorProvider)
    }

    @Test fun missingParachainCandidateCannotReturnAnUnrelatedFirstResult() = runTest {
        val f = ProducerSearchFixture(true)
        f.defaults()
        assertNull(f.interactor.getBlockProducerByAddress("0x" + f.para.hex[2]))
        verify(f.collatorProvider).getCollators(eq(f.chain), anyOrNull())
    }

    @Test fun addressLookupFailureKeepsItsCauseForTheCaller() = runTest {
        val f = ProducerSearchFixture()
        f.defaults()
        val failure = IllegalStateException("RPC unavailable")
        whenever(f.validatorProvider.getValidatorWithoutElectedInfo(any(), any())).thenThrow(failure)
        assertCatalogueFailure(
            failure,
            runCatching {
            f.interactor.getBlockProducerByAddress(f.relay.addresses[0])
        }.exceptionOrNull()
        )
    }

    @Test fun addressLookupCancellationCannotBecomeAnEmptySearchResult() = runTest {
        val f = ProducerSearchFixture(true)
        f.defaults()
        val failure = CancellationException("new search")
        whenever(f.collatorProvider.getCollators(any(), anyOrNull())).thenThrow(failure)
        assertCatalogueFailure(
            failure,
            runCatching {
            f.interactor.getBlockProducerByAddress("0x" + f.para.hex[0])
        }.exceptionOrNull()
        )
    }
}
