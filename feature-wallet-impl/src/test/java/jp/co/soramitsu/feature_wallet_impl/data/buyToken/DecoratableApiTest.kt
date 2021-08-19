package jp.co.soramitsu.feature_wallet_impl.data.buyToken

import com.google.gson.Gson
import com.google.gson.stream.JsonReader
import com.neovisionaries.ws.client.WebSocketFactory
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
import jp.co.soramitsu.fearless_utils.wsrpc.logging.Logger
import jp.co.soramitsu.fearless_utils.wsrpc.recovery.Reconnector
import jp.co.soramitsu.fearless_utils.wsrpc.request.RequestExecutor
import jp.co.soramitsu.feature_wallet_impl.data.buyToken.derive.staking.historyDepth
import jp.co.soramitsu.feature_wallet_impl.data.buyToken.derive.staking.staking
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.runBlocking
import org.junit.Test
import java.io.BufferedReader
import java.io.InputStreamReader
import java.io.Reader

fun Any.getResourceReader(fileName: String): Reader {
    val stream = javaClass.classLoader!!.getResourceAsStream(fileName)

    return BufferedReader(InputStreamReader(stream))
}

class StdoutLogger : Logger {
    override fun log(message: String?) {
        println(message)
    }

    override fun log(throwable: Throwable?) {
        throwable?.printStackTrace()
    }

}

class TestApi {

    @Test
    fun test() = runBlocking {
        val gson = Gson()
        val socketService = SocketService(gson, StdoutLogger(), WebSocketFactory(), Reconnector(), RequestExecutor())

        socketService.start("wss://polkadot.api.onfinality.io/ws?apikey=0b2faaa5-3ef1-48ea-bf75-8f3a0cedb1ef")

        val defaultJsonTree: TypeDefinitionsTree = gson.fromJson(JsonReader(getResourceReader("default.json")), TypeDefinitionsTree::class.java)
        val networkTypesTree: TypeDefinitionsTree = gson.fromJson(JsonReader(getResourceReader("polkadot.json")), TypeDefinitionsTree::class.java)

        val defaultTypePreset = TypeDefinitionParser.parseBaseDefinitions(defaultJsonTree, substratePreParsePreset()).typePreset
        val networkTypePreset = TypeDefinitionParser.parseNetworkVersioning(networkTypesTree, defaultTypePreset).typePreset

        val typeRegistry = TypeRegistry(
            types = networkTypePreset,
            dynamicTypeResolver = DynamicTypeResolver(
                DynamicTypeResolver.DEFAULT_COMPOUND_EXTENSIONS + GenericsExtension
            )
        )

        val runtimeMetadataRaw = socketService.executeAsync(GetMetadataRequest).result as String

        val runtimeMetadataStruct = RuntimeMetadataSchema.read(runtimeMetadataRaw)

        val runtimeMetadata = RuntimeMetadata(typeRegistry, runtimeMetadataStruct)
        val runtime = RuntimeSnapshot(typeRegistry, runtimeMetadata)

        val api = SubstrateApi(runtime, socketService)

        api.query.staking.historyDepth.subscribe()
            .onEach { println(it) }
            .collect()
    }
}
