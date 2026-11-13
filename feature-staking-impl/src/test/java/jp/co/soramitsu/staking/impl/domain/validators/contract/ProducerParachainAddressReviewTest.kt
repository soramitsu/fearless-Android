package jp.co.soramitsu.staking.impl.domain.validators.contract

import jp.co.soramitsu.staking.impl.presentation.common.SetupStakingProcess
import jp.co.soramitsu.staking.impl.presentation.mappers.toCollator
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TestRule
import org.mockito.kotlin.any
import org.mockito.kotlin.anyOrNull
import org.mockito.kotlin.eq
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever
import java.math.BigDecimal

class ProducerParachainAddressReviewTest {
    @get:Rule val environment: TestRule = SearchContractEnvironment()

    @Test fun mixedCasePrefixedHexFindsTheCanonicalRepositoryKey() = runTest {
        val f = ProducerSearchFixture(true)
        f.defaults()
        val accountId = "ab".repeat(20)
        val collator = f.para.candidate.toCollator(accountId, f.para.identity, BigDecimal("7.25"))
        whenever(f.collatorProvider.getCollators(any(), anyOrNull())).thenReturn(mapOf(accountId to collator))
        val row = requireNotNull(f.interactor.getBlockProducerByAddress("0x" + "aB".repeat(20)))
        assertEquals(accountId, row.address)
        assertEquals("Collator Alpha", row.name)
        assertEquals("7.25%", row.rewardsPercent)
        verify(f.collatorProvider).getCollators(eq(f.chain), anyOrNull())
    }

    @Test fun prefixedRemoteResultCanBeSelectedUsingItsReturnedCanonicalAddress() = runTest {
        val f = ProducerSearchFixture(true)
        f.defaults()
        val row = requireNotNull(f.interactor.getBlockProducerByAddress("0x" + f.para.hex[0]))
        assertEquals(f.para.hex[0], row.address)
        assertTrue(f.choose(row.address).isSuccess)
        assertEquals(listOf(f.para.hex[0]), f.paraPayload().blockProducers.map { it.address })
        val payload = f.paraPayload() as SetupStakingProcess.ReadyToSubmit.Payload.Parachain
        assertEquals(BigDecimal("12.34"), payload.amount)
    }
}
