package jp.co.soramitsu.fearless_utils.runtime.metadata.builder

import jp.co.soramitsu.fearless_utils.runtime.definitions.registry.TypeRegistry
import jp.co.soramitsu.fearless_utils.runtime.metadata.ExtrinsicMetadata
import jp.co.soramitsu.fearless_utils.runtime.metadata.RuntimeMetadata
import jp.co.soramitsu.fearless_utils.runtime.metadata.RuntimeMetadataReader
import jp.co.soramitsu.fearless_utils.runtime.metadata.SignedExtensionTypes
import jp.co.soramitsu.fearless_utils.runtime.metadata.groupByName
import jp.co.soramitsu.fearless_utils.runtime.metadata.module.Module
import jp.co.soramitsu.fearless_utils.runtime.metadata.v14.SignedExtensionMetadataV14
import jp.co.soramitsu.fearless_utils.runtime.metadata.v15.ExtrinsicMetadataV15
import jp.co.soramitsu.fearless_utils.runtime.metadata.v15.PalletMetadataV15
import jp.co.soramitsu.fearless_utils.runtime.metadata.v15.RuntimeMetadataSchemaV15
import jp.co.soramitsu.fearless_utils.scale.EncodableStruct

/**
 * Builds the runtime model from V15 metadata. Pallet storage, calls, events, constants and errors have the V14 layout
 * and are built by [V14RuntimeBuilder]; the extrinsic signed extensions are read from the V15 extrinsic metadata.
 */
object V15RuntimeBuilder : RuntimeBuilder {

    override fun buildMetadata(reader: RuntimeMetadataReader, typeRegistry: TypeRegistry): RuntimeMetadata {
        val metadataStruct = reader.metadata

        require(metadataStruct.schema is RuntimeMetadataSchemaV15)

        return RuntimeMetadata(
            extrinsic = buildExtrinsic(metadataStruct[RuntimeMetadataSchemaV15.extrinsic], typeRegistry),
            modules = metadataStruct[RuntimeMetadataSchemaV15.pallets].map { buildModule(typeRegistry, it) }.groupByName(),
            runtimeVersion = reader.metadataVersion.toBigInteger()
        )
    }

    private fun buildModule(typeRegistry: TypeRegistry, struct: EncodableStruct<PalletMetadataV15>): Module {
        val moduleName = struct[PalletMetadataV15.name]
        val moduleIndex = struct[PalletMetadataV15.index].toInt()

        return Module(
            name = moduleName,
            index = moduleIndex.toBigInteger(),
            storage = struct[PalletMetadataV15.storage]?.let { V14RuntimeBuilder.buildStorage(typeRegistry, it, moduleName) },
            calls = struct[PalletMetadataV15.calls]?.let { V14RuntimeBuilder.buildCalls(typeRegistry, it, moduleIndex) },
            events = struct[PalletMetadataV15.events]?.let { V14RuntimeBuilder.buildEvents(typeRegistry, it, moduleIndex) },
            constants = V14RuntimeBuilder.buildConstants(typeRegistry, struct[PalletMetadataV15.constants]),
            errors = struct[PalletMetadataV15.errors]?.let { V14RuntimeBuilder.buildErrors(typeRegistry, it) } ?: emptyMap()
        )
    }

    private fun buildExtrinsic(struct: EncodableStruct<ExtrinsicMetadataV15>, typeRegistry: TypeRegistry) = ExtrinsicMetadata(
        version = struct[ExtrinsicMetadataV15.version].toInt().toBigInteger(),
        signedExtensions = struct[ExtrinsicMetadataV15.signedExtensions].map { it[SignedExtensionMetadataV14.identifier] },
        signedExtensionTypes = struct[ExtrinsicMetadataV15.signedExtensions].associate {
            it[SignedExtensionMetadataV14.identifier] to SignedExtensionTypes(
                typeRegistry[it[SignedExtensionMetadataV14.type].toString()],
                typeRegistry[it[SignedExtensionMetadataV14.additionalSigned].toString()]
            )
        }
    )
}
