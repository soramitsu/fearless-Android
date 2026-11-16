package jp.co.soramitsu.app.root.domain

import co.jp.soramitsu.walletconnect.model.WalletConnectRequest
import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkObject
import io.mockk.unmockkObject
import jp.co.soramitsu.walletconnect.impl.presentation.WCDelegate
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Before
import org.junit.Test

class RootInteractorTest {
    private val interactor = RootInteractor()

    @Before fun setUp() = mockkObject(WCDelegate)

    @After fun tearDown() = unmockkObject(WCDelegate)

    @Test
    fun onlyTheBuyProviderRedirectIsRecognised() {
        assertTrue(interactor.isBuyProviderRedirectLink("fearless://buy-success?transactionStatus=completed"))
        assertFalse(interactor.isBuyProviderRedirectLink("https://fearlesswallet.io/ton-connect/v2"))
    }

    @Test
    fun pendingRequestsComeFromTheWalletConnectClient(): Unit = runBlocking {
        val request = mockk<WalletConnectRequest>()
        every { WCDelegate.getPendingListOfSessionRequests("topic") } returns listOf(request)
        assertEquals(listOf(request), interactor.getPendingListOfSessionRequests("topic"))
    }

    @Test
    fun aFailingClientYieldsNoPendingRequests(): Unit = runBlocking {
        every { WCDelegate.getPendingListOfSessionRequests("broken") } throws IllegalStateException("not attached")
        assertEquals(emptyList<WalletConnectRequest>(), interactor.getPendingListOfSessionRequests("broken"))
    }

    @Test
    fun cancellationIsNotSwallowed(): Unit = runBlocking {
        every { WCDelegate.getPendingListOfSessionRequests("cancelled") } throws CancellationException("stop")
        try {
            interactor.getPendingListOfSessionRequests("cancelled")
            fail("Cancellation must propagate")
        } catch (expected: CancellationException) {
            assertEquals("stop", expected.message)
        }
    }
}
