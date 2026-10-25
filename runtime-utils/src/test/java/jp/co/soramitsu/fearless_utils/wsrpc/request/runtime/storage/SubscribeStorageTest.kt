package jp.co.soramitsu.fearless_utils.wsrpc.request.runtime.storage

import jp.co.soramitsu.fearless_utils.common.assertThrows
import jp.co.soramitsu.fearless_utils.wsrpc.request.runtime.createFakeChange
import org.junit.Assert.assertEquals
import org.junit.Test
import java.lang.IllegalArgumentException

class SubscribeStorageTest {

    @Test
    fun `should transform valid storage change`() {
        val change = createFakeChange(
            mapOf(
                "block" to "block",
                "changes" to listOf(listOf("key", "change"))
            )
        )

        val storageChange = change.storageChange()

        assertEquals("block", storageChange.block)
        assertEquals("change", storageChange.getSingleChange())
    }

    @Test
    fun `should throw on invalid storage change`() {
        val change = createFakeChange(
            mapOf(
                "block" to "block",
                "changes" to 1
            )
        )

        assertThrows<IllegalArgumentException> {
            change.storageChange()
        }
    }
}