package jp.co.soramitsu.fearless_utils.runtime.metadata

import io.emeraldpay.polkaj.scale.ScaleCodecReader
import jp.co.soramitsu.fearless_utils.extensions.fromHex
import jp.co.soramitsu.fearless_utils.runtime.metadata.v14.LookupSchema
import jp.co.soramitsu.fearless_utils.runtime.metadata.v14.RuntimeMetadataSchemaV14
import jp.co.soramitsu.fearless_utils.runtime.metadata.v15.RuntimeMetadataSchemaV15
import jp.co.soramitsu.fearless_utils.scale.EncodableStruct
import jp.co.soramitsu.fearless_utils.scale.Schema
import jp.co.soramitsu.fearless_utils.scale.uint32
import jp.co.soramitsu.fearless_utils.scale.uint8

object Magic : Schema<Magic>() {
    val magicNumber by uint32()
    val runtimeVersion by uint8()
}

class RuntimeMetadataReader private constructor(
    val metadataVersion: Int,
    val metadata: EncodableStruct<*>
) {

    /** The portable type registry of V14 and V15 metadata; null for the legacy formats, which have none. */
    val lookup: EncodableStruct<LookupSchema>?
        get() = when (metadata.schema) {
            is RuntimeMetadataSchemaV14 -> metadata[RuntimeMetadataSchemaV14.lookup]
            is RuntimeMetadataSchemaV15 -> metadata[RuntimeMetadataSchemaV15.lookup]
            else -> null
        }

    companion object {

        @OptIn(ExperimentalUnsignedTypes::class)
        fun read(metadaScale: String): RuntimeMetadataReader {
            val scaleCoderReader = ScaleCodecReader(metadaScale.fromHex())

            val runtimeVersion = Magic.read(scaleCoderReader)[Magic.runtimeVersion].toInt()

            val metadata = when {
                runtimeVersion < 14 -> {
                    RuntimeMetadataSchema.read(scaleCoderReader)
                }
                runtimeVersion == 14 -> {
                    RuntimeMetadataSchemaV14.read(scaleCoderReader)
                }
                runtimeVersion == 15 -> {
                    RuntimeMetadataSchemaV15.read(scaleCoderReader)
                }
                else -> throw IllegalArgumentException("Unsupported metadata version $runtimeVersion")
            }

            return RuntimeMetadataReader(
                metadataVersion = runtimeVersion,
                metadata = metadata
            )
        }
    }
}
