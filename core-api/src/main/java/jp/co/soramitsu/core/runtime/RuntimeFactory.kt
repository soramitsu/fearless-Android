package jp.co.soramitsu.core.runtime

import jp.co.soramitsu.fearless_utils.runtime.RuntimeSnapshot
import jp.co.soramitsu.fearless_utils.runtime.definitions.TypeDefinitionParserV2
import jp.co.soramitsu.fearless_utils.runtime.definitions.TypeDefinitionsTreeV2
import jp.co.soramitsu.fearless_utils.runtime.definitions.dynamic.DynamicTypeResolver
import jp.co.soramitsu.fearless_utils.runtime.definitions.dynamic.extentsions.GenericsExtension
import jp.co.soramitsu.fearless_utils.runtime.definitions.registry.TypePreset
import jp.co.soramitsu.fearless_utils.runtime.definitions.registry.TypeRegistry
import jp.co.soramitsu.fearless_utils.runtime.definitions.registry.v13Preset
import jp.co.soramitsu.fearless_utils.runtime.definitions.registry.v14Preset
import jp.co.soramitsu.fearless_utils.runtime.definitions.v14.TypesParserV14
import jp.co.soramitsu.fearless_utils.runtime.metadata.RuntimeMetadataReader
import jp.co.soramitsu.fearless_utils.runtime.metadata.builder.VersionedRuntimeBuilder
import jp.co.soramitsu.fearless_utils.runtime.metadata.v14.RuntimeMetadataSchemaV14
import kotlinx.serialization.json.Json
import java.security.MessageDigest

data class ConstructedRuntime(
    val runtime: RuntimeSnapshot,
    val metadataHash: String,
    val ownTypesHash: String
)

class RuntimeFactory(
    private val json: Json
) {
    fun constructRuntime(metadataRaw: String, ownTypesRaw: String, runtimeVersion: Int): ConstructedRuntime {
        val metadataReader = RuntimeMetadataReader.read(metadataRaw)
        val typeRegistry = if (metadataReader.metadataVersion < 14) {
            buildV13Registry(ownTypesRaw, defaultTypesRaw = null, runtimeVersion = runtimeVersion)
        } else {
            buildV14Registry(metadataReader, ownTypesRaw, runtimeVersion)
        }

        return ConstructedRuntime(
            runtime = RuntimeSnapshot(
                typeRegistry = typeRegistry,
                metadata = VersionedRuntimeBuilder.buildMetadata(metadataReader, typeRegistry),
                overrides = parseOverrides(ownTypesRaw)
            ),
            metadataHash = metadataRaw.md5(),
            ownTypesHash = ownTypesRaw.md5()
        )
    }

    fun constructRuntimeV13(
        metadataRaw: String,
        ownTypesRaw: String,
        defaultTypesRaw: String,
        runtimeVersion: Int
    ): ConstructedRuntime {
        val metadataReader = RuntimeMetadataReader.read(metadataRaw)
        val typeRegistry = buildV13Registry(
            ownTypesRaw = ownTypesRaw,
            defaultTypesRaw = defaultTypesRaw,
            runtimeVersion = runtimeVersion
        )

        return ConstructedRuntime(
            runtime = RuntimeSnapshot(
                typeRegistry = typeRegistry,
                metadata = VersionedRuntimeBuilder.buildMetadata(metadataReader, typeRegistry),
                overrides = parseOverrides(ownTypesRaw)
            ),
            metadataHash = metadataRaw.md5(),
            ownTypesHash = ownTypesRaw.md5()
        )
    }

    private fun buildV13Registry(
        ownTypesRaw: String,
        defaultTypesRaw: String?,
        runtimeVersion: Int
    ): TypeRegistry {
        val basePreset = defaultTypesRaw?.let { raw ->
            TypeDefinitionParserV2.parseBaseDefinitions(parseTree(raw), v13Preset()).typePreset
        } ?: v13Preset()

        val networkPreset = parseNetworkTypes(ownTypesRaw, basePreset, runtimeVersion, upto14 = true)

        return TypeRegistry(
            types = networkPreset,
            dynamicTypeResolver = DynamicTypeResolver(
                DynamicTypeResolver.DEFAULT_COMPOUND_EXTENSIONS + GenericsExtension
            )
        )
    }

    private fun buildV14Registry(
        metadataReader: RuntimeMetadataReader,
        ownTypesRaw: String,
        runtimeVersion: Int
    ): TypeRegistry {
        val parseResult = TypesParserV14.parse(
            lookup = metadataReader.metadata[RuntimeMetadataSchemaV14.lookup],
            typePreset = v14Preset()
        )
        val networkPreset = parseNetworkTypes(
            ownTypesRaw = ownTypesRaw,
            basePreset = parseResult.typePreset,
            runtimeVersion = runtimeVersion,
            upto14 = false
        )

        return TypeRegistry(
            types = networkPreset,
            dynamicTypeResolver = DynamicTypeResolver.defaultCompoundResolver()
        )
    }

    private fun parseNetworkTypes(
        ownTypesRaw: String,
        basePreset: TypePreset,
        runtimeVersion: Int,
        upto14: Boolean
    ): TypePreset {
        val tree = parseTree(ownTypesRaw)

        return if (tree.versioning == null) {
            TypeDefinitionParserV2.parseBaseDefinitions(tree, basePreset).typePreset
        } else {
            TypeDefinitionParserV2.parseNetworkVersioning(
                tree = tree,
                typePreset = basePreset,
                currentRuntimeVersion = tree.runtimeId ?: runtimeVersion,
                upto14 = upto14
            ).typePreset
        }
    }

    private fun parseOverrides(raw: String): Map<String, Map<String, String>>? {
        return parseTree(raw).overrides
            ?.associate { item -> item.module to item.constants.associate { it.name to it.value } }
            ?.takeIf { it.isNotEmpty() }
    }

    private fun parseTree(raw: String): TypeDefinitionsTreeV2 {
        return json.decodeFromString(TypeDefinitionsTreeV2.serializer(), raw)
    }

    private fun String.md5(): String {
        val hasher = MessageDigest.getInstance("MD5")

        return hasher.digest(encodeToByteArray()).decodeToString()
    }
}
