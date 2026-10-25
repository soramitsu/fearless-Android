package jp.co.soramitsu.fearless_utils.runtime.metadata.builder

import jp.co.soramitsu.fearless_utils.extensions.requireOrException
import jp.co.soramitsu.fearless_utils.runtime.definitions.registry.TypeRegistry
import jp.co.soramitsu.fearless_utils.runtime.definitions.types.Type
import jp.co.soramitsu.fearless_utils.runtime.definitions.types.TypeReference
import jp.co.soramitsu.fearless_utils.runtime.definitions.types.composite.DictEnum
import jp.co.soramitsu.fearless_utils.runtime.definitions.types.composite.Struct
import jp.co.soramitsu.fearless_utils.runtime.definitions.types.composite.Tuple
import jp.co.soramitsu.fearless_utils.runtime.definitions.types.generics.Null
import jp.co.soramitsu.fearless_utils.runtime.metadata.ExtrinsicMetadata
import jp.co.soramitsu.fearless_utils.runtime.metadata.RuntimeMetadata
import jp.co.soramitsu.fearless_utils.runtime.metadata.RuntimeMetadataReader
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
import jp.co.soramitsu.fearless_utils.runtime.metadata.v14.ExtrinsicMetadataV14
import jp.co.soramitsu.fearless_utils.runtime.metadata.v14.MapTypeV14
import jp.co.soramitsu.fearless_utils.runtime.metadata.v14.PalletCallMetadataV14
import jp.co.soramitsu.fearless_utils.runtime.metadata.v14.PalletConstantMetadataV14
import jp.co.soramitsu.fearless_utils.runtime.metadata.v14.PalletErrorMetadataV14
import jp.co.soramitsu.fearless_utils.runtime.metadata.v14.PalletEventMetadataV14
import jp.co.soramitsu.fearless_utils.runtime.metadata.v14.PalletMetadataV14
import jp.co.soramitsu.fearless_utils.runtime.metadata.v14.RuntimeMetadataSchemaV14
import jp.co.soramitsu.fearless_utils.runtime.metadata.v14.SignedExtensionMetadataV14
import jp.co.soramitsu.fearless_utils.runtime.metadata.v14.StorageEntryMetadataV14
import jp.co.soramitsu.fearless_utils.runtime.metadata.v14.StorageMetadataV14
import jp.co.soramitsu.fearless_utils.scale.EncodableStruct
import java.math.BigInteger

@OptIn(ExperimentalUnsignedTypes::class)
object V14RuntimeBuilder : RuntimeBuilder {

    override fun buildMetadata(
        reader: RuntimeMetadataReader,
        typeRegistry: TypeRegistry
    ): RuntimeMetadata {
        val metadataStruct = reader.metadata

        require(metadataStruct.schema is RuntimeMetadataSchemaV14)

        return RuntimeMetadata(
            extrinsic = buildExtrinsic(metadataStruct[RuntimeMetadataSchemaV14.extrinsic]),
            modules = buildModules(metadataStruct[RuntimeMetadataSchemaV14.pallets], typeRegistry),
            runtimeVersion = reader.metadataVersion.toBigInteger()
        )
    }

    private fun buildModules(
        modulesRaw: List<EncodableStruct<PalletMetadataV14>>,
        typeRegistry: TypeRegistry
    ): Map<String, Module> {
        return modulesRaw.map {
            buildModule(typeRegistry, it)
        }.groupByName()
    }

    private fun buildModule(
        typeRegistry: TypeRegistry,
        struct: EncodableStruct<PalletMetadataV14>
    ): Module {
        val moduleName = struct[PalletMetadataV14.name]
        val moduleIndex = struct[PalletMetadataV14.index].toInt()

        return Module(
            name = moduleName,
            index = moduleIndex.toBigInteger(),
            storage = struct[PalletMetadataV14.storage]?.let {
                buildStorage(typeRegistry, it, moduleName)
            },
            calls = struct[PalletMetadataV14.calls]?.let {
                buildCalls(typeRegistry, it, moduleIndex)
            },
            events = struct[PalletMetadataV14.events]?.let {
                buildEvents(typeRegistry, it, moduleIndex)
            },
            constants = buildConstants(typeRegistry, struct[PalletMetadataV14.constants]),
            errors = struct[PalletMetadataV14.errors]?.let {
                buildErrors(typeRegistry, it)
            } ?: emptyMap()
        )
    }

    private fun buildStorage(
        typeRegistry: TypeRegistry,
        struct: EncodableStruct<StorageMetadataV14>,
        moduleName: String
    ): Storage {
        val storageEntries = struct[StorageMetadataV14.entries].map { entryStruct ->
            StorageEntry(
                name = entryStruct[StorageEntryMetadataV14.name],
                modifier = entryStruct[StorageEntryMetadataV14.modifier],
                type = buildEntryType(typeRegistry, entryStruct[StorageEntryMetadataV14.type]),
                default = entryStruct[StorageEntryMetadataV14.default],
                documentation = entryStruct[StorageEntryMetadataV14.documentation],
                moduleName = moduleName
            )
        }

        return Storage(
            prefix = struct[StorageMetadataV14.prefix],
            entries = storageEntries
                .groupByName()
        )
    }

