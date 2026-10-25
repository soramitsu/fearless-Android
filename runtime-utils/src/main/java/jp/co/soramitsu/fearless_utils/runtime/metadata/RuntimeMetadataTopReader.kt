package jp.co.soramitsu.fearless_utils.runtime.metadata

import io.emeraldpay.polkaj.scale.ScaleCodecReader
import jp.co.soramitsu.fearless_utils.extensions.fromHex
import jp.co.soramitsu.fearless_utils.runtime.metadata.v14.RuntimeMetadataSchemaV14
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

    companion object {

        @OptIn(ExperimentalUnsignedTypes::class)
        fun read(metadaScale: String): RuntimeMetadataReader {
            val scaleCoderReader = ScaleCodecReader(metadaScale.fromHex())

            val runtimeVersion = Magic.read(scaleCoderReader)[Magic.runtimeVersion].toInt()

            val metadata = when {
                runtimeVersion < 14 -> {
                    RuntimeMetadataSchema.read(scaleCoderReader)
                }
                else -> {
                    RuntimeMetadataSchemaV14.read(scaleCoderReader)
                }
            }

            return RuntimeMetadataReader(
                metadataVersion = runtimeVersion,
                metadata = metadata
            )
        }
    }
}
