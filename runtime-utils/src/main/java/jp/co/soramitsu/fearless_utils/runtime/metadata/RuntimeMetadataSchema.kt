package jp.co.soramitsu.fearless_utils.runtime.metadata

import jp.co.soramitsu.fearless_utils.hash.Hasher
import jp.co.soramitsu.fearless_utils.hash.Hasher.blake2b128
import jp.co.soramitsu.fearless_utils.hash.Hasher.blake2b128Concat
import jp.co.soramitsu.fearless_utils.hash.Hasher.blake2b256
import jp.co.soramitsu.fearless_utils.hash.hashConcat
import jp.co.soramitsu.fearless_utils.scale.EncodableStruct
import jp.co.soramitsu.fearless_utils.scale.Schema
import jp.co.soramitsu.fearless_utils.scale.bool
import jp.co.soramitsu.fearless_utils.scale.byteArray
import jp.co.soramitsu.fearless_utils.scale.dataType.EnumType
import jp.co.soramitsu.fearless_utils.scale.dataType.scalable
import jp.co.soramitsu.fearless_utils.scale.enum
import jp.co.soramitsu.fearless_utils.scale.schema
import jp.co.soramitsu.fearless_utils.scale.string
import jp.co.soramitsu.fearless_utils.scale.uint8
import jp.co.soramitsu.fearless_utils.scale.vector
import jp.co.soramitsu.fearless_utils.scale.dataType.string as stringType

object RuntimeMetadataSchema : Schema<RuntimeMetadataSchema>() {
    val modules by vector(ModuleMetadataSchema)

    val extrinsic by schema(ExtrinsicMetadataSchema)
}

object ModuleMetadataSchema : Schema<ModuleMetadataSchema>() {
    val name by string()

    val storage by schema(StorageMetadataSchema).optional()

    val calls by vector(FunctionMetadataSchema).optional()

    val events by vector(EventMetadataSchema).optional()

    val constants by vector(ModuleConstantMetadataSchema)

    val errors by vector(ErrorMetadataSchema)

    val index by uint8()
}

object StorageMetadataSchema : Schema<StorageMetadataSchema>() {
    val prefix by string()

    val entries by vector(StorageEntryMetadataSchema)
}

object StorageEntryMetadataSchema : Schema<StorageEntryMetadataSchema>() {
    val name by string()

    val modifier by enum(StorageEntryModifier::class)

    val type by enum(
        stringType, // plain
        scalable(MapSchema),
        scalable(DoubleMapSchema),
        scalable(NMapSchema)
    )

    val default by byteArray() // vector<u8>

    val documentation by vector(stringType)
}

enum class StorageEntryModifier {
    Optional, Default, Required
}

object MapSchema : Schema<MapSchema>() {
    val hasher by enum(StorageHasher::class)
    val key by string()
    val value by string()
    val unused by bool()
}

object DoubleMapSchema : Schema<DoubleMapSchema>() {
    val key1Hasher by enum(StorageHasher::class)
    val key1 by string()
    val key2 by string()
    val value by string()
    val key2Hasher by enum(StorageHasher::class)
}

object NMapSchema : Schema<NMapSchema>() {
    val keys by vector(stringType)
    val hashers by vector(EnumType(StorageHasher::class.java))
    val value by string()
}

enum class StorageHasher(val hashingFunction: (ByteArray) -> ByteArray) {
    Blake2_128({ it.blake2b128() }),
    Blake2_256({ it.blake2b256() }),
    Blake2_128Concat({ it.blake2b128Concat() }),
    Twox128(Hasher.xxHash128::hash),
    Twox256(Hasher.xxHash256::hash),
    Twox64Concat(Hasher.xxHash64::hashConcat),
    Identity({ it })
}

object FunctionMetadataSchema : Schema<FunctionMetadataSchema>() {
    val name by string()

    val arguments by vector(FunctionArgumentMetadataSchema)

    val documentation by vector(stringType)
}

object FunctionArgumentMetadataSchema : Schema<FunctionArgumentMetadataSchema>() {
    val name by string()

    val type by string()
}

object EventMetadataSchema : Schema<EventMetadataSchema>() {
    val name by string()

    val arguments by vector(stringType)

    val documentation by vector(stringType)
}

object ModuleConstantMetadataSchema : Schema<ModuleConstantMetadataSchema>() {
    val name by string()

    val type by string()

    val value by byteArray() // vector<u8>

    val documentation by vector(stringType)
}

object ErrorMetadataSchema : Schema<ErrorMetadataSchema>() {
    val name by string()

    val documentation by vector(stringType)
}

object ExtrinsicMetadataSchema : Schema<ExtrinsicMetadataSchema>() {
    val version by uint8()

    val signedExtensions by vector(stringType)
}

fun EncodableStruct<RuntimeMetadataSchema>.module(name: String) =
    get(RuntimeMetadataSchema.modules).find { it[ModuleMetadataSchema.name] == name }

fun EncodableStruct<ModuleMetadataSchema>.call(name: String) =
    get(ModuleMetadataSchema.calls)?.find { it[FunctionMetadataSchema.name] == name }

fun EncodableStruct<ModuleMetadataSchema>.storage(name: String) =
    get(ModuleMetadataSchema.storage)?.get(StorageMetadataSchema.entries)
        ?.find { it[StorageEntryMetadataSchema.name] == name }
