package jp.co.soramitsu.common.data.network

import okhttp3.OkHttpClient
import org.junit.Assert.assertEquals
import org.junit.Test
import retrofit2.Call
import retrofit2.http.GET
import retrofit2.http.Headers
import java.net.InetAddress
import java.net.ServerSocket
import java.util.concurrent.atomic.AtomicInteger
import kotlin.concurrent.thread

class NetworkApiCreatorSecurityTest {
    interface CredentialApi {
        @GET("history")
        @Headers("Ok-Access-Key: regression-test-only")
        fun history(): Call<String>
    }

    @Test
    fun `credential bearing history clients reject redirects before a second request`() {
        val calls = AtomicInteger()
        val server = ServerSocket(0, 2, InetAddress.getByName("127.0.0.1"))
        val worker = thread(isDaemon = true) {
            runCatching {
                while (!server.isClosed) {
                    server.accept().use { socket ->
                        val input = socket.getInputStream().bufferedReader()
                        while (!input.readLine().isNullOrEmpty()) { /* Consume the request headers. */ }
                        calls.incrementAndGet()
                        socket.getOutputStream().write(
                            "HTTP/1.1 302 Found\r\nLocation: /redirected\r\nContent-Length: 0\r\nConnection: close\r\n\r\n"
                                .toByteArray()
                        )
                    }
                }
            }
        }
        try {
            val client = NetworkApiCreator(OkHttpClient(), "http://127.0.0.1:${server.localPort}/")
                .create(CredentialApi::class.java, followRedirects = false)
            val response = client.history().execute()
            response.errorBody()?.close()
            assertEquals(302, response.code())
            assertEquals(1, calls.get())
        } finally {
            server.close()
            worker.join(2000)
        }
    }
}
