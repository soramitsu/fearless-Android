// Ported from merkleized-metadata 0.5.1, src/types.rs (https://github.com/bkchr/merkleized-metadata).
// Copyright (c) Bastian Köcher. Licensed MIT OR Apache-2.0; used here under the Apache License 2.0.
// The SCALE layouts below are the `#[derive(Encode)]` layouts of that file; the hash of each value is BLAKE3-256
// of its encoding, as RFC 78 (https://polkadot-fellows.github.io/RFCs/approved/0078-merkleized-metadata.html) defines.

package jp.co.soramitsu.fearless_utils.runtime.metadata.hash

/** `types::TypeRef`: a primitive, compact, void or a reference to a merkleized type by its new id. */
sealed class TypeRef(internal val index: Int) {
    object Bool : TypeRef(0)
    object Char : TypeRef(1)
    object Str : TypeRef(2)
    object U8 : TypeRef(3)
    object U16 : TypeRef(4)
    object U32 : TypeRef(5)
    object U64 : TypeRef(6)
    object U128 : TypeRef(7)
    object U256 : TypeRef(8)
    object I8 : TypeRef(9)
    object I16 : TypeRef(10)
    object I32 : TypeRef(11)
    object I64 : TypeRef(12)
    object I128 : TypeRef(13)
    object I256 : TypeRef(14)
    object CompactU8 : TypeRef(15)
    object CompactU16 : TypeRef(16)
    object CompactU32 : TypeRef(17)
    object CompactU64 : TypeRef(18)
    object CompactU128 : TypeRef(19)
    object CompactU256 : TypeRef(20)
    object Void : TypeRef(21)
    class ById(val id: Long) : TypeRef(22) {
        override fun equals(other: Any?) = other is ById && other.id == id
        override fun hashCode() = id.hashCode()
        override fun toString() = "ById($id)"
    }

    override fun toString(): String = javaClass.simpleName

    internal fun encodeTo(output: ScaleOutput) {
        output.writeU8(index)
        if (this is ById) output.writeCompactU32(id)
    }
}

class Field(val name: String?, val type: TypeRef, val typeName: String?) {
    internal fun encodeTo(output: ScaleOutput) {
        output.writeOptionString(name)
        type.encodeTo(output)
        output.writeOptionString(typeName)
    }
}

class EnumerationVariant(val name: String, val fields: List<Field>, val index: Long) {
    internal fun encodeTo(output: ScaleOutput) {
        output.writeString(name)
        output.writeVec(fields) { it.encodeTo(this) }
        output.writeCompactU32(index)
    }
}

/** `types::TypeDef`. */
sealed class TypeDef(private val index: Int) {
    class Composite(val fields: List<Field>) : TypeDef(0)
    class Enumeration(val variant: EnumerationVariant) : TypeDef(1)
    class Sequence(val type: TypeRef) : TypeDef(2)
    class Array(val length: Long, val type: TypeRef) : TypeDef(3)
    class Tuple(val types: List<TypeRef>) : TypeDef(4)
    class BitSequence(val numBytes: Int, val leastSignificantBitFirst: Boolean) : TypeDef(5)

    internal fun encodeTo(output: ScaleOutput) {
        output.writeU8(index)
        when (this) {
            is Composite -> output.writeVec(fields) { it.encodeTo(this) }
            is Enumeration -> variant.encodeTo(output)
            is Sequence -> type.encodeTo(output)
            is Array -> {
                output.writeU32(length)
                type.encodeTo(output)
            }
            is Tuple -> output.writeVec(types) { it.encodeTo(this) }
            is BitSequence -> output.writeU8(numBytes).writeBool(leastSignificantBitFirst)
        }
    }
}

/** `types::Type`: one leaf of the type tree. An enumeration contributes one leaf per variant. */
class MerkleizedType(val path: List<String>, val typeDef: TypeDef, val typeId: Long) {
    fun encode(): ByteArray = ScaleOutput().also { output ->
        output.writeVec(path) { writeString(it) }
        typeDef.encodeTo(output)
        output.writeCompactU32(typeId)
    }.toByteArray()

    fun hash(): ByteArray = Blake3.hash256(encode())
}

class SignedExtensionMetadata(
    val identifier: String,
    val includedInExtrinsic: TypeRef,
    val includedInSignedData: TypeRef
)

/** `types::ExtrinsicMetadata`: the extrinsic format in merkleized form. */
class MerkleizedExtrinsicMetadata(
    val version: Int,
    val addressType: TypeRef,
    val callType: TypeRef,
    val signatureType: TypeRef,
    val signedExtensions: List<SignedExtensionMetadata>
) {
    fun encode(): ByteArray = ScaleOutput().also { output ->
        output.writeU8(version)
        addressType.encodeTo(output)
        callType.encodeTo(output)
        signatureType.encodeTo(output)
        output.writeVec(signedExtensions) { extension ->
            writeString(extension.identifier)
            extension.includedInExtrinsic.encodeTo(this)
            extension.includedInSignedData.encodeTo(this)
        }
    }.toByteArray()

    fun hash(): ByteArray = Blake3.hash256(encode())
}
