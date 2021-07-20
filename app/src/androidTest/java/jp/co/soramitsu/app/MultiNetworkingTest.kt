package jp.co.soramitsu.app

import android.util.Log
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.filters.LargeTest
import androidx.test.platform.app.InstrumentationRegistry
import com.google.gson.Gson
import com.google.gson.stream.JsonReader
import dagger.Component
import jp.co.soramitsu.common.data.network.runtime.binding.AccountInfo
import jp.co.soramitsu.common.data.network.runtime.binding.bindAccountInfo
import jp.co.soramitsu.common.di.CommonApi
import jp.co.soramitsu.common.di.Distinct
import jp.co.soramitsu.common.di.FeatureContainer
import jp.co.soramitsu.common.utils.system
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
import jp.co.soramitsu.fearless_utils.runtime.metadata.storage
import jp.co.soramitsu.fearless_utils.runtime.metadata.storageKey
import jp.co.soramitsu.fearless_utils.ss58.SS58Encoder.toAccountId
import jp.co.soramitsu.fearless_utils.wsrpc.SocketService
import jp.co.soramitsu.fearless_utils.wsrpc.executeAsync
import jp.co.soramitsu.fearless_utils.wsrpc.request.runtime.storage.SubscribeStorageRequest
import jp.co.soramitsu.fearless_utils.wsrpc.request.runtime.storage.storageChange
import jp.co.soramitsu.fearless_utils.wsrpc.subscriptionFlow
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.emitAll
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.merge
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.runBlocking
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import javax.inject.Inject
import javax.inject.Provider


@Component(
    dependencies = [
        CommonApi::class,
    ]
)
interface TestAppComponent {

    fun inject(test: MultiNetworkingTest)
}


@RunWith(AndroidJUnit4::class)
@LargeTest
class MultiNetworkingTest {

    private val context = InstrumentationRegistry.getInstrumentation().targetContext.applicationContext
    private val featureContainer = context as FeatureContainer

    @Inject
    @Distinct
    lateinit var socketServiceProvider: Provider<SocketService>

    @Inject
    lateinit var gson: Gson

    private val networks = mapOf(
        "kusama" to "wss://kusama.api.onfinality.io/public-ws",
        "polkadot" to "wss://polkadot.api.onfinality.io/public-ws",
        "westend" to "wss://westend-rpc.polkadot.io",
        "karura" to "wss://karura.api.onfinality.io/public-ws",
        "rococo" to "wss://rococo-community-rpc.laminar.codes/ws",
        "moonriver" to "wss://wss.moonriver.moonbeam.network/",
        "moonbase-alpha" to "wss://wss.testnet.moonbeam.network",
        "centrifuge" to "wss://fullnode.centrifuge.io/",
        "chainx" to "wss://chainx.elara.patract.io/",
        "dawinia" to "wss://rpc.darwinia.network/",
        "edgeware" to "wss://edgeware.elara.patract.io/",
        "kulupu" to "wss://kulupu.elara.patract.io/",
        "plasm" to "wss://plasm.elara.patract.io/",
        "sora" to "wss://mof2.sora.org/",
        "subsocial" to "wss://rpc.subsocial.network/",
        "statemine" to "wss://statemine.api.onfinality.io/public-ws"
    )

    @Before
    fun setup() {
        val component: TestAppComponent = DaggerTestAppComponent.builder()
            .commonApi(featureContainer.commonApi())
            .build()

        component.inject(this)
    }

    val accountId = "DXYZDV1ocjFihWfiqs5cKJzvvmxbpbURLXWH4ge3RdrVwXB".toAccountId()

    @Test
    fun testMultipleConnections() {
        val defaultJsonTree: TypeDefinitionsTree = gson.fromJson(JsonReader(getResourceReader("default.json")), TypeDefinitionsTree::class.java)

        runBlocking {
            val balanceSubscriptions = networks.map { (network, node) ->
                val service = socketServiceProvider.get()

                service.start(node)

                flow {

                    Log.d("RX", "Parsing types: $network")

                    val networkTypesTree: TypeDefinitionsTree = gson.fromJson(JsonReader(getResourceReader("runtime-${network}.json")), TypeDefinitionsTree::class.java)

                    val defaultTypePreset = TypeDefinitionParser.parseBaseDefinitions(defaultJsonTree, substratePreParsePreset()).typePreset
                    val networkTypePreset = TypeDefinitionParser.parseNetworkVersioning(networkTypesTree, defaultTypePreset).typePreset

                    val typeRegistry = TypeRegistry(
                        types = networkTypePreset,
                        dynamicTypeResolver = DynamicTypeResolver(
                            DynamicTypeResolver.DEFAULT_COMPOUND_EXTENSIONS + GenericsExtension
                        )
                    )

                    Log.d("RX", "Getting metadata for $network")

                    val runtimeMetadataRaw = service.executeAsync(GetMetadataRequest).result as String

                    Log.d("RX", "Constructing runtime:  $network")

                    val runtimeMetadataStruct = RuntimeMetadataSchema.read(runtimeMetadataRaw)

                    val runtimeMetadata = RuntimeMetadata(typeRegistry, runtimeMetadataStruct)
                    val runtime = RuntimeSnapshot(typeRegistry, runtimeMetadata)

                    Log.d("RX", "Start listening for balance: $network")

                    val key = runtime.metadata.system().storage("Account").storageKey(runtime, accountId)

                    val subscription = service.subscriptionFlow(SubscribeStorageRequest(listOf(key)))
                        .map { it.storageChange() }
                        .onEach {
                            val accountInfoRaw = it.getSingleChange()

                            val accountInfo = accountInfoRaw?.let { bindAccountInfo(it, runtime) } ?: AccountInfo.empty()

                            Log.d("RX", "Got new balance for $network at #${it.block}: ${accountInfo.data.free}")
                        }

                    emitAll(subscription)
                }.flowOn(Dispatchers.Default)
                    .catch { Log.d("RX", "Error in $network", it) }
            }

            balanceSubscriptions.merge().collect()
        }
    }
}
