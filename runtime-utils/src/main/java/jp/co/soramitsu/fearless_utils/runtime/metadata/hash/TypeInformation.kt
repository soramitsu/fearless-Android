// Ported from merkleized-metadata 0.5.1, src/from_frame_metadata.rs (https://github.com/bkchr/merkleized-metadata).
// Copyright (c) Bastian Köcher. Licensed MIT OR Apache-2.0; used here under the Apache License 2.0.
// Recursion in the original is replaced by explicit work lists, so hostile metadata cannot overflow the stack.

package jp.co.soramitsu.fearless_utils.runtime.metadata.hash

import java.util.TreeMap
import java.util.TreeSet

/** A leaf position key, ordered like `merkle_tree::TypeId`: by new type id, then by variant index for enumerations. */
internal class LeafKey(val typeId: Long, val variant: Long?) : Comparable<LeafKey> {
    override fun compareTo(other: LeafKey): Int {
        if (variant != null && other.variant != null) {
            return if (typeId == other.typeId) variant.compareTo(other.variant) else typeId.compareTo(other.typeId)
        }
        return typeId.compareTo(other.typeId)
    }
}

/** `TypeInformation`: the merkleized extrinsic metadata and every type leaf in tree order. */
class TypeInformation internal constructor(
    val extrinsicMetadata: MerkleizedExtrinsicMetadata,
    /** The tree leaves, in the order `MerkleTree::new` inserts them. */
    val types: List<MerkleizedType>
) {
    companion object {
        /** `FrameMetadataPrepared::prepare(..).as_type_information()`. */
        fun of(metadata: FrameMetadataV15): TypeInformation = Preparation(metadata).typeInformation()
    }
}

private class Preparation(private val metadata: FrameMetadataV15) {
    private val registry = metadata.types

    init {
        // scale-info registries are closed: every reference names a type in the registry. Then the visitor that
        // `as_basic_type_ref` runs for every reference cannot fail and only its result for a compact matters, so it
        // runs for compacts only. A registry with a dangling reference is refused outright: no runtime produces one.
        if (!registry.all { type -> type.def.references().all(::isKnown) }) {
            throw MetadataDecodeException("The type registry references unknown types")
        }
    }

    private val accessible: TreeSet<Long> = collectAccessible()

    // `frame_id_to_id`: accessible types that become leaves get dense new ids in the order of their frame ids.
    private val newIds: Map<Long, Long> = LinkedHashMap<Long, Long>().also { ids ->
        accessible.filter { isBasicType(typeAt(it)) }.forEach { ids[it] = ids.size.toLong() }
    }

    fun typeInformation(): TypeInformation {
        val extrinsic = metadata.extrinsic
        val extrinsicMetadata = MerkleizedExtrinsicMetadata(
            version = extrinsic.version,
            addressType = typeRef(extrinsic.addressType),
            callType = typeRef(extrinsic.callType),
            signatureType = typeRef(extrinsic.signatureType),
            signedExtensions = extrinsic.signedExtensions.map { extension ->
                SignedExtensionMetadata(extension.identifier, typeRef(extension.type), typeRef(extension.additionalSigned))
            }
        )
        val leaves = TreeMap<LeafKey, MerkleizedType>()
        newIds.forEach { (frameId, newId) ->
            asBasicTypes(typeAt(frameId), newId).forEach { type ->
                val variant = (type.typeDef as? TypeDef.Enumeration)?.variant?.index
                leaves[LeafKey(newId, variant)] = type
            }
        }
        return TypeInformation(extrinsicMetadata, leaves.values.toList())
    }

    // `collect_accessible_types` from the call, address, signature and every signed extension type.
    private fun collectAccessible(): TreeSet<Long> {
        val roots = mutableListOf(metadata.extrinsic.callType, metadata.extrinsic.addressType, metadata.extrinsic.signatureType)
        metadata.extrinsic.signedExtensions.forEach { roots += it.type; roots += it.additionalSigned }
        val result = TreeSet<Long>()
        val pending = ArrayDeque(roots.asReversed())
        while (pending.isNotEmpty()) {
            val id = pending.removeLast()
            if (!result.add(id)) continue
            val type = registry.getOrNull(id) ?: throw MetadataDecodeException("Could not find type with id `$id` in the registry")
            val children = when (val def = type.def) {
                is TypeDefV15.Composite -> def.fields.map { it.type }
                is TypeDefV15.Variant -> def.variants.flatMap { variant -> variant.fields.map { it.type } }
                is TypeDefV15.Sequence -> listOf(def.typeParam)
                is TypeDefV15.Array -> listOf(def.typeParam)
                is TypeDefV15.Tuple -> def.fields
                // Primitives are no types of their own; a compact becomes a primitive reference; bit order and store
                // types are not needed.
                is TypeDefV15.Primitive, is TypeDefV15.Compact, is TypeDefV15.BitSequence -> emptyList()
            }
            children.asReversed().forEach(pending::addLast)
        }
        return result
    }

