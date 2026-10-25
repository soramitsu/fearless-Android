package jp.co.soramitsu.fearless_utils.runtime.metadata.v14

import jp.co.soramitsu.fearless_utils.runtime.metadata.StorageEntryModifier
import jp.co.soramitsu.fearless_utils.runtime.metadata.StorageHasher
import jp.co.soramitsu.fearless_utils.scale.Schema
import jp.co.soramitsu.fearless_utils.scale.byteArray
import jp.co.soramitsu.fearless_utils.scale.compactInt
import jp.co.soramitsu.fearless_utils.scale.dataType.EnumType
import jp.co.soramitsu.fearless_utils.scale.dataType.scalable
import jp.co.soramitsu.fearless_utils.scale.enum
import jp.co.soramitsu.fearless_utils.scale.schema
import jp.co.soramitsu.fearless_utils.scale.string
import jp.co.soramitsu.fearless_utils.scale.uint8
import jp.co.soramitsu.fearless_utils.scale.vector

object PalletMetadataV14 : Schema<PalletMetadataV14>() {
    val name by string()
    val storage by schema(StorageMetadataV14).optional()
    val calls by schema(PalletCallMetadataV14).optional()
    val events by schema(PalletEventMetadataV14).optional()
    val constants by vector(PalletConstantMetadataV14)
    val errors by schema(PalletErrorMetadataV14).optional()
    val index by uint8()
}

object StorageMetadataV14 : Schema<StorageMetadataV14>() {
    val prefix by string()
    val entries by vector(StorageEntryMetadataV14)
}

object StorageEntryMetadataV14 : Schema<StorageEntryMetadataV14>() {
    val name by string()
    val modifier by enum(StorageEntryModifier::class)
    val type by enum(
        jp.co.soramitsu.fearless_utils.scale.dataType.compactInt,
        scalable(MapTypeV14)
    )
    val default by byteArray()
    val documentation by vector(jp.co.soramitsu.fearless_utils.scale.dataType.string)
}

object MapTypeV14 : Schema<MapTypeV14>() {
    val hashers by vector(EnumType(StorageHasher::class.java))
    val key by compactInt()
    val value by compactInt()
}

object PalletCallMetadataV14 : Schema<PalletCallMetadataV14>() {
    val type by compactInt()
}

object PalletEventMetadataV14 : Schema<PalletEventMetadataV14>() {
    val type by compactInt()
}

object PalletErrorMetadataV14 : Schema<PalletErrorMetadataV14>() {
    val type by compactInt()
}

object PalletConstantMetadataV14 : Schema<PalletConstantMetadataV14>() {
    val name by string()
    val type by compactInt()
    val value by byteArray() // vector<u8>
    val documentation by vector(jp.co.soramitsu.fearless_utils.scale.dataType.string)
}

object ExtrinsicMetadataV14 : Schema<ExtrinsicMetadataV14>() {
    val type by compactInt()
    val version by uint8()
    val signedExtensions by vector(SignedExtensionMetadataV14)
}

object SignedExtensionMetadataV14 : Schema<SignedExtensionMetadataV14>() {
    val identifier by string()
    val type by compactInt()
    val additionalSigned by compactInt()
}
