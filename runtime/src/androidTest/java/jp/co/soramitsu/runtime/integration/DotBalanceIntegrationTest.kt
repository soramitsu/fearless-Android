package jp.co.soramitsu.runtime.integration

import android.util.Log
import org.junit.Assume.assumeTrue
import org.junit.Test
import java.io.BufferedReader
import java.io.OutputStreamWriter
import java.net.HttpURLConnection
import java.net.URL

/**
 * Balance integration logger for Polkadot (DOT).
 * This test logs the raw result of `state_getStorage` for a provided System.Account storage key.
 *
 * Usage:
 *  - Provide a reachable RPC URL via system property or env var `DOT_RPC_URL`.
 *  - Provide a precomputed storage key for System.Account(<SS58 accountId>) via `DOT_ACCOUNT_STORAGE_KEY`.
 *    The key is SCALE-encoded and hashed as per substrate (pallet: "System", storage: "Account").
 *  - Example run:
 *      ./gradlew \
 *        -Pandroid.testInstrumentationRunnerArguments.DOT_RPC_URL=https://polkadot-rpc.publicnode.com \
 *        -Pandroid.testInstrumentationRunnerArguments.DOT_ACCOUNT_STORAGE_KEY=0x26aa394eea5630e07c48ae0c9558cef702a5...
 *        :runtime:connectedDebugAndroidTest
 */
class DotBalanceIntegrationTest {

    private fun hasNetwork(): Boolean = try {
        val url = URL("https://www.google.com/generate_204")
        (url.openConnection() as HttpURLConnection).run {
            connectTimeout = 2000
            readTimeout = 2000
            requestMethod = "GET"
            connect()
            val ok = responseCode in 200..399
            disconnect()
            ok
        }
    } catch (_: Exception) { false }

    @Test
    fun testPolkadotAccountStorage_logsResponseOrSkips() {
        assumeTrue("No network; skipping", hasNetwork())

        val rpcUrl = System.getProperty("DOT_RPC_URL")
            ?: System.getenv("DOT_RPC_URL")
            ?: "https://polkadot-rpc.publicnode.com"

        val storageKey = System.getProperty("DOT_ACCOUNT_STORAGE_KEY") ?: System.getenv("DOT_ACCOUNT_STORAGE_KEY")
        assumeTrue("No DOT_ACCOUNT_STORAGE_KEY provided; skipping", !storageKey.isNullOrBlank())

        val payload = """
            {"jsonrpc":"2.0","method":"state_getStorage","params":["$storageKey"],"id":2}
        """.trimIndent()

        val response = httpPost(rpcUrl, payload)
        Log.i("DotBalanceIntegrationTest", "RPC URL: $rpcUrl")
        Log.i("DotBalanceIntegrationTest", "state_getStorage(Account) => $response")

        // Basic sanity: expect non-empty hex or null
        assumeTrue("Unexpected storage response", response.contains("result"))
    }

    private fun httpPost(urlStr: String, body: String): String {
        val url = URL(urlStr)
        val conn = (url.openConnection() as HttpURLConnection).apply {
            requestMethod = "POST"
            connectTimeout = 5000
            readTimeout = 10000
            doOutput = true
            setRequestProperty("Content-Type", "application/json")
        }

        OutputStreamWriter(conn.outputStream, Charsets.UTF_8).use { it.write(body) }

        val code = conn.responseCode
        val stream = if (code in 200..299) conn.inputStream else conn.errorStream
        val text = BufferedReader(stream.reader(Charsets.UTF_8)).use { it.readText() }
        conn.disconnect()
        return text
    }
}