    // `AsBasicTypeRef for UntrackedSymbol`.
    private fun typeRef(id: Long): TypeRef {
        val type = registry.getOrNull(id) ?: throw MetadataDecodeException("Could not find type for id `$id`.")
        return when (val def = type.def) {
            is TypeDefV15.Primitive -> primitiveRef(def.primitive)
            is TypeDefV15.Compact -> {
                val primitives = primitivesBelow(type)
                when {
                    primitives.size > 1 -> throw MetadataDecodeException("Unexpected: $primitives")
                    primitives.size == 1 -> compactRef(primitives.single())
                    else -> TypeRef.Void
                }
            }
            is TypeDefV15.Variant -> if (def.variants.isEmpty()) TypeRef.Void else byId(id)
            is TypeDefV15.Composite -> if (def.fields.isEmpty()) TypeRef.Void else byId(id)
            is TypeDefV15.Tuple -> if (def.fields.isEmpty()) TypeRef.Void else byId(id)
            else -> byId(id)
        }
    }

    private fun byId(frameId: Long): TypeRef =
        TypeRef.ById(newIds[frameId] ?: throw MetadataDecodeException("Type `$frameId` is referenced but not accessible"))

    // `AsBasicType for Type`: no leaf for primitives, compacts and empty types; one leaf per variant, sorted by index.
    private fun asBasicTypes(type: PortableTypeV15, newId: Long): List<MerkleizedType> {
        val def: TypeDef = when (val frame = type.def) {
            is TypeDefV15.Compact, is TypeDefV15.Primitive -> return emptyList()
            is TypeDefV15.Composite -> {
                if (frame.fields.isEmpty()) return emptyList()
                TypeDef.Composite(frame.fields.map(::field))
            }
            is TypeDefV15.Variant -> {
                if (frame.variants.isEmpty()) return emptyList()
                return frame.variants.sortedBy { it.index }.map { variant ->
                    MerkleizedType(type.path, TypeDef.Enumeration(variant(variant)), newId)
                }
            }
            is TypeDefV15.Tuple -> {
                if (frame.fields.isEmpty()) return emptyList()
                TypeDef.Tuple(frame.fields.map(::typeRef))
            }
            is TypeDefV15.Array -> TypeDef.Array(frame.length, typeRef(frame.typeParam))
            is TypeDefV15.Sequence -> TypeDef.Sequence(typeRef(frame.typeParam))
            is TypeDefV15.BitSequence -> bitSequence(frame)
        }
        return listOf(MerkleizedType(type.path, def, newId))
    }

    private fun field(field: FieldV15) = Field(field.name, typeRef(field.type), field.typeName)

    private fun variant(variant: VariantV15) = EnumerationVariant(variant.name, variant.fields.map(::field), variant.index.toLong())

    // `AsBasicType for TypeDefBitSequence`.
    private fun bitSequence(def: TypeDefV15.BitSequence): TypeDef.BitSequence {
        val store = registry.getOrNull(def.bitStoreType) ?: throw MetadataDecodeException("Could not find type for id `${def.bitStoreType}`.")
        val primitives = primitivesBelow(store)
        if (primitives.size != 1) {
            throw MetadataDecodeException("Only expected to find `1` primitive type as store type for `BitSequence`")
        }
        val numBytes = when (primitives.single()) {
            PrimitiveV15.U8 -> 1
            PrimitiveV15.U16 -> 2
            PrimitiveV15.U32 -> 4
            PrimitiveV15.U64 -> 8
            else -> throw MetadataDecodeException("Invalid primitive type as store type for `BitSequence`")
        }
        val order = registry.getOrNull(def.bitOrderType) ?: throw MetadataDecodeException("Could not find type for id `${def.bitOrderType}`.")
        val orderName = order.path.firstOrNull { it == "Lsb0" || it == "Msb0" }
            ?: throw MetadataDecodeException("`bit_order_type` should be either `Lsb0` or `Msb0`")
        return TypeDef.BitSequence(numBytes, orderName == "Lsb0")
    }

