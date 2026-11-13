package jp.co.soramitsu.common.data.network.rpc

import com.google.gson.Gson
import org.junit.Assert.assertEquals
import org.junit.Test

/** `state_getKeysPaged(prefix, count, startKey?, at?)` reads its parameters by position. */
class GetKeysPagedRequestTest {
    private fun params(request: GetKeysPagedRequest) = Gson().toJson(request.params)

    @Test
    fun `first page at a block keeps the block in the block position`() {
        assertEquals("""["0x5f3e",1000,null,"0xblock"]""", params(GetKeysPagedRequest("0x5f3e", 1000, null, "0xblock")))
    }

    @Test
    fun `later page at a block passes the start key before the block`() {
        assertEquals("""["0x5f3e",1000,"0x5f3e01","0xblock"]""", params(GetKeysPagedRequest("0x5f3e", 1000, "0x5f3e01", "0xblock")))
    }

    @Test
    fun `requests without a block keep their previous shape`() {
        assertEquals("""["0x5f3e",1000]""", params(GetKeysPagedRequest("0x5f3e", 1000, null, null)))
        assertEquals("""["0x5f3e",1000,"0x5f3e01"]""", params(GetKeysPagedRequest("0x5f3e", 1000, "0x5f3e01", null)))
    }
}
