package jp.co.soramitsu.fearless_utils.runtime.metadata.v15

import jp.co.soramitsu.fearless_utils.runtime.metadata.v14.LookupSchema
import jp.co.soramitsu.fearless_utils.runtime.metadata.v14.PalletCallMetadataV14
import jp.co.soramitsu.fearless_utils.runtime.metadata.v14.PalletConstantMetadataV14
import jp.co.soramitsu.fearless_utils.runtime.metadata.v14.PalletErrorMetadataV14
import jp.co.soramitsu.fearless_utils.runtime.metadata.v14.PalletEventMetadataV14
import jp.co.soramitsu.fearless_utils.runtime.metadata.v14.SignedExtensionMetadataV14
import jp.co.soramitsu.fearless_utils.runtime.metadata.v14.StorageMetadataV14
import jp.co.soramitsu.fearless_utils.scale.Schema
import jp.co.soramitsu.fearless_utils.scale.byteArray
import jp.co.soramitsu.fearless_utils.scale.compactInt
import jp.co.soramitsu.fearless_utils.scale.schema
import jp.co.soramitsu.fearless_utils.scale.string
import jp.co.soramitsu.fearless_utils.scale.uint8
import jp.co.soramitsu.fearless_utils.scale.vector
import jp.co.soramitsu.fearless_utils.scale.dataType.string as stringType

/**
 * `frame_metadata::v15::RuntimeMetadataV15`. Its type registry, storage, call, event, constant and error layouts are
 * those of V14; V15 adds pallet docs, the full extrinsic types, the runtime APIs, the outer enums and custom values.
 * A transaction checked by `CheckMetadataHash` is built from this metadata, the one its hash covers.
 */
object RuntimeMetadataSchemaV15 : Schema<RuntimeMetadataSchemaV15>() {
    val lookup by schema(LookupSchema)
    val pallets by vector(PalletMetadataV15)
    val extrinsic by schema(ExtrinsicMetadataV15)
    val type by compactInt()
    val apis by vector(RuntimeApiMetadataV15)
    val outerEnums by schema(OuterEnumsV15)
    val custom by vector(CustomValueMetadataV15)
}

object PalletMetadataV15 : Schema<PalletMetadataV15>() {
    val name by string()
    val storage by schema(StorageMetadataV14).optional()
    val calls by schema(PalletCallMetadataV14).optional()
    val events by schema(PalletEventMetadataV14).optional()
    val constants by vector(PalletConstantMetadataV14)
    val errors by schema(PalletErrorMetadataV14).optional()
    val index by uint8()
    val docs by vector(stringType)
}

object ExtrinsicMetadataV15 : Schema<ExtrinsicMetadataV15>() {
    val version by uint8()
    val addressType by compactInt()
    val callType by compactInt()
    val signatureType by compactInt()
    val extraType by compactInt()
    val signedExtensions by vector(SignedExtensionMetadataV14)
}

object RuntimeApiMetadataV15 : Schema<RuntimeApiMetadataV15>() {
    val name by string()
    val methods by vector(RuntimeApiMethodMetadataV15)
    val docs by vector(stringType)
}

object RuntimeApiMethodMetadataV15 : Schema<RuntimeApiMethodMetadataV15>() {
    val name by string()
    val inputs by vector(RuntimeApiMethodParamMetadataV15)
    val output by compactInt()
    val docs by vector(stringType)
}

object RuntimeApiMethodParamMetadataV15 : Schema<RuntimeApiMethodParamMetadataV15>() {
    val name by string()
    val type by compactInt()
}

object OuterEnumsV15 : Schema<OuterEnumsV15>() {
    val callEnumType by compactInt()
    val eventEnumType by compactInt()
    val errorEnumType by compactInt()
}

/** One entry of `CustomMetadata.map`, a `BTreeMap<String, CustomValueMetadata>` encoded as a sequence of pairs. */
object CustomValueMetadataV15 : Schema<CustomValueMetadataV15>() {
    val name by string()
    val type by compactInt()
    val value by byteArray()
}
