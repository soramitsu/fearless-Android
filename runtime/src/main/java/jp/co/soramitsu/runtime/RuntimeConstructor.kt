package jp.co.soramitsu.runtime

import com.google.gson.Gson
import jp.co.soramitsu.core_db.dao.RuntimeDao
import jp.co.soramitsu.fearless_utils.runtime.RuntimeSnapshot
import jp.co.soramitsu.fearless_utils.runtime.definitions.TypeDefinitionParser
import jp.co.soramitsu.fearless_utils.runtime.definitions.TypeDefinitionsTree
import jp.co.soramitsu.fearless_utils.runtime.definitions.dynamic.DynamicTypeResolver
import jp.co.soramitsu.fearless_utils.runtime.definitions.dynamic.extentsions.GenericsExtension
import jp.co.soramitsu.fearless_utils.runtime.definitions.registry.TypeRegistry
import jp.co.soramitsu.fearless_utils.runtime.definitions.registry.v13Preset
import jp.co.soramitsu.fearless_utils.runtime.definitions.registry.v14Preset
import jp.co.soramitsu.fearless_utils.runtime.definitions.v14.TypesParserV14
import jp.co.soramitsu.fearless_utils.runtime.metadata.GetMetadataRequest
import jp.co.soramitsu.fearless_utils.runtime.metadata.RuntimeMetadataReader
import jp.co.soramitsu.fearless_utils.runtime.metadata.builder.VersionedRuntimeBuilder
import jp.co.soramitsu.fearless_utils.runtime.metadata.v14.RuntimeMetadataSchemaV14
import jp.co.soramitsu.fearless_utils.wsrpc.SocketService
import jp.co.soramitsu.fearless_utils.wsrpc.executeAsync
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

private const val TYPE_DEFINITIONS_DEFAULT = "default"

class ConstructionParams(
    val metadataReader: RuntimeMetadataReader,
    val latestMetadataVersion: Int,
    val defaultDefinitions: TypeDefinitionsTree,
    val networkDefinitions: TypeDefinitionsTree
)

