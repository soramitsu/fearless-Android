package jp.co.soramitsu.runtime

import com.google.gson.Gson
import jp.co.soramitsu.core_db.dao.RuntimeDao
import jp.co.soramitsu.fearless_utils.runtime.RuntimeSnapshot
import jp.co.soramitsu.fearless_utils.runtime.definitions.TypeDefinitionParser
import jp.co.soramitsu.fearless_utils.runtime.definitions.TypeDefinitionsTree
import jp.co.soramitsu.fearless_utils.runtime.definitions.dynamic.DynamicTypeResolver
import jp.co.soramitsu.fearless_utils.runtime.definitions.dynamic.extentsions.GenericsExtension
import jp.co.soramitsu.fearless_utils.runtime.definitions.registry.TypeRegistry
import jp.co.soramitsu.fearless_utils.runtime.definitions.registry.substratePreParsePreset
import jp.co.soramitsu.fearless_utils.runtime.metadata.RuntimeMetadata
import jp.co.soramitsu.fearless_utils.runtime.metadata.RuntimeMetadataSchema
import jp.co.soramitsu.fearless_utils.wsrpc.SocketService
import jp.co.soramitsu.fearless_utils.wsrpc.executeAsync
import jp.co.soramitsu.fearless_utils.wsrpc.request.runtime.RuntimeRequest
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

private const val TYPE_DEFINITIONS_DEFAULT = "default"

/**
 * westend block 7 500 000
 */
private val westendBlockHash = "0xa300b367c112c55e137a6fbba806975910695057ed0f7a3e37ac2fcbe19d70c1"

/**
 * kusama block 9 624 000
 */
private val kusamaBlockHash = "0x331cd3019b10cb639b6855e10f411bce33e65c2d129381907ac69f62bc054df9"

/**
 * polkadot block 7 227 700
 */
private val polkadotBlockHash = "0xe8af816df50b4cabb3f396b61d4574925b2aa6fae556626804aab22fea276234"

class GetMetadataRequestHash(hash: String) : RuntimeRequest("state_getMetadata", listOf(hash))

class ConstructionParams(
    val metadataRaw: String,
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
        val (defaultTree, networkTree) = networkTypesFromCache(networkName)
        val metadata = runtimeCache.getRuntimeMetadata(networkName)!!
        val latestMetadataVersion = runtimeDao.getCacheEntry(networkName).latestKnownVersion

        constructRuntime(ConstructionParams(metadata, latestMetadataVersion, defaultTree, networkTree))
    }

    private fun constructRuntime(params: ConstructionParams): RuntimeSnapshot {
        val typeRegistry = constructTypeRegistry(params)

        val runtimeMetadataStruct = RuntimeMetadataSchema.read(params.metadataRaw)
        val runtimeMetadata = RuntimeMetadata(typeRegistry, runtimeMetadataStruct)

        return RuntimeSnapshot(typeRegistry, runtimeMetadata)
    }

    private suspend fun getRuntimeParams(latestRuntimeVersion: Int, networkName: String): ConstructionParams {
        val cacheInfo = runtimeDao.getCacheEntry(networkName)
        val hash = when (networkName.toLowerCase()) {
            "kusama" -> kusamaBlockHash
            "polkadot" -> polkadotBlockHash
            else -> westendBlockHash
        }

        val metadataRaw = if (latestRuntimeVersion == cacheInfo.latestAppliedVersion) {
            //val metadataRaw = runtimeCache.getRuntimeMetadata(networkName)!!
            val metadataRaw = socketService.executeAsync(GetMetadataRequestHash(hash)).result as String

            if (latestRuntimeVersion <= cacheInfo.typesVersion) {
                val (default, network) = networkTypesFromCache(networkName)

                return ConstructionParams(metadataRaw, latestRuntimeVersion, default, network)
            }

            metadataRaw
        } else {
            val metadataRaw = socketService.executeAsync(GetMetadataRequestHash(hash)).result as String

            runtimeCache.saveRuntimeMetadata(networkName, metadataRaw)
            runtimeDao.updateLatestAppliedVersion(networkName, latestRuntimeVersion)

            metadataRaw
        }

        val defaultTreeRaw = definitionsFetcher.getDefinitionsByNetwork(TYPE_DEFINITIONS_DEFAULT)
        val networkTreeRaw = definitionsFetcher.getDefinitionsByNetwork(networkName)

        val defaultTree = typesFromJson(defaultTreeRaw)
        val networkTree = typesFromJson(networkTreeRaw)

        val newestTypesChange = networkTree.versioning!!.maxOf { it.from }
        val typesVersion = maxOf(newestTypesChange, networkTree.runtimeId!!)

        runtimeDao.updateTypesVersion(networkName, typesVersion)

        runtimeCache.saveTypeDefinitions(TYPE_DEFINITIONS_DEFAULT, defaultTreeRaw)

        runtimeCache.saveTypeDefinitions(networkName, networkTreeRaw)

        return ConstructionParams(
            metadataRaw,
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

    private fun constructTypeRegistry(
        constructionParams: ConstructionParams
    ): TypeRegistry {
        val defaultTypePreset = TypeDefinitionParser.parseBaseDefinitions(
            constructionParams.defaultDefinitions,
            substratePreParsePreset()
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
