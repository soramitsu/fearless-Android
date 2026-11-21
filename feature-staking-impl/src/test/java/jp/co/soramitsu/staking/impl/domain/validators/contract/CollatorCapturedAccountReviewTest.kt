package jp.co.soramitsu.staking.impl.domain.validators.contract

import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.never
import org.mockito.kotlin.verify
import org.mockito.kotlin.verifyNoInteractions

class CollatorCapturedAccountReviewTest {
    @Test fun explicitAccountFiltersItsDelegationsWithoutReadingSelectedWalletAgain() = runTest {
        val f = CollatorCatalogueFixture()
        f.defaults()
        f.used(0)
        val rows = f.provider.getCollators(f.chain, f.accountId.copyOf())
        assertEquals(listOf(f.hex[1], f.hex[2]), rows.keys.toList())
        verifyNoInteractions(f.accounts)
        verify(f.repository).getDelegatorState(f.chainId, f.accountId)
    }

    @Test fun explicitMissingAccountReturnsCandidatesWithoutReadingWalletOrDelegations() = runTest {
        val f = CollatorCatalogueFixture()
        f.defaults()
        val rows = f.provider.getCollators(f.chain, null)
        assertEquals(f.hex, rows.keys.toList())
        verifyNoInteractions(f.accounts)
        verify(f.repository, never()).getDelegatorState(any(), any())
    }
}
