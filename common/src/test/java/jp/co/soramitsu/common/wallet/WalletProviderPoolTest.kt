package jp.co.soramitsu.common.wallet

import jp.co.soramitsu.common.data.network.WalletProviderPool
import jp.co.soramitsu.common.data.network.WalletProviderUnavailableException
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.test.runTest
import okhttp3.Protocol
import okhttp3.Request
import okhttp3.ResponseBody.Companion.toResponseBody
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test
import retrofit2.HttpException
import retrofit2.Response
import java.io.IOException

class WalletProviderPoolTest {
    @Test
    fun `rate limited provider backs off and successful fallback stays preferred`() = runTest {
        var now = 0L
        val pool = WalletProviderPool { now }
        val calls = mutableListOf<String>()
        val nodes = listOf("primary", "fallback")
        assertEquals("ok", pool.execute(nodes) {
            calls += it
            if (it == "primary") throw rateLimit("60")
            "ok"
        })
        assertEquals(listOf("primary", "fallback"), calls)
        calls.clear()
        pool.execute(nodes) { calls += it; "ok" }
        assertEquals(listOf("fallback"), calls)
        now = 60_001
        calls.clear()
        pool.execute(nodes) { calls += it; if (it == "fallback") throw IOException(); "ok" }
        assertEquals(listOf("fallback", "primary"), calls)
    }

    @Test
    fun `all cooling providers fail without a new network attempt then recover`() = runTest {
        var now = 0L
        val pool = WalletProviderPool { now }
        var calls = 0
        try { pool.execute(listOf("one", "two")) { calls++; throw IOException() } } catch (_: IOException) { }
        try { pool.execute(listOf("one", "two")) { calls++; "unexpected" }; error("Expected cooldown") }
        catch (_: WalletProviderUnavailableException) { }
        assertEquals(2, calls)
        now = 1_001
        assertEquals("recovered", pool.execute(listOf("one", "two")) { calls++; "recovered" })
        assertEquals(3, calls)
    }

    @Test
    fun `deterministic rejection and cancellation never try another provider`() = runTest {
        for (failure in listOf(IllegalArgumentException("bad recipient"), CancellationException())) {
            var calls = 0
            try { WalletProviderPool().execute(listOf("one", "two")) { calls++; throw failure } }
            catch (actual: Exception) { assertEquals(failure, actual) }
            assertEquals(1, calls)
        }
    }

    @Test
    fun `semantic transient failure retries identical caller payload and bounds retry after`() = runTest {
        var now = 0L
        val pool = WalletProviderPool { now }
        val payloads = mutableListOf<String>()
        assertEquals("accepted", pool.execute(listOf("one", "two"), { it.message == "node lag" }) {
            payloads += "frozen-signed-transaction"
            if (it == "one") throw IllegalStateException("node lag")
            "accepted"
        })
        assertEquals(listOf("frozen-signed-transaction", "frozen-signed-transaction"), payloads)
        try { pool.execute(listOf("rate")) { throw rateLimit("999999999999999999999") } } catch (_: HttpException) { }
        now = 30_001
        assertEquals(true, pool.execute(listOf("rate")) { true })
    }

    private fun rateLimit(seconds: String) = HttpException(
        Response.error<String>("limited".toResponseBody(), okhttp3.Response.Builder()
            .request(Request.Builder().url("https://provider.example").build())
            .protocol(Protocol.HTTP_1_1).code(429).message("limited")
            .header("Retry-After", seconds).build())
    )
}
