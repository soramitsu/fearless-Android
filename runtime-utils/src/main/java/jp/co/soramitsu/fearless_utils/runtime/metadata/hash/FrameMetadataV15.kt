package jp.co.soramitsu.fearless_utils.runtime.metadata.hash

/**
 * The parts of FRAME metadata V15 (`frame-metadata` crate, `RuntimeMetadataPrefixed` with version 15) that the RFC 78
 * metadata hash and its extra information are computed from. [decode] reads and checks the whole structure, the parts
 * kept here and the ones it skips, so the hash is only ever computed from one complete, well-formed metadata blob.
 */
class FrameMetadataV15 internal constructor(
    /** The portable type registry. A type id is its position in this list, as in `scale-info`. */
    val types: List<PortableTypeV15>,
    val pallets: List<PalletV15>,
    val extrinsic: ExtrinsicMetadataV15
) {
    /** The value of a pallet constant, or null when the pallet or the constant does not exist. */
    fun constant(pallet: String, name: String): ByteArray? =
        pallets.firstOrNull { it.name == pallet }?.constants?.firstOrNull { it.name == name }?.value

    companion object {
        private const val MAGIC: Long = 0x6174656d // "meta", little-endian
        private const val VERSION = 15

        /** Decodes `RuntimeMetadataPrefixed` bytes. Anything but a complete V15 blob is rejected. */
        fun decode(bytes: ByteArray): FrameMetadataV15 {
            val input = ScaleInput(bytes)
            if (input.readU32() != MAGIC) throw MetadataDecodeException("Not runtime metadata")
            val version = input.readU8()
            if (version != VERSION) throw MetadataDecodeException("Metadata version $version is not V15")
            val types = input.readVec { readPortableType() }
            val pallets = input.readVec { readPallet() }
            val extrinsic = input.readExtrinsic()
            input.readCompactU32() // the Runtime type
            input.readVec { readRuntimeApi() }
            repeat(OUTER_ENUMS) { input.readCompactU32() } // call, event and error enum types
            input.readVec { readString(); readCompactU32(); readByteVec() } // custom values
            input.requireEnd()
            return FrameMetadataV15(types, pallets, extrinsic)
        }

        /**
         * Decodes the result of `state_call("Metadata_metadata_at_version", 15u32)`: an `Option<OpaqueMetadata>`.
         * Null when the runtime answers `None`, that is, it does not provide V15.
         */
        fun decodeVersionedResult(bytes: ByteArray): FrameMetadataV15? = unwrapVersionedResult(bytes)?.let(::decode)

        /** The `RuntimeMetadataPrefixed` bytes inside an `Option<OpaqueMetadata>` runtime call result. */
        fun unwrapVersionedResult(bytes: ByteArray): ByteArray? {
            val input = ScaleInput(bytes)
            val metadata = input.readOption { readByteVec() }
            input.requireEnd()
            return metadata
        }

        private const val OUTER_ENUMS = 3

        private fun ScaleInput.readPortableType(): PortableTypeV15 {
            readCompactU32() // The id field. scale-info ids are positions; the hash uses positions too.
            val path = readVec { readString() }
            readVec { readString(); readOption { readCompactU32() } } // type parameters
            val def = readTypeDef()
            readVec { readString() } // docs
            return PortableTypeV15(path, def)
        }

        private fun ScaleInput.readTypeDef(): TypeDefV15 = when (val index = readU8()) {
            0 -> TypeDefV15.Composite(readVec { readField() })
            1 -> TypeDefV15.Variant(readVec { readVariant() })
            2 -> TypeDefV15.Sequence(readCompactU32())
            3 -> {
                val length = readU32()
                TypeDefV15.Array(length, readCompactU32())
            }
            4 -> TypeDefV15.Tuple(readVec { readCompactU32() })
            5 -> TypeDefV15.Primitive(PrimitiveV15.entries.getOrNull(readU8()) ?: throw MetadataDecodeException("Unknown primitive"))
            6 -> TypeDefV15.Compact(readCompactU32())
            7 -> {
                val store = readCompactU32()
                TypeDefV15.BitSequence(store, readCompactU32())
            }
            else -> throw MetadataDecodeException("Unknown type definition $index")
        }

        private fun ScaleInput.readField(): FieldV15 {
            val name = readOption { readString() }
            val type = readCompactU32()
            val typeName = readOption { readString() }
            readVec { readString() } // docs
            return FieldV15(name, type, typeName)
        }

        private fun ScaleInput.readVariant(): VariantV15 {
            val name = readString()
            val fields = readVec { readField() }
            val index = readU8()
            readVec { readString() } // docs
            return VariantV15(name, fields, index)
        }

        private fun ScaleInput.readPallet(): PalletV15 {
            val name = readString()
            readOption { readStorage() }
            readOption { readCompactU32() } // calls
            readOption { readCompactU32() } // event
            val constants = readVec { readConstant() }
            readOption { readCompactU32() } // error
            val index = readU8()
            readVec { readString() } // docs
            return PalletV15(name, index, constants)
        }

        private fun ScaleInput.readStorage() {
            readString() // prefix
            readVec {
                readString() // name
                if (readU8() > 1) throw MetadataDecodeException("Unknown storage modifier")
                when (readU8()) {
                    0 -> readCompactU32() // plain
                    1 -> {
                        readVec { if (readU8() > MAX_STORAGE_HASHER) throw MetadataDecodeException("Unknown storage hasher") }
                        readCompactU32() // key
                        readCompactU32() // value
                    }
                    else -> throw MetadataDecodeException("Unknown storage entry type")
                }
                readByteVec() // default
                readVec { readString() } // docs
            }
        }

        private const val MAX_STORAGE_HASHER = 6

        private fun ScaleInput.readConstant(): ConstantV15 {
            val name = readString()
            val type = readCompactU32()
            val value = readByteVec()
            readVec { readString() } // docs
            return ConstantV15(name, type, value)
        }

        private fun ScaleInput.readExtrinsic(): ExtrinsicMetadataV15 {
            val version = readU8()
            val address = readCompactU32()
            val call = readCompactU32()
            val signature = readCompactU32()
            val extra = readCompactU32()
            val extensions = readVec {
                val identifier = readString()
                val type = readCompactU32()
                SignedExtensionV15(identifier, type, readCompactU32())
            }
            return ExtrinsicMetadataV15(version, address, call, signature, extra, extensions)
        }

        private fun ScaleInput.readRuntimeApi() {
            readString() // name
            readVec {
                readString() // method name
                readVec { readString(); readCompactU32() } // inputs
                readCompactU32() // output
                readVec { readString() } // docs
            }
            readVec { readString() } // docs
        }
    }
}

