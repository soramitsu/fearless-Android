package jp.co.soramitsu.fearless_utils.runtime.metadata.hash

import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

/** Synthetic V15 registries for the cases real metadata never contains. */
class TypeInformationTest {

    // A V15 blob whose call, address, signature and extra types are all type 0.
    private fun metadata(writeTypes: ScaleOutput.() -> Unit): FrameMetadataV15 {
        val output = ScaleOutput()
            .writeU32(0x6174656d)
            .writeU8(15)
        output.writeTypes()
        output.writeCompactU32(0) // pallets
        output.writeU8(4).writeCompactU32(0).writeCompactU32(0).writeCompactU32(0).writeCompactU32(0).writeCompactU32(0)
        output.writeCompactU32(0) // runtime type
        output.writeCompactU32(0) // apis
        output.writeCompactU32(0).writeCompactU32(0).writeCompactU32(0) // outer enums
        output.writeCompactU32(0) // custom
        return FrameMetadataV15.decode(output.toByteArray())
    }

    // type: id, path, no params, definition, no docs
    private fun ScaleOutput.type(id: Long, path: List<String>, definition: ScaleOutput.() -> Unit) {
        writeCompactU32(id)
        writeVec(path) { writeString(it) }
        writeCompactU32(0)
        definition()
        writeCompactU32(0)
    }

    private fun ScaleOutput.field(type: Long) {
        writeU8(0) // no name
        writeCompactU32(type)
        writeU8(0) // no type name
        writeCompactU32(0) // no docs
    }

    @Test
    fun `a registry with a dangling reference is refused`() {
        val metadata = metadata {
            writeCompactU32(1)
            type(0, listOf("A")) { writeU8(0); writeCompactU32(1); field(5) }
        }
        assertThrows(MetadataDecodeException::class.java) { TypeInformation.of(metadata) }
    }

    @Test
    fun `a compact over more than one primitive is refused`() {
        val metadata = metadata {
            writeCompactU32(4)
            type(0, listOf("Root")) { writeU8(0); writeCompactU32(1); field(1) }
            type(1, emptyList()) { writeU8(6); writeCompactU32(2) } // Compact<Pair>
            type(2, listOf("Pair")) { writeU8(0); writeCompactU32(2); field(3); field(3 + 0) }
            type(3, emptyList()) { writeU8(5); writeU8(5) } // u32
        }
        // The same primitive type twice is one visited type: Compact<(u32, u32)> resolves to CompactU32.
        val information = TypeInformation.of(metadata)
        assertEquals(TypeRef.CompactU32, (information.types.single().typeDef as TypeDef.Composite).fields.single().type)

        val twoPrimitives = metadata {
            writeCompactU32(5)
            type(0, listOf("Root")) { writeU8(0); writeCompactU32(1); field(1) }
            type(1, emptyList()) { writeU8(6); writeCompactU32(2) }
            type(2, listOf("Pair")) { writeU8(0); writeCompactU32(2); field(3); field(4) }
            type(3, emptyList()) { writeU8(5); writeU8(5) } // u32
            type(4, emptyList()) { writeU8(5); writeU8(6) } // u64
        }
        assertThrows(MetadataDecodeException::class.java) { TypeInformation.of(twoPrimitives) }
    }

    @Test
    fun `enumerations give one leaf per variant in index order and empty types give none`() {
        val metadata = metadata {
            writeCompactU32(3)
            // type 0: enum { B = 7 (u8), A = 2 } given out of order
            type(0, listOf("E")) {
                writeU8(1)
                writeCompactU32(2)
                writeString("B"); writeCompactU32(1); field(1); writeU8(7); writeCompactU32(0)
                writeString("A"); writeCompactU32(0); writeU8(2); writeCompactU32(0)
            }
            type(1, emptyList()) { writeU8(5); writeU8(3) } // u8
            type(2, listOf("Empty")) { writeU8(0); writeCompactU32(0) }
        }
        val information = TypeInformation.of(metadata)
        val variants = information.types.map { (it.typeDef as TypeDef.Enumeration).variant }
        assertEquals(listOf("A", "B"), variants.map { it.name })
        assertEquals(listOf(2L, 7L), variants.map { it.index })
        assertTrue(information.types.all { it.typeId == 0L })
        assertEquals(TypeRef.ById(0), information.extrinsicMetadata.callType)
    }
}
