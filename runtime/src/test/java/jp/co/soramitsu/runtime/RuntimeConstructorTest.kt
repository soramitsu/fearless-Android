package jp.co.soramitsu.runtime

import com.google.gson.Gson
import jp.co.soramitsu.core_db.dao.RuntimeDao
import jp.co.soramitsu.core_db.model.RuntimeCacheEntry
import jp.co.soramitsu.fearless_utils.runtime.definitions.TypeDefinitionsTree
import jp.co.soramitsu.fearless_utils.runtime.metadata.GetMetadataRequest
import jp.co.soramitsu.fearless_utils.wsrpc.SocketService
import jp.co.soramitsu.fearless_utils.wsrpc.response.RpcResponse
import jp.co.soramitsu.test_shared.any
import jp.co.soramitsu.test_shared.eq
import jp.co.soramitsu.test_shared.isA
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.BDDMockito.given
import org.mockito.Mock
import org.mockito.Mockito.anyString
import org.mockito.Mockito.never
import org.mockito.Mockito.times
import org.mockito.Mockito.verify
import org.mockito.junit.MockitoJUnitRunner

private const val EMPTY_METADATA = "0x1111111122001100"

@RunWith(MockitoJUnitRunner::class)
class RuntimeConstructorTest {

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

    lateinit var runtimeConstructor: RuntimeConstructor

    @Suppress("UNCHECKED_CAST")
    @Before
    fun setup() {
        runBlocking {
            given(socketService.executeRequest(isA(GetMetadataRequest::class.java), deliveryType = any(), callback = any())).willAnswer {
                val callback = it.arguments[2] as SocketService.ResponseListener<RpcResponse>

                callback.onNext(metadataResponse)

                null
            }

            given(cache.getTypeDefinitions(anyString())).willReturn("")
            given(gson.fromJson(anyString(), eq(TypeDefinitionsTree::class.java))).willReturn(TypeDefinitionsTree(1, emptyMap(), emptyList()))

            given(definitionsFetcher.getDefinitionsByNetwork(anyString())).willReturn("server")
            given(definitionsFetcher.getDefinitionsByFile(anyString())).willReturn("server")

            runtimeConstructor = RuntimeConstructor(socketService, definitionsFetcher, gson, runtimeDao, cache)
        }
    }

    @Test
    fun `should use cache when runtime version is not changed and types are actual`() {
        runBlocking {
            dbReturnsCacheInfo(lastKnownVersion = 1, lastAppliedVersion = 1, typesVersion = 1)
            cacheReturnsMetadata(EMPTY_METADATA)

            val result = runtimeConstructor.constructRuntime(newRuntimeVersion = 1, "kusama")

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
            serverReturnsTypes(runtimeIdInRoot = 1, runtimeIdInVersioning = 1)

            val result = runtimeConstructor.constructRuntime(newRuntimeVersion = 2, "kusama")

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
            serverReturnsTypes(runtimeIdInRoot = 2, runtimeIdInVersioning = 2)

            val result = runtimeConstructor.constructRuntime(newRuntimeVersion = 2, "kusama")

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
            serverReturnsTypes(runtimeIdInRoot = 1, runtimeIdInVersioning = 1)

            val result = runtimeConstructor.constructRuntime(newRuntimeVersion = 2, "kusama")

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
            serverReturnsTypes(runtimeIdInRoot = 2, runtimeIdInVersioning = 2)

            val result = runtimeConstructor.constructRuntime(newRuntimeVersion = 2, "kusama")

            assertEquals(true, result.isNewest)

            verify(socketService, times(1)).executeRequest(isA(GetMetadataRequest::class.java), deliveryType = any(), callback = any())
            verify(definitionsFetcher, times(1)).getDefinitionsByFile(eq("default.json"))
            verify(definitionsFetcher, times(1)).getDefinitionsByFile(eq("kusama.json"))

            verify(runtimeDao, times(1)).updateTypesVersion(eq("kusama"), eq(2))
        }
    }