class RuntimeConstructor(
    private val socketService: SocketService,
    private val definitionsFetcher: DefinitionsFetcher,
    private val gson: Gson,
    private val runtimeDao: RuntimeDao,
    private val runtimeCache: RuntimeCache
) {

    suspend fun constructRuntime(
        networkName: String
    ): RuntimeSnapshot {
        val latestRuntimeVersion = runtimeDao.getCacheEntry(networkName).latestKnownVersion

        return constructRuntime(latestRuntimeVersion, networkName)
    }

    suspend fun constructRuntime(
        newRuntimeVersion: Int,
        networkName: String
    ) = withContext(Dispatchers.IO) {
        runtimeDao.updateLatestKnownVersion(networkName, newRuntimeVersion)

        val runtimeParams = getRuntimeParams(newRuntimeVersion, networkName)

        constructRuntime(runtimeParams)
    }

    suspend fun constructFromCache(networkName: String) = withContext(Dispatchers.Default) {
        val metadata = runtimeCache.getRuntimeMetadata(networkName)!!
        val reader = RuntimeMetadataReader.read(metadata)
        val (defaultTree, networkTree) = networkTypesFromCache(networkName)
        val latestMetadataVersion = runtimeDao.getCacheEntry(networkName).latestKnownVersion

        constructRuntime(ConstructionParams(reader, latestMetadataVersion, defaultTree, networkTree))
    }

    private fun constructRuntime(params: ConstructionParams): RuntimeSnapshot {
        val typeRegistry = constructTypeRegistryVersioned(params, params.metadataReader)

        val runtimeMetadata = VersionedRuntimeBuilder.buildMetadata(params.metadataReader, typeRegistry)

        return RuntimeSnapshot(typeRegistry, runtimeMetadata)
    }

    private suspend fun getRuntimeParams(latestRuntimeVersion: Int, networkName: String): ConstructionParams {
        val cacheInfo = runtimeDao.getCacheEntry(networkName)

        val metadataRaw = if (latestRuntimeVersion == cacheInfo.latestAppliedVersion) {
            val metadataRaw = runtimeCache.getRuntimeMetadata(networkName)!!

            if (latestRuntimeVersion <= cacheInfo.typesVersion) {
                val (default, network) = networkTypesFromCache(networkName)

                val reader = RuntimeMetadataReader.read(metadataRaw)
                return ConstructionParams(reader, latestRuntimeVersion, default, network)
            }

            metadataRaw
        } else {
            val metadataRaw = socketService.executeAsync(GetMetadataRequest).result as String

            runtimeCache.saveRuntimeMetadata(networkName, metadataRaw)
            runtimeDao.updateLatestAppliedVersion(networkName, latestRuntimeVersion)

            metadataRaw
        }

        val reader = RuntimeMetadataReader.read(metadataRaw)
        val typesNetworkSuffix = "_v${reader.metadataVersion}"
        val defaultTreeRaw = definitionsFetcher.getDefinitionsByNetwork(TYPE_DEFINITIONS_DEFAULT)
        val networkTreeRaw = definitionsFetcher.getDefinitionsByNetwork("$networkName$typesNetworkSuffix")

        val defaultTree = typesFromJson(defaultTreeRaw)
        val networkTree = typesFromJson(networkTreeRaw)

        val newestTypesChange = networkTree.versioning!!.maxOf { it.from }
        val typesVersion = maxOf(newestTypesChange, networkTree.runtimeId!!)

        runtimeDao.updateTypesVersion(networkName, typesVersion)

        runtimeCache.saveTypeDefinitions(TYPE_DEFINITIONS_DEFAULT, defaultTreeRaw)

        runtimeCache.saveTypeDefinitions(networkName, networkTreeRaw)

        return ConstructionParams(
            reader,
            latestRuntimeVersion,
            defaultTree,
            networkTree
        )
    }

    private suspend fun networkTypesFromCache(networkName: String): Pair<TypeDefinitionsTree, TypeDefinitionsTree> {
        val defaultRaw = runtimeCache.getTypeDefinitions(TYPE_DEFINITIONS_DEFAULT)!!
        val defaultTree = typesFromJson(defaultRaw)

        val networkRaw = runtimeCache.getTypeDefinitions(networkName)!!
        val networkTree = typesFromJson(networkRaw)

        return defaultTree to networkTree
    }

    private fun typesFromJson(typeDefinitions: String) = gson.fromJson(typeDefinitions, TypeDefinitionsTree::class.java)

    private fun constructTypeRegistryVersioned(
        constructionParams: ConstructionParams,
        reader: RuntimeMetadataReader,
    ): TypeRegistry {
        return if (reader.metadataVersion < 14) {
            constructTypeRegistry(constructionParams)
        } else {
            val parseResult = TypesParserV14.parse(
                reader.metadata[RuntimeMetadataSchemaV14.lookup],
                v14Preset()
            )
            val networkTypePreset = TypeDefinitionParser.parseNetworkVersioning(
                constructionParams.networkDefinitions,
                parseResult.typePreset,
                constructionParams.latestMetadataVersion
            ).typePreset
            TypeRegistry(
                networkTypePreset,
                DynamicTypeResolver.defaultCompoundResolver()
            )
        }
    }

    private fun constructTypeRegistry(
        constructionParams: ConstructionParams
    ): TypeRegistry {
        val defaultTypePreset = TypeDefinitionParser.parseBaseDefinitions(
            constructionParams.defaultDefinitions,
            v13Preset()
        ).typePreset

        val networkTypePreset = TypeDefinitionParser.parseNetworkVersioning(
            constructionParams.networkDefinitions,
            defaultTypePreset,
            constructionParams.latestMetadataVersion
        ).typePreset

        return TypeRegistry(
            types = networkTypePreset,
            dynamicTypeResolver = DynamicTypeResolver(
                DynamicTypeResolver.DEFAULT_COMPOUND_EXTENSIONS + GenericsExtension
            )
        )
    }
}
