package jp.co.soramitsu.app.root.data.runtime

import com.google.gson.Gson
import jp.co.soramitsu.core_db.dao.RuntimeDao
import jp.co.soramitsu.core_db.model.RuntimeCacheEntry
import jp.co.soramitsu.fearless_utils.runtime.definitions.TypeDefinitionsTree
import jp.co.soramitsu.fearless_utils.runtime.metadata.GetMetadataRequest
import jp.co.soramitsu.fearless_utils.wsrpc.SocketService
import jp.co.soramitsu.fearless_utils.wsrpc.response.RpcResponse
import jp.co.soramitsu.test.any
import jp.co.soramitsu.test.eq
import jp.co.soramitsu.test.isA
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.Mock
import org.mockito.Mockito.`when`
import org.mockito.Mockito.anyString
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
    lateinit var runtimeDao: RuntimeDao

    @Mock
    lateinit var definitionsFetcher: DefinitionsFetcher

    @Mock
    lateinit var socketService: SocketService

    @Mock
    lateinit var metadataResponse: RpcResponse

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

            `when`(cache.getTypeDefinitions(anyString())).thenReturn("")
            `when`(gson.fromJson(anyString(), eq(TypeDefinitionsTree::class.java))).thenReturn(TypeDefinitionsTree(1, emptyMap()))

            `when`(definitionsFetcher.getDefinitionsByNetwork(anyString())).thenReturn("server")
            `when`(definitionsFetcher.getDefinitionsByFile(anyString())).thenReturn("server")

            runtimeProvider = RuntimeProvider(socketService, definitionsFetcher, gson, runtimeDao, cache)
        }
    }

    @Test
    fun `should use cache when runtime version is not changed and types are actual`() {
        runBlocking {
            dbReturnsCacheInfo(lastKnownVersion = 1, lastAppliedVersion = 1, typesVersion = 1)
            cacheReturnsMetadata(EMPTY_METADATA)

            val result = runtimeProvider.prepareRuntime(latestRuntimeVersion = 1,"kusama")

            assertEquals(true, result.isNewest)

            verify(socketService, never()).executeRequest(isA(GetMetadataRequest::class.java), deliveryType = any(), callback = any())
            verify(definitionsFetcher, never()).getDefinitionsByFile(eq("default.json"))
            verify(definitionsFetcher, never()).getDefinitionsByFile(eq("kusama.json"))
        }
    }

    @Test
    fun `should fetch only types and mark not actual`() {
        runBlocking {
            dbReturnsCacheInfo(lastKnownVersion = 2, lastAppliedVersion = 2, typesVersion = 1)
            cacheReturnsMetadata(EMPTY_METADATA)
            serverReturnsTypes(runtimeId = 1)

            val result = runtimeProvider.prepareRuntime(latestRuntimeVersion = 2, "kusama")

            assertEquals(false, result.isNewest)

            verify(socketService, never()).executeRequest(isA(GetMetadataRequest::class.java), deliveryType = any(), callback = any())
            verify(definitionsFetcher, times(1)).getDefinitionsByFile(eq("default.json"))
            verify(definitionsFetcher, times(1)).getDefinitionsByFile(eq("kusama.json"))

            verify(runtimeDao, times(1)).updateTypesVersion(eq("kusama"), eq(1))
        }
    }

    @Test
    fun `should fetch only types and mark actual`() {
        runBlocking {
            dbReturnsCacheInfo(lastKnownVersion = 2, lastAppliedVersion = 2, typesVersion = 1)
            cacheReturnsMetadata(EMPTY_METADATA)
            serverReturnsTypes(runtimeId = 2)

            val result = runtimeProvider.prepareRuntime(latestRuntimeVersion = 2, "kusama")

            assertEquals(true, result.isNewest)

            verify(socketService, never()).executeRequest(isA(GetMetadataRequest::class.java), deliveryType = any(), callback = any())
            verify(definitionsFetcher, times(1)).getDefinitionsByFile(eq("default.json"))
            verify(definitionsFetcher, times(1)).getDefinitionsByFile(eq("kusama.json"))

            verify(runtimeDao, times(1)).updateTypesVersion(eq("kusama"), eq(2))
        }
    }

    @Test
    fun `should fetch runtime if outdated and mark that types are outdated`() {
        runBlocking {
            dbReturnsCacheInfo(lastKnownVersion = 1, lastAppliedVersion = 1, typesVersion = 1)
            cacheReturnsMetadata(EMPTY_METADATA)
            nodeReturnsMetadata(EMPTY_METADATA)
            serverReturnsTypes(runtimeId = 1)

            val result = runtimeProvider.prepareRuntime(latestRuntimeVersion = 2, "kusama")

            assertEquals(false, result.isNewest)

            verify(socketService, times(1)).executeRequest(isA(GetMetadataRequest::class.java), deliveryType = any(), callback = any())
            verify(definitionsFetcher, times(1)).getDefinitionsByFile(eq("default.json"))
            verify(definitionsFetcher, times(1)).getDefinitionsByFile(eq("kusama.json"))

            verify(runtimeDao, times(1)).updateTypesVersion(eq("kusama"), eq(1))
        }
    }

    @Test
    fun `should fetch runtime if outdated and mark that types are actual`() {
        runBlocking {
            dbReturnsCacheInfo(lastKnownVersion = 1, lastAppliedVersion = 1, typesVersion = 1)
            cacheReturnsMetadata(EMPTY_METADATA)
            nodeReturnsMetadata(EMPTY_METADATA)
            serverReturnsTypes(runtimeId = 2)

            val result = runtimeProvider.prepareRuntime(latestRuntimeVersion = 2, "kusama")

            assertEquals(true, result.isNewest)

            verify(socketService, times(1)).executeRequest(isA(GetMetadataRequest::class.java), deliveryType = any(), callback = any())
            verify(definitionsFetcher, times(1)).getDefinitionsByFile(eq("default.json"))
            verify(definitionsFetcher, times(1)).getDefinitionsByFile(eq("kusama.json"))

            verify(runtimeDao, times(1)).updateTypesVersion(eq("kusama"), eq(2))
        }
    }

    private suspend fun cacheReturnsMetadata(metadata: String) = `when`(cache.getRuntimeMetadata(anyString())).thenReturn(metadata)

    private fun nodeReturnsMetadata(metadata: String) = `when`(metadataResponse.result).thenReturn(metadata)

    private fun serverReturnsTypes(runtimeId: Int) {
        `when`(gson.fromJson(eq("server"), eq(TypeDefinitionsTree::class.java))).thenReturn(TypeDefinitionsTree(runtimeId, emptyMap()))
    }

    private suspend fun dbReturnsCacheInfo(
        lastKnownVersion: Int,
        lastAppliedVersion: Int,
        typesVersion: Int
    ) = `when`(runtimeDao.getCacheEntry(anyString())).thenReturn(RuntimeCacheEntry("test", lastKnownVersion, lastAppliedVersion, typesVersion))
}