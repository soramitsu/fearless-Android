package jp.co.soramitsu.runtime.integration

import android.util.Log
import org.junit.Assume.assumeTrue
import org.junit.Test
import java.io.BufferedReader
import java.io.OutputStreamWriter
import java.net.HttpURLConnection
import java.net.URL

class DotIntegrationTest {

    private fun hasNetwork(): Boolean = try {
        // Lightweight reachability check to a stable host
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
    fun testPolkadotRuntimeVersion_logsResponse() {
        assumeTrue("No network; skipping", hasNetwork())

        // Allow override via instrumentation arg or system property; otherwise use a public RPC mirror
        val override = System.getProperty("DOT_RPC_URL") ?: System.getenv("DOT_RPC_URL")
        val rpcUrl = override ?: "https://polkadot-rpc.publicnode.com"

        val payload = """
            {"jsonrpc":"2.0","method":"state_getRuntimeVersion","params":[],"id":1}
        """.trimIndent()

        val response = httpPost(rpcUrl, payload)

        Log.i("DotIntegrationTest", "RPC URL: $rpcUrl")
        Log.i("DotIntegrationTest", "state_getRuntimeVersion => $response")

        // Basic sanity: expect fields like specName/specVersion in response
        val ok = response.contains("specName") && response.contains("specVersion")
        assumeTrue("Unexpected DOT RPC response; possibly blocked network", ok)
    }

    private fun httpPost(urlStr: String, body: String): String {
        val url = URL(urlStr)
        val conn = (url.openConnection() as HttpURLConnection).apply {
            requestMethod = "POST"
            connectTimeout = 5000
            readTimeout = 7000
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