    // `CollectPrimitives`: the primitives reachable from [root], each type visited once. The root itself is not marked
    // as visited before its children, exactly like the original visitor.
    private fun primitivesBelow(root: PortableTypeV15): List<PrimitiveV15> {
        val found = mutableListOf<PrimitiveV15>()
        val visited = HashSet<Long>()
        val pending = ArrayDeque<PortableTypeV15>()
        pending.addLast(root)
        while (pending.isNotEmpty()) {
            val def = pending.removeLast().def
            if (def is TypeDefV15.Primitive) found += def.primitive
            def.references().asReversed().forEach { child ->
                if (visited.add(child)) {
                    pending.addLast(registry.getOrNull(child) ?: throw MetadataDecodeException("Could not find type for id `$child`."))
                }
            }
        }
        return found
    }

    private fun isBasicType(type: PortableTypeV15): Boolean = when (val def = type.def) {
        is TypeDefV15.Compact, is TypeDefV15.Primitive -> false
        is TypeDefV15.Variant -> def.variants.isNotEmpty()
        is TypeDefV15.Composite -> def.fields.isNotEmpty()
        is TypeDefV15.Tuple -> def.fields.isNotEmpty()
        else -> true
    }

    private fun typeAt(id: Long): PortableTypeV15 = registry.getOrNull(id) ?: throw MetadataDecodeException("Could not find type `$id`")

    private fun isKnown(id: Long) = id >= 0 && id < registry.size

    private fun primitiveRef(primitive: PrimitiveV15): TypeRef = when (primitive) {
        PrimitiveV15.Bool -> TypeRef.Bool
        PrimitiveV15.Char -> TypeRef.Char
        PrimitiveV15.Str -> TypeRef.Str
        PrimitiveV15.U8 -> TypeRef.U8
        PrimitiveV15.U16 -> TypeRef.U16
        PrimitiveV15.U32 -> TypeRef.U32
        PrimitiveV15.U64 -> TypeRef.U64
        PrimitiveV15.U128 -> TypeRef.U128
        PrimitiveV15.U256 -> TypeRef.U256
        PrimitiveV15.I8 -> TypeRef.I8
        PrimitiveV15.I16 -> TypeRef.I16
        PrimitiveV15.I32 -> TypeRef.I32
        PrimitiveV15.I64 -> TypeRef.I64
        PrimitiveV15.I128 -> TypeRef.I128
        PrimitiveV15.I256 -> TypeRef.I256
    }

    private fun compactRef(primitive: PrimitiveV15): TypeRef = when (primitive) {
        PrimitiveV15.U8 -> TypeRef.CompactU8
        PrimitiveV15.U16 -> TypeRef.CompactU16
        PrimitiveV15.U32 -> TypeRef.CompactU32
        PrimitiveV15.U64 -> TypeRef.CompactU64
        PrimitiveV15.U128 -> TypeRef.CompactU128
        PrimitiveV15.U256 -> TypeRef.CompactU256
        else -> throw MetadataDecodeException("Unsupported primitive type for `Compact`: $primitive")
    }
}

// The order the original visitor walks children in: fields, then the order type before the store type.
private fun TypeDefV15.references(): List<Long> = when (this) {
    is TypeDefV15.Composite -> fields.map { it.type }
    is TypeDefV15.Variant -> variants.flatMap { variant -> variant.fields.map { it.type } }
    is TypeDefV15.Sequence -> listOf(typeParam)
    is TypeDefV15.Array -> listOf(typeParam)
    is TypeDefV15.Tuple -> fields
    is TypeDefV15.Compact -> listOf(typeParam)
    is TypeDefV15.BitSequence -> listOf(bitOrderType, bitStoreType)
    is TypeDefV15.Primitive -> emptyList()
}

private fun <T> List<T>.getOrNull(id: Long): T? = if (id in 0 until size) get(id.toInt()) else null
