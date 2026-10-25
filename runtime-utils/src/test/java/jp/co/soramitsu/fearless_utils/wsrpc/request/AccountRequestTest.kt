package jp.co.soramitsu.fearless_utils.wsrpc.request

import com.google.gson.Gson
import jp.co.soramitsu.fearless_utils.wsrpc.request.runtime.account.AccountInfoRequest
import org.bouncycastle.util.encoders.Hex
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.junit.MockitoJUnitRunner

private const val PUBLIC_KEY = "8ad2a3fba73321961cd5d1b8272aa95a21e75dd5b098fb36ed996961ac7b2931"
private const val EXPECTED_HASH =
    "0x26aa394eea5630e07c48ae0c9558cef7b99d880ec681799c0cf30e8886371da9d14d49c37bcc0afd3d9093917c6d46ea8ad2a3fba73321961cd5d1b8272aa95a21e75dd5b098fb36ed996961ac7b2931"

@RunWith(MockitoJUnitRunner::class)
class AccountRequestTest {
    @Test
    fun `should correctly encode request`() {
        val request = createRequest()

        assertEquals(EXPECTED_HASH, request.params[0])
    }

    @Test
    fun `should be serializable`() {
        val mapper = Gson()

        val request = createRequest()

        mapper.toJson(request, AccountInfoRequest::class.java)
    }

    private fun createRequest(): AccountInfoRequest {
        val publicKeyBytes = Hex.decode(PUBLIC_KEY)

        return AccountInfoRequest(publicKeyBytes)
    }
}