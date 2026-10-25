package jp.co.soramitsu.common.data.network.config

import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertSame
import org.junit.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.doSuspendableAnswer
import org.mockito.kotlin.mock
import org.mockito.kotlin.never
import org.mockito.kotlin.times
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever

@OptIn(ExperimentalCoroutinesApi::class)
class MutationAuthorizationRefresherTest {
    @Test
    fun `starts immediately refreshes every five minutes and is idempotent`() = runTest {
        val fetcher = mock<RemoteConfigFetcher>()
        val store = mock<MutationAuthorizationStore>()
        whenever(fetcher.getFeatureToggle()).thenReturn(FeatureToggleConfig(mutationAuthorization = "token"))
        val refresher = MutationAuthorizationRefresher(fetcher, store, mock())
        val job = refresher.start(backgroundScope)
        assertSame(job, refresher.start(backgroundScope))
        runCurrent()
        verify(store).acceptFresh("token")
        advanceTimeBy(299_999)
        runCurrent()
        verify(store, times(1)).acceptFresh("token")
        advanceTimeBy(1)
        runCurrent()
        verify(store, times(2)).acceptFresh("token")
        job.cancel()
    }

    @Test
    fun `unavailable refresh clears authority`() = runTest {
        val fetcher = mock<RemoteConfigFetcher>()
        val store = mock<MutationAuthorizationStore>()
        whenever(fetcher.getFeatureToggle()).thenThrow(IllegalStateException("offline"))
        MutationAuthorizationRefresher(fetcher, store, mock()).refreshOnce()
        verify(store).invalidate()
    }

    @Test
    fun `timeout denies but leaves the refresh loop running`() = runTest {
        val fetcher = mock<RemoteConfigFetcher>()
        val store = mock<MutationAuthorizationStore>()
        whenever(fetcher.getFeatureToggle(any())).doSuspendableAnswer { awaitCancellation() }
        val job = MutationAuthorizationRefresher(fetcher, store, mock()).start(backgroundScope)
        runCurrent()
        advanceTimeBy(30_000)
        runCurrent()
        verify(store).invalidate()
        advanceTimeBy(300_000)
        runCurrent()
        verify(fetcher, times(2)).getFeatureToggle("no-cache")
        job.cancel()
    }

    @Test
    fun `successful refresh remembers kill switch vetoes`() = runTest {
        val fetcher = mock<RemoteConfigFetcher>()
        val store = mock<MutationAuthorizationStore>()
        val toggles = mock<ProductFeatureToggleStore>()
        val config = FeatureToggleConfig(bitcoinSendEnabled = false)
        whenever(fetcher.getFeatureToggle()).thenReturn(config)

        MutationAuthorizationRefresher(fetcher, store, toggles).refreshOnce()

        verify(toggles).rememberRemoteVetoes(config)
    }

    @Test
    fun `failed refresh leaves remembered vetoes untouched`() = runTest {
        val fetcher = mock<RemoteConfigFetcher>()
        val toggles = mock<ProductFeatureToggleStore>()
        whenever(fetcher.getFeatureToggle()).thenThrow(IllegalStateException("offline"))

        MutationAuthorizationRefresher(fetcher, mock(), toggles).refreshOnce()

        verify(toggles, never()).rememberRemoteVetoes(any())
        verify(toggles, never()).update(any())
    }
}
