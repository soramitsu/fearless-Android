package jp.co.soramitsu.fearless_utils.runtime

import com.google.gson.Gson
import com.google.gson.stream.JsonReader
import jp.co.soramitsu.fearless_utils.getFileContentFromResources
import jp.co.soramitsu.fearless_utils.getResourceReader
import jp.co.soramitsu.fearless_utils.runtime.definitions.TypeDefinitionParser
import jp.co.soramitsu.fearless_utils.runtime.definitions.TypeDefinitionsTree
import jp.co.soramitsu.fearless_utils.runtime.definitions.dynamic.DynamicTypeResolver
import jp.co.soramitsu.fearless_utils.runtime.definitions.dynamic.extentsions.GenericsExtension
import jp.co.soramitsu.fearless_utils.runtime.definitions.registry.TypeRegistry
import jp.co.soramitsu.fearless_utils.runtime.definitions.registry.v13Preset
import jp.co.soramitsu.fearless_utils.runtime.definitions.registry.v14Preset
import jp.co.soramitsu.fearless_utils.runtime.definitions.v14.TypesParserV14
import jp.co.soramitsu.fearless_utils.runtime.metadata.RuntimeMetadataReader
import jp.co.soramitsu.fearless_utils.runtime.metadata.builder.VersionedRuntimeBuilder
import jp.co.soramitsu.fearless_utils.runtime.metadata.v14.RuntimeMetadataSchemaV14

object RealRuntimeProvider {

    fun buildRuntime(networkName: String, suffix: String = ""): RuntimeSnapshot {
        val metadataRaw = buildRawMetadata(networkName, suffix)
        val typeRegistry = if (metadataRaw.metadataVersion < 14) {
            buildRegistry(networkName)
        } else {
            val parseResult = TypesParserV14.parse(
                lookup = metadataRaw.metadata[RuntimeMetadataSchemaV14.lookup],
                typePreset = v14Preset()
            )
            val nReader = JsonReader(getResourceReader("${networkName}$suffix.json"))
            val nTree =
                Gson().fromJson<TypeDefinitionsTree>(nReader, TypeDefinitionsTree::class.java)
            val networkParsed = TypeDefinitionParser.parseNetworkVersioning(
                tree = nTree,
                typePreset = parseResult.typePreset,
            )
            TypeRegistry(
                networkParsed.typePreset,
                DynamicTypeResolver.defaultCompoundResolver()
            )
        }
        val metadata = VersionedRuntimeBuilder.buildMetadata(metadataRaw, typeRegistry)
        return RuntimeSnapshot(typeRegistry, metadata)
    }

    fun buildRawMetadata(networkName: String = "kusama", suffix: String = "") =
        getFileContentFromResources("${networkName}_metadata$suffix").run {
            RuntimeMetadataReader.read(this)
        }

    fun buildRegistry(networkName: String): TypeRegistry {
        val gson = Gson()
        val reader = JsonReader(getResourceReader("default.json"))
        val kusamaReader = JsonReader(getResourceReader("${networkName}.json"))

        val tree = gson.fromJson<TypeDefinitionsTree>(reader, TypeDefinitionsTree::class.java)
        val kusamaTree =
            gson.fromJson<TypeDefinitionsTree>(kusamaReader, TypeDefinitionsTree::class.java)

        val defaultTypeRegistry =
            TypeDefinitionParser.parseBaseDefinitions(tree, v13Preset()).typePreset
        val networkParsed = TypeDefinitionParser.parseNetworkVersioning(
            tree = kusamaTree,
            typePreset = defaultTypeRegistry,
            upto14 = true,
        )

        return TypeRegistry(
            types = networkParsed.typePreset,
            dynamicTypeResolver = DynamicTypeResolver(
                DynamicTypeResolver.DEFAULT_COMPOUND_EXTENSIONS + GenericsExtension
            )
        )
    }
}