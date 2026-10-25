package jp.co.soramitsu.fearless_utils.runtime.metadata.builder

import jp.co.soramitsu.fearless_utils.runtime.definitions.registry.TypeRegistry
import jp.co.soramitsu.fearless_utils.runtime.metadata.DoubleMapSchema
import jp.co.soramitsu.fearless_utils.runtime.metadata.ErrorMetadataSchema
import jp.co.soramitsu.fearless_utils.runtime.metadata.EventMetadataSchema
import jp.co.soramitsu.fearless_utils.runtime.metadata.ExtrinsicMetadata
import jp.co.soramitsu.fearless_utils.runtime.metadata.ExtrinsicMetadataSchema
import jp.co.soramitsu.fearless_utils.runtime.metadata.FunctionArgumentMetadataSchema
import jp.co.soramitsu.fearless_utils.runtime.metadata.FunctionMetadataSchema
import jp.co.soramitsu.fearless_utils.runtime.metadata.MapSchema
import jp.co.soramitsu.fearless_utils.runtime.metadata.ModuleConstantMetadataSchema
import jp.co.soramitsu.fearless_utils.runtime.metadata.ModuleMetadataSchema
import jp.co.soramitsu.fearless_utils.runtime.metadata.NMapSchema
import jp.co.soramitsu.fearless_utils.runtime.metadata.RuntimeMetadata
import jp.co.soramitsu.fearless_utils.runtime.metadata.RuntimeMetadataReader
import jp.co.soramitsu.fearless_utils.runtime.metadata.RuntimeMetadataSchema
import jp.co.soramitsu.fearless_utils.runtime.metadata.StorageEntryMetadataSchema
import jp.co.soramitsu.fearless_utils.runtime.metadata.StorageMetadataSchema
import jp.co.soramitsu.fearless_utils.runtime.metadata.groupByName
import jp.co.soramitsu.fearless_utils.runtime.metadata.module.Constant
import jp.co.soramitsu.fearless_utils.runtime.metadata.module.Error
import jp.co.soramitsu.fearless_utils.runtime.metadata.module.Event
import jp.co.soramitsu.fearless_utils.runtime.metadata.module.FunctionArgument
import jp.co.soramitsu.fearless_utils.runtime.metadata.module.MetadataFunction
import jp.co.soramitsu.fearless_utils.runtime.metadata.module.Module
import jp.co.soramitsu.fearless_utils.runtime.metadata.module.Storage
import jp.co.soramitsu.fearless_utils.runtime.metadata.module.StorageEntry
import jp.co.soramitsu.fearless_utils.runtime.metadata.module.StorageEntryType
import jp.co.soramitsu.fearless_utils.scale.EncodableStruct

@OptIn(ExperimentalUnsignedTypes::class)
internal object V13RuntimeBuilder : RuntimeBuilder {

    override fun buildMetadata(
        reader: RuntimeMetadataReader,
        typeRegistry: TypeRegistry
    ): RuntimeMetadata {
        val metadataStruct = reader.metadata

        require(metadataStruct.schema is RuntimeMetadataSchema)

        return RuntimeMetadata(
            extrinsic = buildExtrinsic(metadataStruct[RuntimeMetadataSchema.extrinsic]),
            modules = buildModules(metadataStruct[RuntimeMetadataSchema.modules], typeRegistry),
            runtimeVersion = reader.metadataVersion.toBigInteger()
        )
    }

    private fun buildModules(
        modulesRaw: List<EncodableStruct<ModuleMetadataSchema>>,
        typeRegistry: TypeRegistry
    ): Map<String, Module> {
        return modulesRaw.map {
            buildModule(typeRegistry, it)
        }.groupByName()
    }

    private fun buildModule(
        typeRegistry: TypeRegistry,
        struct: EncodableStruct<ModuleMetadataSchema>
    ): Module {
        val moduleName = struct[ModuleMetadataSchema.name]
        val moduleIndex = struct[ModuleMetadataSchema.index].toInt()

        return Module(
            name = moduleName,
            index = moduleIndex.toBigInteger(),
            storage = struct[ModuleMetadataSchema.storage]?.let {
                buildStorage(typeRegistry, it, moduleName)
            },
            calls = struct[ModuleMetadataSchema.calls]?.let {
                buildCalls(typeRegistry, it, moduleIndex)
            },
            events = struct[ModuleMetadataSchema.events]?.let {
                buildEvents(typeRegistry, it, moduleIndex)
            },
            constants = buildConstants(typeRegistry, struct[ModuleMetadataSchema.constants]),
            errors = buildErrors(struct[ModuleMetadataSchema.errors])
        )
    }

