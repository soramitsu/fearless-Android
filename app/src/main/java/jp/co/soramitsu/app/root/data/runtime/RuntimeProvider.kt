package jp.co.soramitsu.app.root.data.runtime

import com.google.gson.Gson
import jp.co.soramitsu.common.data.network.runtime.RuntimeVersion
import jp.co.soramitsu.core_db.dao.RuntimeDao
import jp.co.soramitsu.core_db.model.RuntimeCacheEntry
import jp.co.soramitsu.fearless_utils.runtime.RuntimeSnapshot
import jp.co.soramitsu.fearless_utils.runtime.definitions.TypeDefinitionParser
import jp.co.soramitsu.fearless_utils.runtime.definitions.TypeDefinitionsTree
import jp.co.soramitsu.fearless_utils.runtime.definitions.dynamic.DynamicTypeResolver
import jp.co.soramitsu.fearless_utils.runtime.definitions.dynamic.extentsions.GenericsExtension
import jp.co.soramitsu.fearless_utils.runtime.definitions.registry.TypeRegistry
import jp.co.soramitsu.fearless_utils.runtime.definitions.registry.substratePreParsePreset
import jp.co.soramitsu.fearless_utils.runtime.metadata.GetMetadataRequest
import jp.co.soramitsu.fearless_utils.runtime.metadata.RuntimeMetadata
import jp.co.soramitsu.fearless_utils.runtime.metadata.RuntimeMetadataSchema
import jp.co.soramitsu.fearless_utils.wsrpc.SocketService
import jp.co.soramitsu.fearless_utils.wsrpc.executeAsync
import jp.co.soramitsu.fearless_utils.wsrpc.mappers.nonNull
import jp.co.soramitsu.fearless_utils.wsrpc.mappers.pojo
import jp.co.soramitsu.fearless_utils.wsrpc.request.runtime.chain.RuntimeVersionRequest
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

private const val TYPE_DEFINITIONS_DEFAULT = "default"

class RuntimeParams(
    val metadataRaw: String,
    val defaultDefinitions: TypeDefinitionsTree,
    val networkDefinitions: TypeDefinitionsTree,
    val areNewest: Boolean
)

class RuntimeProvider(
    private val socketService: SocketService,
    private val definitionsFetcher: DefinitionsFetcher,
    private val gson: Gson,
    private val runtimeDao: RuntimeDao,
    private val runtimeCache: RuntimeCache
) {

    class Prepared(val runtime: RuntimeSnapshot, val isNewest: Boolean)

    suspend fun prepareRuntime(networkName: String): Prepared = withContext(Dispatchers.Default) {
        val runtimeParams = getRuntimeParams(networkName)

        val typeRegistry = constructTypeRegistry(runtimeParams)

        val runtimeMetadataStruct = RuntimeMetadataSchema.read(runtimeParams.metadataRaw)
        val runtimeMetadata = RuntimeMetadata(typeRegistry, runtimeMetadataStruct)


        val runtime = RuntimeSnapshot(typeRegistry, runtimeMetadata)

        Prepared(
            runtime,
            isNewest = runtimeParams.areNewest
        )
    }

    private suspend fun getRuntimeParams(networkName: String): RuntimeParams {
        val runtimeInfo = socketService.executeAsync(RuntimeVersionRequest(), mapper = pojo<RuntimeVersion>().nonNull())

        val latestRuntimeVersion = runtimeInfo.specVersion

        val cacheInfo = cacheInfoOrCreateDefault(networkName)

        if (latestRuntimeVersion > cacheInfo.latestKnownVersion) {
            runtimeDao.updateLatestKnownVersion(networkName, latestRuntimeVersion)
        }

        val metadataRaw = if (latestRuntimeVersion <= cacheInfo.latestAppliedVersion) {
            val metadataRaw = runtimeCache.getRuntimeMetadata(networkName)!!

            if (latestRuntimeVersion <= cacheInfo.typesVersion) {
                val (default, network) = networkTypesFromCache(networkName)

                return RuntimeParams(metadataRaw, default, network, areNewest = true)
            }

            metadataRaw
        } else {
            val metadataRaw = socketService.executeAsync(GetMetadataRequest).result as String

            runtimeCache.saveRuntimeMetadata(networkName, metadataRaw)
            runtimeDao.updateLatestAppliedVersion(networkName, latestRuntimeVersion)

            metadataRaw
        }

        val defaultTreeRaw = definitionsFetcher.getDefinitionsByNetwork(TYPE_DEFINITIONS_DEFAULT)
        val networkTreeRaw = definitionsFetcher.getDefinitionsByNetwork(networkName)

        val defaultTree = typesFromJson(defaultTreeRaw)
        val networkTree = typesFromJson(networkTreeRaw)

        val typesVersion = networkTree.runtimeId!!

        runtimeDao.updateTypesVersion(networkName, typesVersion)

        runtimeCache.saveTypeDefinitions(TYPE_DEFINITIONS_DEFAULT, defaultTreeRaw)

        runtimeCache.saveTypeDefinitions(networkName, networkTreeRaw)

        return RuntimeParams(
            metadataRaw,
            defaultTree,
            networkTree,
            areNewest = latestRuntimeVersion <= typesVersion
        )
    }

    private suspend fun cacheInfoOrCreateDefault(networkName: String): RuntimeCacheEntry {
        val cacheEntry = runtimeDao.getCacheEntry(networkName)

        return if (cacheEntry != null) {
            cacheEntry
        } else {
            val default = RuntimeCacheEntry.default(networkName)

            runtimeDao.insertCacheEntry(default)

            default
        }
    }

    private suspend fun networkTypesFromCache(networkName: String): Pair<TypeDefinitionsTree, TypeDefinitionsTree> {
        val defaultRaw = runtimeCache.getTypeDefinitions(TYPE_DEFINITIONS_DEFAULT)!!
        val defaultTree = typesFromJson(defaultRaw)

        val networkRaw = runtimeCache.getTypeDefinitions(networkName)!!
        val networkTree = typesFromJson(networkRaw)

        return defaultTree to networkTree
    }

    private fun typesFromJson(typeDefinitions: String) = gson.fromJson(typeDefinitions, TypeDefinitionsTree::class.java)

    private fun constructTypeRegistry(runtimeParams: RuntimeParams): TypeRegistry {
        val defaultTypePreset = TypeDefinitionParser.parseTypeDefinitions(runtimeParams.defaultDefinitions, substratePreParsePreset()).typePreset
        val networkTypePreset = TypeDefinitionParser.parseTypeDefinitions(runtimeParams.networkDefinitions, defaultTypePreset).typePreset

        return TypeRegistry(
            types = networkTypePreset,
            dynamicTypeResolver = DynamicTypeResolver(
                DynamicTypeResolver.DEFAULT_COMPOUND_EXTENSIONS + GenericsExtension
            )
        )
    }
}