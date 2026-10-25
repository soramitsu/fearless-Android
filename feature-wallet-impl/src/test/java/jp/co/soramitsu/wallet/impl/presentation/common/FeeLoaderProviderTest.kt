package jp.co.soramitsu.wallet.impl.presentation.common

import androidx.arch.core.executor.testing.InstantTaskExecutorRule
import io.mockk.coEvery
import io.mockk.every
import io.mockk.mockk
import java.io.IOException
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger
import jp.co.soramitsu.androidfoundation.testing.MainCoroutineRule
import jp.co.soramitsu.common.mixin.api.RetryPayload
import jp.co.soramitsu.common.resources.ResourceManager
import jp.co.soramitsu.fearless_utils.runtime.extrinsic.CallMovedToAssetHubException
import jp.co.soramitsu.feature_wallet_impl.R
import jp.co.soramitsu.wallet.api.presentation.mixin.fee.FeeLoaderProvider
import jp.co.soramitsu.wallet.api.presentation.mixin.fee.FeeStatus
import jp.co.soramitsu.wallet.impl.domain.TokenUseCase
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.cancel
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Rule
import org.junit.Test

/** Relay staking, payout and crowdloan screens load their fee here and show its retry dialog when that fails. */
@OptIn(ExperimentalCoroutinesApi::class)
class FeeLoaderProviderTest {

    @get:Rule
    val instantTaskExecutorRule = InstantTaskExecutorRule()

    @get:Rule
    val mainCoroutineRule = MainCoroutineRule(UnconfinedTestDispatcher())

    private val resourceManager = mockk<ResourceManager>(relaxed = true).also { resources ->
        every { resources.getString(any()) } answers { "text-${firstArg<Int>()}" }
    }
    private val tokenUseCase = mockk<TokenUseCase> { coEvery { currentToken() } returns mockk(relaxed = true) }
    private val scope = CoroutineScope(SupervisorJob())
    private val provider = FeeLoaderProvider(resourceManager, tokenUseCase)

    @After
    fun tearDown() {
        scope.cancel()
    }

    @Test
    fun `a call that moved to Asset Hub says so instead of blaming the network`() {
        var cancelled = 0
        val retry = loadFailingFee(CallMovedToAssetHubException("Staking", "bond"), onRetryCancelled = { cancelled++ })

        assertEquals(text(R.string.relay_moved_to_asset_hub_title), retry.title)
        assertEquals(text(R.string.relay_moved_to_asset_hub_message), retry.message)
        assertEquals(FeeStatus.Error, provider.feeLiveData.value)

        // It would be refused again, so the dialog offers no retry, only the way back.
        assertNull(retry.onRetry)
        retry.onCancel!!()
        assertEquals(1, cancelled)
    }

    @Test
    fun `other failures keep the network error and can be retried`() {
        val retry = loadFailingFee(IOException("offline"))

        assertEquals(text(R.string.choose_amount_network_error), retry.title)
        assertEquals(text(R.string.choose_amount_error_fee), retry.message)

        completed = CountDownLatch(1)
        retry.onRetry!!()

        assertTrue(completed.await(5, TimeUnit.SECONDS))
        assertEquals(2, estimates.get())
    }

    @Test
    fun `asking for a fee that moved to Asset Hub explains why it is missing`() {
        loadFailingFee(CallMovedToAssetHubException("NominationPools", "join"))

        // The payout confirmation shows no retry dialog and asks on confirm.
        assertEquals(text(R.string.relay_moved_to_asset_hub_title) to text(R.string.relay_moved_to_asset_hub_message), requireFeeError())
    }

    @Test
    fun `asking for a fee that failed otherwise reports the network error`() {
        loadFailingFee(IOException("offline"))

        assertEquals(text(R.string.choose_amount_network_error) to text(R.string.choose_amount_error_fee), requireFeeError())
    }

    @Test
    fun `asking while the fee is loading again says it is not loaded yet`() {
        loadFailingFee(CallMovedToAssetHubException("Staking", "bond"))

        // A new load forgets the previous failure until it fails again.
        provider.loadFee(scope, feeConstructor = { awaitCancellation() }, onRetryCancelled = {})

        assertEquals(text(R.string.fee_not_yet_loaded_title) to text(R.string.fee_not_yet_loaded_message), requireFeeError())
    }

    private fun requireFeeError(): Pair<String, String> {
        var error: Pair<String, String>? = null
        provider.requireFee(block = { fail("no fee was loaded") }, onError = { title, message -> error = title to message })

        return requireNotNull(error)
    }

    private val estimates = AtomicInteger()
    private var completed = CountDownLatch(1)

    private fun loadFailingFee(error: Throwable, onRetryCancelled: () -> Unit = {}): RetryPayload {
        provider.loadFee(
            coroutineScope = scope,
            feeConstructor = {
                estimates.incrementAndGet()
                throw error
            },
            onRetryCancelled = onRetryCancelled,
            onComplete = { completed.countDown() }
        )

        assertTrue(completed.await(5, TimeUnit.SECONDS))
        return provider.retryEvent.value!!.peekContent()
    }

    private fun text(id: Int) = "text-$id"
}