    private fun buildStorage(
        typeRegistry: TypeRegistry,
        struct: EncodableStruct<StorageMetadataSchema>,
        moduleName: String
    ): Storage {
        val storageEntries = struct[StorageMetadataSchema.entries].map { entryStruct ->
            StorageEntry(
                name = entryStruct[StorageEntryMetadataSchema.name],
                modifier = entryStruct[StorageEntryMetadataSchema.modifier],
                type = buildEntryType(typeRegistry, entryStruct[StorageEntryMetadataSchema.type]),
                default = entryStruct[StorageEntryMetadataSchema.default],
                documentation = entryStruct[StorageEntryMetadataSchema.documentation],
                moduleName = moduleName
            )
        }

        return Storage(
            prefix = struct[StorageMetadataSchema.prefix],
            entries = storageEntries
                .groupByName()
        )
    }

    private fun buildCalls(
        typeRegistry: TypeRegistry,
        callsRaw: List<EncodableStruct<FunctionMetadataSchema>>,
        moduleIndex: Int
    ): Map<String, MetadataFunction> {
        return callsRaw.mapIndexed { index, callStruct ->
            MetadataFunction(
                name = callStruct[FunctionMetadataSchema.name],
                arguments = callStruct[FunctionMetadataSchema.arguments].map { argumentStruct ->
                    FunctionArgument(
                        name = argumentStruct[FunctionArgumentMetadataSchema.name],
                        type = typeRegistry[argumentStruct[FunctionArgumentMetadataSchema.type]]
                    )
                },
                documentation = callStruct[FunctionMetadataSchema.documentation],
                index = moduleIndex to index
            )
        }.groupByName()
    }

    private fun buildEvents(
        typeRegistry: TypeRegistry,
        eventsRaw: List<EncodableStruct<EventMetadataSchema>>,
        moduleIndex: Int
    ): Map<String, Event> {
        return eventsRaw.mapIndexed { index, eventStruct ->
            Event(
                name = eventStruct[EventMetadataSchema.name],
                arguments = eventStruct[EventMetadataSchema.arguments].map { typeRegistry[it] },
                documentation = eventStruct[EventMetadataSchema.documentation],
                index = moduleIndex to index
            )
        }.groupByName()
    }

    private fun buildConstants(
        typeRegistry: TypeRegistry,
        constantsRaw: List<EncodableStruct<ModuleConstantMetadataSchema>>
    ): Map<String, Constant> {
        return constantsRaw.map { constantStruct ->
            Constant(
                name = constantStruct[ModuleConstantMetadataSchema.name],
                type = typeRegistry[constantStruct[ModuleConstantMetadataSchema.type]],
                value = constantStruct[ModuleConstantMetadataSchema.value],
                documentation = constantStruct[ModuleConstantMetadataSchema.documentation]
            )
        }.groupByName()
    }

    private fun buildErrors(
        errorsRaw: List<EncodableStruct<ErrorMetadataSchema>>
    ): Map<String, Error> {
        return errorsRaw.map { errorStruct ->
            Error(
                name = errorStruct[ErrorMetadataSchema.name],
                documentation = errorStruct[ErrorMetadataSchema.documentation]
            )
        }.groupByName()
    }

    private fun buildEntryType(
        typeRegistry: TypeRegistry,
        enumValue: Any?
    ): StorageEntryType {
        return when (enumValue) {
            is String -> {
                StorageEntryType.Plain(typeRegistry[enumValue])
            }
            is EncodableStruct<*> -> {
                when (enumValue.schema) {
                    MapSchema -> StorageEntryType.NMap(
                        keys = listOf(typeRegistry[enumValue[MapSchema.key]]),
                        hashers = listOf(enumValue[MapSchema.hasher]),
                        value = typeRegistry[enumValue[MapSchema.value]]
                    )
                    DoubleMapSchema -> StorageEntryType.NMap(
                        keys = listOf(
                            typeRegistry[enumValue[DoubleMapSchema.key1]],
                            typeRegistry[enumValue[DoubleMapSchema.key2]]
                        ),
                        hashers = listOf(
                            enumValue[DoubleMapSchema.key1Hasher],
                            enumValue[DoubleMapSchema.key2Hasher]
                        ),
                        value = typeRegistry[enumValue[DoubleMapSchema.value]]
                    )
                    NMapSchema -> StorageEntryType.NMap(
                        keys = enumValue[NMapSchema.keys].map { typeRegistry[it] },
                        hashers = enumValue[NMapSchema.hashers],
                        value = typeRegistry[enumValue[NMapSchema.value]]
                    )
                    else -> cannotConstructStorageEntry(enumValue)
                }
            }
            else -> cannotConstructStorageEntry(enumValue)
        }
    }

    private fun buildExtrinsic(
        struct: EncodableStruct<ExtrinsicMetadataSchema>
    ) = ExtrinsicMetadata(
        version = struct[ExtrinsicMetadataSchema.version].toInt().toBigInteger(),
        signedExtensions = struct[ExtrinsicMetadataSchema.signedExtensions]
    )

    private fun cannotConstructStorageEntry(from: Any?): Nothing {
        throw IllegalArgumentException("Cannot construct StorageEntryType from $from")
    }
}
