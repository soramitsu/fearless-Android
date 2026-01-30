package jp.co.soramitsu.staking.impl.domain.solana

import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlinx.coroutines.test.runTest
import okhttp3.OkHttpClient
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.After
import org.junit.Before
import org.junit.Test

class SolanaRpcServiceTest {

    private lateinit var webServer: MockWebServer
    private lateinit var service: SolanaRpcService

    @Before
    fun setup() {
        webServer = MockWebServer()
        webServer.start()
        service = SolanaRpcService(OkHttpClient())
    }

    @After
    fun tearDown() {
        webServer.shutdown()
    }

    @Test
    fun `should parse validators from rpc response`() = runTest {
        val body = """
            {
              "jsonrpc":"2.0",
              "result":{
                "current":[
                  {"votePubkey":"Vote111111111111111111111111111111111", "activatedStake":"2000000000", "commission":5},
                  {"votePubkey":"Vote222222222222222222222222222222222", "activatedStake":"1000000000", "commission":10}
                ],
                "delinquent":[]
              },
              "id":1
            }
        """.trimIndent()
        webServer.enqueue(MockResponse().setResponseCode(200).setBody(body))

        val validators = service.fetchValidators(webServer.url("/").toString())

        assertEquals(2, validators.size)
        val first = validators.first()
        assertEquals("Vote111111111111111111111111111111111", first.voteAccount)
        assertEquals(5, first.commission)
        assertTrue(first.activatedStake.toLong() > 0)
    }

    @Test
    fun `should return empty list on error`() = runTest {
        webServer.enqueue(MockResponse().setResponseCode(500))

        val validators = service.fetchValidators(webServer.url("/").toString())

        assertTrue(validators.isEmpty())
    }
}
