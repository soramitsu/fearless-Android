package jp.co.soramitsu.walletconnect.impl.presentation

import com.reown.walletkit.client.Wallet
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class WalletConnectSignedRequestsTest {

    private val request = personalSign(message = "0x01")
    private val ledger = WalletConnectSignedRequests()
    private var signatures = 0
    private val broadcasts = mutableListOf<String>()

    @Test
    fun `a request is signed once and its response reused on retry`() = runBlocking {
        val first = respond(request) { "0xsig${++signatures}" }
        val retry = respond(request) { "0xsig${++signatures}" }

        assertEquals("0xsig1", first)
        assertEquals(first, retry)
        assertEquals(1, signatures)
    }

    @Test
    fun `a broadcast transaction is never signed or broadcast again`() = runBlocking {
        val first = sendTransaction { "0xhash" }
        val retry = sendTransaction { "0xhash" }

        assertEquals("0xhash", first)
        assertEquals("0xhash", retry)
        assertEquals(1, signatures)
        assertEquals(listOf("0xsigned1"), broadcasts)
    }

    @Test
    fun `a failed broadcast is retried with the same signed bytes`() = runBlocking {
        val failure = runCatching { sendTransaction { error("node unreachable") } }
        val retry = sendTransaction { "0xhash" }

        assertTrue(failure.isFailure)
        assertEquals("0xhash", retry)
        assertEquals(1, signatures)
        assertEquals(listOf("0xsigned1", "0xsigned1"), broadcasts)
    }

    @Test
    fun `a failure before anything was signed allows a new attempt`() = runBlocking {
        val failure = runCatching { respond(request) { error("no connection") } }
        val retry = respond(request) { "0xsig" }

        assertTrue(failure.isFailure)
        assertEquals("0xsig", retry)
    }

    @Test
    fun `concurrent attempts for one request sign once`() = runBlocking {
        val results = (1..8).map {
            async {
                ledger.responseFor(
                    request = request,
                    sign = {
                        delay(10)
                        SignedRequest.Response("0xsig${++signatures}")
                    },
                    broadcast = { error("not a transaction") }
                )
            }
        }.awaitAll()

        assertEquals(List(8) { "0xsig1" }, results)
        assertEquals(1, signatures)
    }

    @Test
    fun `a request id reused for other content is refused and never signed again`() = runBlocking {
        val first = respond(request) { "0xsig${++signatures}" }
        val reused = runCatching { respond(personalSign(message = "0x02")) { "0xsig${++signatures}" } }

        assertEquals("0xsig1", first)
        assertTrue(reused.exceptionOrNull() is WalletConnectRequestRejectedException)
        assertEquals(1, signatures)
    }

    private suspend fun respond(request: Wallet.Model.SessionRequest, signature: () -> String): String =
        ledger.responseFor(
            request = request,
            sign = { SignedRequest.Response(signature()) },
            broadcast = { error("not a transaction") }
        )

    private suspend fun sendTransaction(broadcast: () -> String): String = ledger.responseFor(
        request = request,
        sign = { SignedRequest.Transaction(chainId = "1", signedTransaction = "0xsigned${++signatures}") },
        broadcast = { transaction ->
            broadcasts += transaction.signedTransaction
            broadcast()
        }
    )

    private fun personalSign(message: String) = sessionRequest(
        id = 1,
        method = "personal_sign",
        params = """["$message", "0x8ba1f109551bd432803012645ac136ddd64dba72"]""",
        chainId = "eip155:1"
    )
}
