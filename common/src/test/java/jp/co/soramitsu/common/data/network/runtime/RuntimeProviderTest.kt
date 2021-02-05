package jp.co.soramitsu.common.data.network.runtime

import com.google.gson.Gson
import jp.co.soramitsu.common.any
import jp.co.soramitsu.common.eq
import jp.co.soramitsu.common.isA
import jp.co.soramitsu.fearless_utils.runtime.definitions.TypeDefinitionsTree
import jp.co.soramitsu.fearless_utils.runtime.metadata.GetMetadataRequest
import jp.co.soramitsu.fearless_utils.wsrpc.SocketService
import jp.co.soramitsu.fearless_utils.wsrpc.request.runtime.chain.RuntimeVersionRequest
import jp.co.soramitsu.fearless_utils.wsrpc.response.RpcResponse
import kotlinx.coroutines.runBlocking
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.Mock
import org.mockito.Mockito.`when`
import org.mockito.Mockito.anyString
import org.mockito.Mockito.atLeastOnce
import org.mockito.Mockito.never
import org.mockito.Mockito.times
import org.mockito.Mockito.verify
import org.mockito.junit.MockitoJUnitRunner

private const val EMPTY_METADATA = "0x1111111122001100"

@RunWith(MockitoJUnitRunner::class)
class RuntimeProviderTest {

    @Mock
    lateinit var cache: RuntimeCache

    @Mock
    lateinit var definitionsFetcher: DefinitionsFetcher

    @Mock
    lateinit var socketService: SocketService

    @Mock
    lateinit var metadataResponse: RpcResponse

    @Mock
    lateinit var runtimeVersionResponse: RpcResponse

    @Mock
    lateinit var gson: Gson

    lateinit var runtimeProvider: RuntimeProvider

    @Suppress("UNCHECKED_CAST")
    @Before
    fun setup() {
        runBlocking {
            `when`(socketService.executeRequest(isA(GetMetadataRequest::class.java), deliveryType = any(), callback = any())).thenAnswer {
                val callback = it.arguments[2] as SocketService.ResponseListener<RpcResponse>

                callback.onNext(metadataResponse)

                null
            }

            `when`(socketService.executeRequest(isA(RuntimeVersionRequest::class.java), deliveryType = any(), callback = any())).thenAnswer {
                val callback = it.arguments[2] as SocketService.ResponseListener<RpcResponse>

                callback.onNext(runtimeVersionResponse)

                null
            }


            `when`(cache.getTypeDefinitions(anyString())).thenReturn("")
            `when`(gson.fromJson(anyString(), eq(TypeDefinitionsTree::class.java))).thenReturn(TypeDefinitionsTree(1, emptyMap()))

            `when`(socketService.jsonMapper).thenReturn(gson)


            runtimeProvider = RuntimeProvider(socketService, definitionsFetcher, gson, cache)
        }
    }

    @Test
    fun `should use cache when runtime version is not changed and types are actual`() {
        runBlocking {
            nodeReturnsRuntimeVersion(1)
            cacheReturnsRuntimeVersion(1)
            cacheReturnsMetadata(EMPTY_METADATA)
            cacheReturnsAreTypeDefinitionsActual(true)

            val result = runtimeProvider.prepareRuntime("kusama")

            assert(result.isSuccess)

            verify(definitionsFetcher, never()).getDefinitionsByFile(any())
        }
    }

    @Test
    fun `should fetch only types and mark not actual`() {
        runBlocking {
            nodeReturnsRuntimeVersion(2)
            cacheReturnsRuntimeVersion(2)
            cacheReturnsMetadata(EMPTY_METADATA)
            serverReturnsTypes(runtimeId = 1)
            cacheReturnsAreTypeDefinitionsActual(false)

            val result = runtimeProvider.prepareRuntime("kusama")

            assert(result.isSuccess)

            verify(cache, times(1)).setTypeDefinitionsActual(eq("kusama"), eq(false))
        }
    }

    @Test
    fun `should fetch only types and mark actual`() {
        runBlocking {
            nodeReturnsRuntimeVersion(2)
            cacheReturnsRuntimeVersion(2)
            cacheReturnsMetadata(EMPTY_METADATA)
            cacheReturnsAreTypeDefinitionsActual(false)
            serverReturnsTypes(runtimeId = 2)

            val result = runtimeProvider.prepareRuntime("kusama")

            assert(result.isSuccess)

            verify(cache, times(1)).setTypeDefinitionsActual(eq("kusama"), eq(true))
        }
    }

    @Test
    fun `should fetch runtime if outdated and mark that types are outdated`() {
        runBlocking {
            nodeReturnsRuntimeVersion(2)
            cacheReturnsRuntimeVersion(1)
            nodeReturnsMetadata(EMPTY_METADATA)
            serverReturnsTypes(runtimeId = 1)

            val result = runtimeProvider.prepareRuntime("kusama")

            assert(result.isSuccess)

            verify(cache, atLeastOnce()).saveRuntimeMetadata(eq("kusama"), eq(EMPTY_METADATA))

            verify(cache, times(1)).setTypeDefinitionsActual(eq("kusama"), eq(false))

            verify(cache, times(1)).saveTypeDefinitions(eq("kusama"), any())
            verify(cache, times(1)).saveTypeDefinitions(eq("default"), any())
        }
    }

    @Test
    fun `should fetch runtime if outdated and mark that types are actual`() {
        runBlocking {
            nodeReturnsRuntimeVersion(2)
            cacheReturnsRuntimeVersion(1)
            nodeReturnsMetadata(EMPTY_METADATA)
            serverReturnsTypes(runtimeId = 2)

            val result = runtimeProvider.prepareRuntime("kusama")

            assert(result.isSuccess)

            verify(cache, atLeastOnce()).saveRuntimeMetadata(eq("kusama"), eq(EMPTY_METADATA))

            verify(cache, times(1)).setTypeDefinitionsActual(eq("kusama"), eq(true))

            verify(cache, times(1)).saveTypeDefinitions(eq("kusama"), any())
            verify(cache, times(1)).saveTypeDefinitions(eq("default"), any())
        }
    }

    private fun cacheReturnsAreTypeDefinitionsActual(actual: Boolean) {
        `when`(cache.areTypeDefinitionsActual(anyString())).thenReturn(actual)
    }

    private suspend fun cacheReturnsMetadata(metadata: String) = `when`(cache.getRuntimeMetadata(anyString())).thenReturn(metadata)

    private fun nodeReturnsMetadata(metadata: String) = `when`(metadataResponse.result).thenReturn(metadata)

    private suspend fun serverReturnsTypes(runtimeId: Int) {
        `when`(definitionsFetcher.getDefinitionsByNetwork(anyString())).thenReturn("server")
        `when`(gson.fromJson(eq("server"), eq(TypeDefinitionsTree::class.java))).thenReturn(TypeDefinitionsTree(runtimeId, emptyMap()))
    }

    private fun nodeReturnsRuntimeVersion(runtimeVersion: Int) = `when`(runtimeVersionResponse.result).thenReturn(RuntimeVersion(runtimeVersion, 0))

    private fun cacheReturnsRuntimeVersion(runtimeVersion: Int) = `when`(cache.currentRuntimeVersion(anyString())).thenReturn(runtimeVersion)
}