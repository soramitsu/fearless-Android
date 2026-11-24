package jp.co.soramitsu.fearless_utils.runtime.metadata.hash

import jp.co.soramitsu.fearless_utils.extensions.toHexString
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Test

class MetadataMerkleTreeTest {

    private fun leaf(byte: Int) = ByteArray(32) { byte.toByte() }

    private fun node(left: ByteArray, right: ByteArray) = Blake3.hash256(left + right)

    @Test
    fun `no leaves give a zero root and one leaf is its own root`() {
        assertArrayEquals(ByteArray(32), MetadataMerkleTree.rootOfLeafHashes(emptyList()))
        assertArrayEquals(leaf(7), MetadataMerkleTree.rootOfLeafHashes(listOf(leaf(7))))
    }

    @Test
    fun `nodes are joined from the back and pushed to the front`() {
        val (a, b, c) = Triple(leaf(1), leaf(2), leaf(3))
        // [a, b, c] -> [H(b,c), a] -> H(H(b,c), a)
        assertArrayEquals(node(node(b, c), a), MetadataMerkleTree.rootOfLeafHashes(listOf(a, b, c)))
        val d = leaf(4)
        // [a, b, c, d] -> [H(c,d), a, b] -> [H(a,b), H(c,d)] -> H(H(a,b), H(c,d))
        assertArrayEquals(node(node(a, b), node(c, d)), MetadataMerkleTree.rootOfLeafHashes(listOf(a, b, c, d)))
    }

    @Test
    fun `a type leaf hashes its SCALE encoding`() {
        val type = MerkleizedType(listOf("a"), TypeDef.Sequence(TypeRef.ById(300)), 5)
        // path ["a"], Sequence(ById(300)), type id 5
        assertEquals("0x0404610216b104" + "14", type.encode().toHexString(true))
        assertArrayEquals(Blake3.hash256(type.encode()), type.hash())
        assertArrayEquals(type.hash(), MetadataMerkleTree.root(listOf(type)))
    }

    @Test
    fun `every type definition encodes with its enum index`() {
        val field = Field(name = "x", type = TypeRef.CompactU128, typeName = null)
        val cases = listOf(
            TypeDef.Composite(listOf(field)) to "0x00" + "04" + "010478" + "13" + "00",
            TypeDef.Enumeration(EnumerationVariant("V", listOf(field), 3)) to "0x01" + "0456" + "04" + "010478" + "13" + "00" + "0c",
            TypeDef.Sequence(TypeRef.Void) to "0x0215",
            TypeDef.Array(32, TypeRef.U8) to "0x032000000003",
            TypeDef.Tuple(listOf(TypeRef.Bool, TypeRef.I256)) to "0x0408000e",
            TypeDef.BitSequence(4, true) to "0x050401"
        )
        cases.forEach { (def, hex) ->
            val encoded = MerkleizedType(emptyList(), def, 0).encode()
            // Strip the empty path (00) and the type id (00) around the definition.
            assertEquals(hex, encoded.copyOfRange(1, encoded.size - 1).toHexString(true))
        }
    }
}