    @Test
    fun `should fetch runtime if outdated and mark that types are actual even if only versioning is updated`() {
        runBlocking {
            dbReturnsCacheInfo(lastKnownVersion = 1, lastAppliedVersion = 1, typesVersion = 1)
            cacheReturnsMetadata(EMPTY_METADATA)
            nodeReturnsMetadata(EMPTY_METADATA)
            serverReturnsTypes(runtimeIdInRoot = 1, runtimeIdInVersioning = 2)

            val result = runtimeConstructor.constructRuntime(newRuntimeVersion = 2, "kusama")

            assertEquals(true, result.isNewest)

            verify(socketService, times(1)).executeRequest(isA(GetMetadataRequest::class.java), deliveryType = any(), callback = any())
            verify(definitionsFetcher, times(1)).getDefinitionsByFile(eq("default.json"))
            verify(definitionsFetcher, times(1)).getDefinitionsByFile(eq("kusama.json"))

            verify(runtimeDao, times(1)).updateTypesVersion(eq("kusama"), eq(2))
        }
    }

    @Test
    fun `should fetch runtime if outdated and mark that types are actual even if only runtimeId is updated`() {
        runBlocking {
            dbReturnsCacheInfo(lastKnownVersion = 1, lastAppliedVersion = 1, typesVersion = 1)
            cacheReturnsMetadata(EMPTY_METADATA)
            nodeReturnsMetadata(EMPTY_METADATA)
            serverReturnsTypes(runtimeIdInRoot = 2, runtimeIdInVersioning = 1)

            val result = runtimeConstructor.constructRuntime(newRuntimeVersion = 2, "kusama")

            assertEquals(true, result.isNewest)

            verify(socketService, times(1)).executeRequest(isA(GetMetadataRequest::class.java), deliveryType = any(), callback = any())
            verify(definitionsFetcher, times(1)).getDefinitionsByFile(eq("default.json"))
            verify(definitionsFetcher, times(1)).getDefinitionsByFile(eq("kusama.json"))

            verify(runtimeDao, times(1)).updateTypesVersion(eq("kusama"), eq(2))
        }
    }

    @Test
    fun `should fetch runtime if node runtime version is lower and mark actual`() {
        runBlocking {
            dbReturnsCacheInfo(lastKnownVersion = 2, lastAppliedVersion = 2, typesVersion = 2)
            cacheReturnsMetadata(EMPTY_METADATA)
            nodeReturnsMetadata(EMPTY_METADATA)
            serverReturnsTypes(runtimeIdInRoot = 2, runtimeIdInVersioning = 2)

            val result = runtimeConstructor.constructRuntime(newRuntimeVersion = 1, "kusama")

            assertEquals(true, result.isNewest)

            verify(socketService, times(1)).executeRequest(isA(GetMetadataRequest::class.java), deliveryType = any(), callback = any())
            verify(definitionsFetcher, times(1)).getDefinitionsByFile(eq("default.json"))
            verify(definitionsFetcher, times(1)).getDefinitionsByFile(eq("kusama.json"))

            verify(runtimeDao, times(1)).updateTypesVersion(eq("kusama"), eq(2))
        }
    }

    private suspend fun cacheReturnsMetadata(metadata: String) = given(cache.getRuntimeMetadata(anyString())).willReturn(metadata)

    private fun nodeReturnsMetadata(metadata: String) = given(metadataResponse.result).willReturn(metadata)

    private fun serverReturnsTypes(runtimeIdInRoot: Int, runtimeIdInVersioning: Int) {
        given(gson.fromJson(eq("server"), eq(TypeDefinitionsTree::class.java))).willReturn(
            TypeDefinitionsTree(
                runtimeIdInRoot,
                emptyMap(),
                listOf(
                    TypeDefinitionsTree.Versioning(
                        range = listOf(runtimeIdInVersioning, null),
                        emptyMap()
                    )
                )
            )
        )
    }

    private suspend fun dbReturnsCacheInfo(
        lastKnownVersion: Int,
        lastAppliedVersion: Int,
        typesVersion: Int
    ) = given(runtimeDao.getCacheEntry(anyString())).willReturn(RuntimeCacheEntry("test", lastKnownVersion, lastAppliedVersion, typesVersion))
}