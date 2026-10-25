package jp.co.soramitsu.fearless_utils.runtime.definitions.types.generics

import jp.co.soramitsu.fearless_utils.common.assertThrows
import org.junit.Assert.assertEquals
import org.junit.Test

class HashTest {

    @Test
    fun `should have valid name`() {
        val hash = Hash(256)

        assertEquals(hash.name, "H256")
    }

    @Test
    fun `should require integer bytes`() {
        assertThrows<IllegalArgumentException> {
            Hash(129)
        }
    }

    @Test
    fun `should have valid length in bytes`() {
        val hash = Hash(256)

        assertEquals(hash.length, 32)
    }
}