    private fun buildCalls(
        typeRegistry: TypeRegistry,
        callsRaw: EncodableStruct<PalletCallMetadataV14>,
        moduleIndex: Int
    ): Map<String, MetadataFunction> {
        val type = typeRegistry[callsRaw[PalletCallMetadataV14.type].toString()]

        if (type !is DictEnum) return emptyMap()

        return type.elements.map { (index, call) ->
            MetadataFunction(
                name = call.name,
                arguments = extractArguments(call.value.value!!) { name, type ->
                    FunctionArgument(name!!, type)
                },
                documentation = emptyList(),
                index = moduleIndex to index
            )
        }.groupByName()
    }

    private fun buildEvents(
        typeRegistry: TypeRegistry,
        eventsRaw: EncodableStruct<PalletEventMetadataV14>,
        moduleIndex: Int
    ): Map<String, Event> {
        val type = typeRegistry[eventsRaw[PalletEventMetadataV14.type].toString()]

        if (type !is DictEnum) return emptyMap()

        return type.elements.map { (index, event) ->
            Event(
                name = event.name,
                arguments = extractArguments(event.value.value!!) { _, type -> type },
                documentation = emptyList(),
                index = moduleIndex to index
            )
        }.groupByName()
    }

    private fun <T> extractArguments(
        type: Type<*>,
        mapper: (name: String?, type: Type<*>?) -> T
    ): List<T> {
        return when (type) {
            is Null -> emptyList()
            is Tuple -> type.typeReferences.map { typeRef ->
                mapper(null, typeRef.value)
            }
            is Struct -> type.mapping.map { mapEntry ->
                mapper(mapEntry.key, mapEntry.value.value)
            }
            else -> listOf(mapper(null, type))
        }
    }

    private fun buildConstants(
        typeRegistry: TypeRegistry,
        constantsRaw: List<EncodableStruct<PalletConstantMetadataV14>>
    ): Map<String, Constant> {
        return constantsRaw.map { constantStruct ->
            val typeIndex = constantStruct[PalletConstantMetadataV14.type].toString()

            Constant(
                name = constantStruct[PalletConstantMetadataV14.name],
                type = typeRegistry[typeIndex],
                value = constantStruct[PalletConstantMetadataV14.value],
                documentation = constantStruct[PalletConstantMetadataV14.documentation]
            )
        }.groupByName()
    }

    private fun buildErrors(
        typeRegistry: TypeRegistry,
        errorsRaw: EncodableStruct<PalletErrorMetadataV14>
    ): Map<String, Error> {
        val type = typeRegistry[errorsRaw[PalletErrorMetadataV14.type].toString()]

        if (type !is DictEnum) return emptyMap()

        return type.elements.values.map {
            Error(
                name = it.name,
                documentation = emptyList()
            )
        }.groupByName()
    }

    private fun buildEntryType(
        typeRegistry: TypeRegistry,
        enumValue: Any?
    ): StorageEntryType {
        return when (enumValue) {
            is BigInteger -> {
                StorageEntryType.Plain(typeRegistry[enumValue.toString()])
            }
            is EncodableStruct<*> -> {
                requireOrException(enumValue.schema is MapTypeV14) {
                    cannotConstructStorageEntry(enumValue)
                }

                val hashers = enumValue[MapTypeV14.hashers]

                val type = typeRegistry[enumValue[MapTypeV14.key].toString()]
                    ?: cannotConstructStorageEntry(enumValue)

                val keys = if (hashers.size == 1) {
                    listOf(type)
                } else {
                    if (type is Tuple) {
                        type.typeReferences.mapNotNull(TypeReference::value)
                    } else {
                        cannotConstructStorageEntry(enumValue)
                    }
                }

                requireOrException(keys.size == hashers.size) {
                    cannotConstructStorageEntry(enumValue)
                }

                StorageEntryType.NMap(
                    keys,
                    hashers,
                    typeRegistry[enumValue[MapTypeV14.value].toString()]
                )
            }
            else -> cannotConstructStorageEntry(enumValue)
        }
    }

    private fun buildExtrinsic(
        struct: EncodableStruct<ExtrinsicMetadataV14>
    ) = ExtrinsicMetadata(
        version = struct[ExtrinsicMetadataV14.version].toInt().toBigInteger(),
        signedExtensions = struct[ExtrinsicMetadataV14.signedExtensions].map {
            it[SignedExtensionMetadataV14.identifier]
        }
    )

    private fun cannotConstructStorageEntry(from: Any?): Nothing {
        throw IllegalArgumentException("Cannot construct StorageEntryType from $from")
    }
}
