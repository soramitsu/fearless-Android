package jp.co.soramitsu.common.data.network.runtime

import android.util.Log
import com.google.gson.Gson
import jp.co.soramitsu.common.data.network.rpc.ConnectionManager
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
import jp.co.soramitsu.fearless_utils.wsrpc.state.SocketStateMachine
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.first
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
    private val connectionManager: ConnectionManager,
    private val runtimeCache: RuntimeCache
) {

    class Prepared(val runtime: RuntimeSnapshot, val isNewest: Boolean)

    suspend fun prepareRuntime(networkName: String): Prepared = withContext(Dispatchers.Default) {
        Log.d("RX", "------------------------\n")

        connectionManager.networkStateFlow().filter { it is SocketStateMachine.State.Connected }
            .first() // wait for connect

        val runtimeParams = getRuntimeParams(networkName)

        val typeRegistry = timeIt("Construct type registry") { constructTypeRegistry(runtimeParams) }

        val runtimeMetadata = timeIt("Parse metadata") {
            val runtimeMetadataStruct = RuntimeMetadataSchema.read(runtimeParams.metadataRaw)
            RuntimeMetadata(typeRegistry, runtimeMetadataStruct)
        }

       val runtime = RuntimeSnapshot(typeRegistry, runtimeMetadata)

        Log.d("RX", "------------------------\n")

        Prepared(
            runtime,
            isNewest = runtimeParams.areNewest
        )
    }

    private suspend fun getRuntimeParams(networkName: String): RuntimeParams {
        val runtimeInfo = timeIt("Fetch runtime version") {
            socketService.executeAsync(RuntimeVersionRequest(), mapper = pojo<RuntimeVersion>().nonNull())
        }

        val latestRuntimeVersion = runtimeInfo.specVersion

        val metadataRaw = if (latestRuntimeVersion > runtimeCache.currentRuntimeVersion(networkName) ) {

            val metadataRaw = timeIt("Fetch metadata from network") { socketService.executeAsync(GetMetadataRequest).result as String }


            timeIt("Cache metadata") { runtimeCache.saveRuntimeMetadata(networkName, metadataRaw)  }
            runtimeCache.updateCurrentRuntimeVersion(networkName, latestRuntimeVersion)

            metadataRaw
        } else {
            val metadataRaw =  timeIt("Fetch metadata from cache") {  runtimeCache.getRuntimeMetadata(networkName)!! }

            if (runtimeCache.areTypeDefinitionsActual(networkName)) {
                val (default, network) = networkTypesFromCache(networkName)

                return RuntimeParams(metadataRaw, default, network, areNewest = true)
            }

            metadataRaw
        }

        val defaultTreeRaw = timeIt("Fetch default types from network") { definitionsFetcher.getDefinitionsByNetwork(TYPE_DEFINITIONS_DEFAULT) }
        val networkTreeRaw = timeIt("Fetch $networkName types from network") { definitionsFetcher.getDefinitionsByNetwork(networkName) }

        val defaultTree = typesFromJson(defaultTreeRaw)
        val networkTree = typesFromJson(networkTreeRaw)

        val runtimeVersionInTypes = networkTree.runtimeId!!

        val areDefinitionsActual = runtimeVersionInTypes >= latestRuntimeVersion

        runtimeCache.setTypeDefinitionsActual(networkName, areDefinitionsActual)

        timeIt("Save default types to cache") {
            runtimeCache.saveTypeDefinitions(TYPE_DEFINITIONS_DEFAULT, defaultTreeRaw)
        }

        timeIt("Save $networkName types to cache") {
            runtimeCache.saveTypeDefinitions(networkName, networkTreeRaw)
        }

        return RuntimeParams(
            metadataRaw,
            defaultTree,
            networkTree,
            areNewest = areDefinitionsActual
        )
    }

    suspend fun <T> timeIt(message: String = "", block: suspend () -> T): T {
        val start = System.currentTimeMillis()
        val r = block()
        val end = System.currentTimeMillis()
        Log.d("RX", "$message: ${end - start} ms\n")
        return r
    }

    private suspend fun networkTypesFromCache(networkName: String): Pair<TypeDefinitionsTree, TypeDefinitionsTree> {
        val defaultRaw = timeIt("Fetch default types from cache") {  runtimeCache.getTypeDefinitions(TYPE_DEFINITIONS_DEFAULT)!! }
        val defaultTree = typesFromJson(defaultRaw)

        val networkRaw = timeIt("Fetch $networkName types from cache") { runtimeCache.getTypeDefinitions(networkName)!! }
        val networkTree = typesFromJson(networkRaw)

        return defaultTree to networkTree
    }

    private suspend fun typesFromJson(typeDefinitions: String) = timeIt("Parse types") { gson.fromJson(typeDefinitions, TypeDefinitionsTree::class.java) }

    private suspend fun constructTypeRegistry(runtimeParams: RuntimeParams) = withContext(Dispatchers.Default) {
        val defaultTypePreset = TypeDefinitionParser.parseTypeDefinitions(runtimeParams.defaultDefinitions, substratePreParsePreset()).typePreset
        val networkTypePreset = TypeDefinitionParser.parseTypeDefinitions(runtimeParams.networkDefinitions, defaultTypePreset).typePreset

        TypeRegistry(
            types = networkTypePreset,
            dynamicTypeResolver = DynamicTypeResolver(
                DynamicTypeResolver.DEFAULT_COMPOUND_EXTENSIONS + GenericsExtension
            )
        )
    }
}