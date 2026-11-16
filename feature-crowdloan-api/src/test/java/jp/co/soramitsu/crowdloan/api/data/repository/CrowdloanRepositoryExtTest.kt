package jp.co.soramitsu.crowdloan.api.data.repository

import io.mockk.coEvery
import io.mockk.mockk
import jp.co.soramitsu.crowdloan.api.data.network.blockhain.binding.Contribution
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test
import java.math.BigInteger

class CrowdloanRepositoryExtTest {
    private val repository = mockk<CrowdloanRepository>()
    private val accountId = ByteArray(32) { 1 }

    @Test fun contributionsAreFetchedForEveryParachainFund() = runTest {
        val first = Contribution(BigInteger.TEN, "memo")
        coEvery { repository.getContribution("polkadot", accountId, BigInteger.valueOf(2000), BigInteger.ONE) } returns first
        coEvery { repository.getContribution("polkadot", accountId, BigInteger.valueOf(2004), BigInteger.TWO) } returns null

        val contributions = repository.getContributions(
            "polkadot", accountId, mapOf(BigInteger.valueOf(2000) to BigInteger.ONE, BigInteger.valueOf(2004) to BigInteger.TWO)
        )

        assertEquals(setOf(BigInteger.valueOf(2000), BigInteger.valueOf(2004)), contributions.keys)
        assertSame(first, contributions[BigInteger.valueOf(2000)])
        assertNull(contributions[BigInteger.valueOf(2004)])
    }

    @Test fun noFundsMeanNoContributions() = runTest {
        assertTrue(repository.getContributions("kusama", accountId, emptyMap()).isEmpty())
    }
}
