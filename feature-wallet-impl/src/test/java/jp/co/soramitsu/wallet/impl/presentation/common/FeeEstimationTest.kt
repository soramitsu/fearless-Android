package jp.co.soramitsu.wallet.impl.presentation.common

import io.mockk.every
import io.mockk.mockk
import java.io.IOException
import java.math.BigInteger
import jp.co.soramitsu.common.resources.ResourceManager
import jp.co.soramitsu.common.validation.MovedToAssetHubException
import jp.co.soramitsu.common.validation.WaitForFeeCalculationException
import jp.co.soramitsu.fearless_utils.runtime.extrinsic.CallMovedToAssetHubException
import jp.co.soramitsu.feature_wallet_impl.R
import jp.co.soramitsu.wallet.api.presentation.mixin.fee.FeeEstimation
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

class FeeEstimationTest {

    private val resourceManager = mockk<ResourceManager>(relaxed = true).also { resources ->
        every { resources.getString(any()) } answers { "text-${firstArg<Int>()}" }
    }
    private val estimation = FeeEstimation(resourceManager)

    @Test
    fun `an estimated fee leaves nothing to explain`() = runBlocking {
        assertEquals(BigInteger.TEN, estimation.estimate { BigInteger.TEN })

        assertFalse(estimation.hasFailed)
        assertNull(estimation.failureFlow.value)
    }

    @Test
    fun `no fee yet asks to wait and starts nothing`() {
        assertTrue(estimation.missingFeeError() is WaitForFeeCalculationException)
        assertEquals(0, estimation.retryFlow.value)
    }

    @Test
    fun `a call that moved to Asset Hub is explained and never retried`() = runBlocking {
        assertNull(estimation.estimate { throw CallMovedToAssetHubException("Staking", "bond") })

        val error = estimation.missingFeeError()

        assertTrue(error is MovedToAssetHubException)
        assertEquals(text(R.string.relay_moved_to_asset_hub_title) to text(R.string.relay_moved_to_asset_hub_message), error.message to error.explanation)
        assertEquals(0, estimation.retryFlow.value)
    }

    @Test
    fun `the refusal is recognised behind the wrappers a caller may add`() = runBlocking {
        estimation.estimate { throw IllegalStateException("fee", RuntimeException(CallMovedToAssetHubException("NominationPools", "join"))) }

        assertTrue(estimation.missingFeeError() is MovedToAssetHubException)
    }

    @Test
    fun `any other failure is a fee error that the next attempt retries`() = runBlocking {
        assertNull(estimation.estimate { throw IOException("offline") })

        val error = estimation.missingFeeError()

        assertEquals(text(R.string.choose_amount_network_error) to text(R.string.choose_amount_error_fee), error.message to error.explanation)
        assertEquals(1, estimation.retryFlow.value)

        // The retried estimation succeeds: nothing is left to explain.
        assertEquals(BigInteger.ONE, estimation.estimate { BigInteger.ONE })
        assertFalse(estimation.hasFailed)
    }

    @Test
    fun `a cancelled estimation is not a failure`() {
        assertThrows(CancellationException::class.java) {
            runBlocking { estimation.estimate { throw CancellationException("screen closed") } }
        }

        assertFalse(estimation.hasFailed)
    }

    private fun text(id: Int) = "text-$id"
}
