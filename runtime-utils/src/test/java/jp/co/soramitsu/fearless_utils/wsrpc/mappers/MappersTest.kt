package jp.co.soramitsu.fearless_utils.wsrpc.mappers

import com.google.gson.Gson
import jp.co.soramitsu.fearless_utils.common.assertThrows
import jp.co.soramitsu.fearless_utils.scale.Schema
import jp.co.soramitsu.fearless_utils.scale.bool
import jp.co.soramitsu.fearless_utils.scale.invoke
import jp.co.soramitsu.fearless_utils.scale.toHexString
import jp.co.soramitsu.fearless_utils.scale.uint32
import jp.co.soramitsu.fearless_utils.wsrpc.exception.RpcException
import jp.co.soramitsu.fearless_utils.wsrpc.response.RpcError
import jp.co.soramitsu.fearless_utils.wsrpc.response.RpcResponse
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.junit.MockitoJUnitRunner

private class TestPojo(val a: Int, val b: Boolean)

private object TestSchema : Schema<TestSchema>() {
    val a by uint32()

    val b by bool()
}

@RunWith(MockitoJUnitRunner::class)
class MappersTest {
    private val gson = Gson()

    @Test
    fun `should map boolean response`() {
        val body = true
        val response = createSuccessResponse(body)

        val mapped = pojo<Boolean>().nonNull().map(response, gson)

        assertEquals(body, mapped)
    }

    @Test
    fun `should map string response`() {
        val body = "Test"
        val response = createSuccessResponse(body)

        val mapped = pojo<String>().nonNull().map(response, gson)

        assertEquals(body, mapped)
    }

    @Test
    fun `should map nullable response`() {
        val body = null
        val response = createSuccessResponse(body)

        val mapped = pojo<String>().map(response, gson)

        assertNull(mapped.result)
    }

    @Test
    fun `should throw on null non-null response`() {
        val error = "test"
        val response = createErrorResponse(error)

        val exception = assertThrows<RpcException> {
            pojo<String>().nonNull().map(response, gson)
        }

        assertEquals(error, exception.message)
    }

    @Test
    fun `should map pojo response`() {
        val a = 1
        val b = true

        val body = mapOf(
            "a" to a,
            "b" to b
        )

        val response = createSuccessResponse(body)

        val mapped = pojo<TestPojo>().nonNull().map(response, gson)

        assertEquals(mapped.a, a)
        assertEquals(mapped.b, b)
    }

    @Test
    fun `should map scale response`() {
        val a = 1U
        val b = true

        val body = TestSchema {
            it[TestSchema.a] = a
            it[TestSchema.b] = b
        }.toHexString()

        val response = createSuccessResponse(body)

        val mapped = scale(TestSchema).nonNull().map(response, gson)

        assertEquals(mapped[TestSchema.a], a)
        assertEquals(mapped[TestSchema.b], b)
    }

    @Test
    fun `should map scale collection response`() {
        val a = 1U
        val b = true

        val body = listOf(
            TestSchema {
                it[TestSchema.a] = a
                it[TestSchema.b] = b
            }.toHexString()
        )

        val response = createSuccessResponse(body)

        val mapped = scaleCollection(TestSchema).nonNull().map(response, gson)

        assertEquals(mapped.size, 1)
        assertEquals(mapped.first()[TestSchema.a], a)
        assertEquals(mapped.first()[TestSchema.b], b)
    }

    private fun createSuccessResponse(body: Any?): RpcResponse =
        RpcResponse(jsonrpc = "2.0", result = body, id = 0, error = null)

    private fun createErrorResponse(error: String): RpcResponse = RpcResponse(
        jsonrpc = "2.0",
        result = null,
        id = 0,
        error = RpcError(
            code = 0,
            message = error
        )
    )
}