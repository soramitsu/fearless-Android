package jp.co.soramitsu.fearless_utils.runtime.definitions

import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Test

class TypeDefinitionsTreeV2SerializationTest {

    @Test
    fun `missing legacy runtime id decodes to null`() {
        val tree = decode("""{"types": {}}""")

        assertNull(tree.runtimeId)
        assertEquals(0, tree.types.size)
    }

    @Test
    fun `explicit null runtime id remains compatible`() {
        val tree = decode("""{"runtime_id": null, "types": {}}""")

        assertNull(tree.runtimeId)
    }

    @Test
    fun `present runtime id remains authoritative`() {
        val tree = decode("""{"runtime_id": 9122, "types": {}}""")

        assertEquals(9122, tree.runtimeId)
    }

    @Test
    fun `malformed runtime id still fails closed`() {
        assertThrows(SerializationException::class.java) {
            decode("""{"runtime_id": {"unexpected": 9122}, "types": {}}""")
        }
    }

    private fun decode(payload: String): TypeDefinitionsTreeV2 =
        Json.decodeFromString(TypeDefinitionsTreeV2.serializer(), payload)
}
