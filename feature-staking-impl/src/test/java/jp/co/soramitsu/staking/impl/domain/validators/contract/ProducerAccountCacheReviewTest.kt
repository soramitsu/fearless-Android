package jp.co.soramitsu.staking.impl.domain.validators.contract

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.async
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TestRule
import org.mockito.kotlin.any
import org.mockito.kotlin.times
import org.mockito.kotlin.verify

class ProducerAccountCacheReviewTest {
    @get:Rule val environment: TestRule = SearchContractEnvironment()

    @Test fun sameChainDifferentWalletUsesItsOwnDelegationFilteredCatalogue() = runTest {
        val f = ProducerAccountOwnershipFixture()
        try {
            f.start()
            assertEquals(f.para.hex.drop(1), f.rows().map { it.address })
            f.selectedWallet.value = f.wallet(22, f.secondAccount)
            assertEquals(listOf(f.para.hex[0], f.para.hex[2]), f.rows().map { it.address })
            verify(f.para.repository, times(2)).getCandidateInfos(any(), any())
        } finally {
            f.close()
        }
    }

    @Test fun changedChainAccountWithinSameWalletGetsANewCatalogue() = runTest {
        val f = ProducerAccountOwnershipFixture()
        try {
            f.start()
            f.rows()
            f.selectedWallet.value = f.wallet(11, f.secondAccount)
            assertEquals(listOf(f.para.hex[0], f.para.hex[2]), f.rows().map { it.address })
            verify(f.para.repository, times(2)).getCandidateInfos(any(), any())
        } finally {
            f.close()
        }
    }

    @Test fun returningToAnAccountReusesOnlyThatAccountsOwnedCatalogue() = runTest {
        val f = ProducerAccountOwnershipFixture()
        try {
            f.start()
            val firstWallet = f.selectedWallet.value
            val first = f.rows().map { it.address }
            f.selectedWallet.value = f.wallet(22, f.secondAccount)
            f.rows()
            f.selectedWallet.value = firstWallet
            assertEquals(first, f.rows().map { it.address })
            verify(f.para.repository, times(2)).getCandidateInfos(any(), any())
        } finally {
            f.close()
        }
    }

    @Test fun accountCatalogueSurvivesOneOwnerButReloadsAfterTheLastOwnerEnds() = runTest {
        val f = ProducerAccountOwnershipFixture()
        val second = SearchContractLifecycle()
        val replacement = SearchContractLifecycle()
        try {
            f.start()
            f.rows()
            f.rows(second)
            f.data.lifecycle.destroy()
            assertEquals(f.para.hex.drop(1), f.rows(second).map { it.address })
            verify(f.para.repository, times(1)).getCandidateInfos(any(), any())
            second.destroy()
            f.rows(replacement)
            verify(f.para.repository, times(2)).getCandidateInfos(any(), any())
        } finally {
            second.destroy()
            replacement.destroy()
            f.close()
        }
    }

    @Test fun destroyingOneWalletsOwnerDoesNotEvictAnotherWalletsCacheEntry() = runTest {
        val f = ProducerAccountOwnershipFixture()
        val second = SearchContractLifecycle()
        val replacement = SearchContractLifecycle()
        try {
            f.start()
            val firstWallet = f.selectedWallet.value
            f.rows()
            f.selectedWallet.value = f.wallet(22, f.secondAccount)
            f.rows(second)
            f.data.lifecycle.destroy()
            assertEquals(listOf(f.para.hex[0], f.para.hex[2]), f.rows(second).map { it.address })
            verify(f.para.repository, times(2)).getCandidateInfos(any(), any())
            f.selectedWallet.value = firstWallet
            assertEquals(f.para.hex.drop(1), f.rows(replacement).map { it.address })
            verify(f.para.repository, times(3)).getCandidateInfos(any(), any())
        } finally {
            second.destroy()
            replacement.destroy()
            f.close()
        }
    }

    @Test fun suspendedOldWalletCatalogueCannotPublishAsTheNewWalletsResults() = runTest {
        val f = ProducerAccountOwnershipFixture()
        f.start()
        f.holdCandidateResponse()
        val pending = async { runCatching { f.rows() } }
        try {
            f.await(f.started)
            f.selectedWallet.value = f.wallet(22, f.secondAccount)
            f.release.complete(Unit)
            assertTrue(f.await(pending).exceptionOrNull() is CancellationException)
            assertEquals(listOf(f.para.hex[0], f.para.hex[2]), f.rows().map { it.address })
            verify(f.para.repository, times(2)).getCandidateInfos(any(), any())
        } finally {
            pending.cancelAndJoin()
            f.close()
        }
    }
}