class PortableTypeV15 internal constructor(val path: List<String>, val def: TypeDefV15)

/** `scale_info::TypeDef` in portable form. Type ids are kept as read: a `Compact<u32>` may exceed `Int`. */
sealed class TypeDefV15 {
    class Composite(val fields: List<FieldV15>) : TypeDefV15()
    class Variant(val variants: List<VariantV15>) : TypeDefV15()
    class Sequence(val typeParam: Long) : TypeDefV15()
    class Array(val length: Long, val typeParam: Long) : TypeDefV15()
    class Tuple(val fields: List<Long>) : TypeDefV15()
    class Primitive(val primitive: PrimitiveV15) : TypeDefV15()
    class Compact(val typeParam: Long) : TypeDefV15()
    class BitSequence(val bitStoreType: Long, val bitOrderType: Long) : TypeDefV15()
}

class FieldV15 internal constructor(val name: String?, val type: Long, val typeName: String?)

class VariantV15 internal constructor(val name: String, val fields: List<FieldV15>, val index: Int)

/** `scale_info::TypeDefPrimitive`, in encoding order. */
enum class PrimitiveV15 { Bool, Char, Str, U8, U16, U32, U64, U128, U256, I8, I16, I32, I64, I128, I256 }

class PalletV15 internal constructor(val name: String, val index: Int, val constants: List<ConstantV15>)

class ConstantV15 internal constructor(val name: String, val type: Long, val value: ByteArray)

class ExtrinsicMetadataV15 internal constructor(
    val version: Int,
    val addressType: Long,
    val callType: Long,
    val signatureType: Long,
    val extraType: Long,
    val signedExtensions: List<SignedExtensionV15>
)

class SignedExtensionV15 internal constructor(val identifier: String, val type: Long, val additionalSigned: Long)
