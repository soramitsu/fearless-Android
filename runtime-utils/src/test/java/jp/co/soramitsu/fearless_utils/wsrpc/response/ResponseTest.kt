package jp.co.soramitsu.fearless_utils.wsrpc.response

import com.google.gson.Gson
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.junit.MockitoJUnitRunner

@RunWith(MockitoJUnitRunner::class)
class ResponseTest {
    val mapper = Gson()

    private val SCALE_ENCODED_RESULT =
        "7b0000007b4000000000000000000000011e61b68c00400000000000000000000000000000000040000000000000000000000000000000004000000000000000000000000000000000"

    private val SCALE_JSON = """
        {
            "jsonrpc": "2.0",
            "id": 1,
            "result": $SCALE_ENCODED_RESULT
        }
    """.trimIndent()

    private val ERROR_JSON = """
        {
            "jsonrpc":"2.0",
            "error":{
                "code":-32602,
                "message":"Invalid params: invalid type: string \"test\", expected a sequence."
            },
            "id":1052566100
        }
    """.trimIndent()

    @Test
    fun `should deserialize scale json`() {
        val response = mapper.fromJson(SCALE_JSON, RpcResponse::class.java)

        assert(response.result == SCALE_ENCODED_RESULT)
    }

    @Test
    fun `should deserialize error json`() {
        val response = mapper.fromJson(ERROR_JSON, RpcResponse::class.java)

        assert(response.error != null)
    }
}