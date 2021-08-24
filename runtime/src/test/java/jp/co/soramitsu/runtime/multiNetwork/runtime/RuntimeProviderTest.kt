package jp.co.soramitsu.runtime.multiNetwork.runtime

import jp.co.soramitsu.fearless_utils.runtime.RuntimeSnapshot
import jp.co.soramitsu.runtime.multiNetwork.chain.model.Chain
import jp.co.soramitsu.runtime.multiNetwork.chain.model.TypesUsage
import jp.co.soramitsu.runtime.multiNetwork.runtime.types.BaseTypeSynchronizer
import jp.co.soramitsu.test_shared.any
import jp.co.soramitsu.test_shared.eq
import jp.co.soramitsu.test_shared.thenThrowUnsafe
import jp.co.soramitsu.test_shared.whenever
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.Mock
import org.mockito.Mockito.times
import org.mockito.Mockito.verify
import org.mockito.junit.MockitoJUnitRunner

@RunWith(MockitoJUnitRunner::class)
class RuntimeProviderTest {

    lateinit var baseTypeSyncFlow: MutableSharedFlow<FileHash>
    lateinit var chainSyncFlow: MutableSharedFlow<SyncResult>

    lateinit var chain: Chain

    @Mock
    lateinit var runtime: RuntimeSnapshot

    @Mock
    lateinit var constructedRuntime: ConstructedRuntime

    @Mock
    lateinit var runtimeSyncService: RuntimeSyncService

    @Mock
    lateinit var runtimeFactory: RuntimeFactory

    @Mock
    lateinit var baseTypesSynchronizer: BaseTypeSynchronizer

    lateinit var runtimeProvider: RuntimeProvider

    @Before
    fun setup() {
        runBlocking {
            chain = Mocks.chain(id = "1")

            baseTypeSyncFlow = MutableSharedFlow()
            chainSyncFlow = MutableSharedFlow()

            whenever(constructedRuntime.runtime).thenReturn(runtime)
            whenever(runtimeFactory.constructRuntime(any(), any())).thenReturn(constructedRuntime)

            whenever(baseTypesSynchronizer.syncStatusFlow).thenAnswer { baseTypeSyncFlow }
            whenever(runtimeSyncService.syncResultFlow(eq(chain.id))).thenAnswer { chainSyncFlow }
        }
    }

    @Test
    fun `should init from cache`() {
        runBlocking {
            initProvider()

            verify(runtimeFactory, times(1)).constructRuntime(eq(chain.id), any())

            val returnedRuntime = withTimeout(timeMillis = 10) {
                runtimeProvider.get()
            }

            assertEquals(returnedRuntime, runtime)
        }
    }

    @Test
    fun `should not reconstruct runtime if base types has remains the same`() {
        runBlocking {
            initProvider()

            currentBaseTypesHash("Hash")

            baseTypeSyncFlow.emit("Hash")

            verifyReconstructionNotStarted()
        }
    }

    @Test
    fun `should not reconstruct runtime on base types change if they are not used`() {
        runBlocking {
            initProvider(typesUsage = TypesUsage.OWN)

            baseTypeSyncFlow.emit("Hash")

            verifyReconstructionNotStarted()
        }
    }

    @Test
    fun `should reconstruct runtime if base types changes`() {
        runBlocking {
            initProvider()

            currentBaseTypesHash("Hash")

            baseTypeSyncFlow.emit("Changed Hash")

            verifyReconstructionStarted()
        }
    }

    @Test
    fun `should not reconstruct runtime if chain metadata or types did not change`() {
        runBlocking {
            initProvider()

            currentChainTypesHash("Hash")
            currentMetadataHash("Hash")

            chainSyncFlow.emit(SyncResult(chain.id, metadataHash = "Hash", typesHash = "Hash"))

            verifyReconstructionNotStarted()
        }
    }

    @Test
    fun `should reconstruct runtime if chain metadata or types changed`() {
        runBlocking {
            initProvider()

            currentChainTypesHash("Hash")
            currentMetadataHash("Hash")

            chainSyncFlow.emit(SyncResult(chain.id, metadataHash = "Hash Changed", typesHash = "Hash"))

            verifyReconstructionAfterInit(1)

            chainSyncFlow.emit(SyncResult(chain.id, metadataHash = "Hash Changed", typesHash = "Hash Changed"))

            verifyReconstructionAfterInit(2)
        }
    }

    @Test
    fun `should reconstruct runtime on chain info sync if cache init failed`() {
        runBlocking {
            withRuntimeFactoryFailing {
                chainSyncFlow.emit(SyncResult(chain.id, metadataHash = "Hash Changed", typesHash = "Hash"))

                verifyReconstructionStarted()
            }
        }
    }

    @Test
    fun `should report missing cache for base types`() {
        runBlocking {
            withRuntimeFactoryFailing(BaseTypesNotInCacheException) {
                verify(baseTypesSynchronizer, times(1)).cacheNotFound()
                verify(runtimeSyncService, times(0)).cacheNotFound(any())
            }
        }
    }

    @Test
    fun `should report missing cache for chain types or metadata`() {
        runBlocking {
            withRuntimeFactoryFailing(ChainInfoNotInCacheException) {
                verify(runtimeSyncService, times(1)).cacheNotFound(eq(chain.id))
                verify(baseTypesSynchronizer, times(0)).cacheNotFound()
            }
        }
    }

    @Test
    fun `should construct runtime on base types sync if cache init failed`() {
        runBlocking {
            withRuntimeFactoryFailing {
                baseTypeSyncFlow.emit("Hash")

                verifyReconstructionStarted()
            }
        }
    }

    @Test
    fun `should construct runtime on type usage change`() {
        runBlocking {
            initProvider(typesUsage = TypesUsage.BASE)

            runtimeProvider.considerUpdatingTypesUsage(TypesUsage.OWN)

            verifyReconstructionStarted()
        }
    }

    @Test
    fun `should not construct runtime on same type usage`() {
        runBlocking {
            initProvider(typesUsage = TypesUsage.BASE)

            runtimeProvider.considerUpdatingTypesUsage(TypesUsage.BASE)

            verifyReconstructionNotStarted()
        }
    }

    private suspend fun verifyReconstructionNotStarted() {
        verifyReconstructionAfterInit(0)
    }

    private suspend fun verifyReconstructionStarted() {
        verifyReconstructionAfterInit(1)
    }

    private suspend fun withRuntimeFactoryFailing(exception: Exception = BaseTypesNotInCacheException, block: suspend () -> Unit) {
        whenever(runtimeFactory.constructRuntime(any(), any())).thenThrowUnsafe(exception)

        initProvider()

        delay(10)

        block()
    }

    private suspend fun verifyReconstructionAfterInit(times: Int) {
        delay(10)

        // + 1 since it is called once in init (cache)
        verify(runtimeFactory, times(times + 1)).constructRuntime(eq(chain.id), any())
    }

    private fun currentBaseTypesHash(hash: String?) {
        whenever(constructedRuntime.baseTypesHash).thenReturn(hash)
    }

    private fun currentMetadataHash(hash: String?) {
        whenever(constructedRuntime.metadataHash).thenReturn(hash)
    }

    private fun currentChainTypesHash(hash: String?) {
        whenever(constructedRuntime.ownTypesHash).thenReturn(hash)
    }

    private fun initProvider(typesUsage: TypesUsage? = null) {
        val types = when (typesUsage) {
            TypesUsage.OWN -> Chain.Types(url = "url", overridesCommon = true)
            TypesUsage.BOTH -> Chain.Types(url = "url", overridesCommon = false)
            else -> null
        }

        whenever(chain.types).thenReturn(types)

        runtimeProvider = RuntimeProvider(runtimeFactory, runtimeSyncService, baseTypesSynchronizer, chain)
    }
}
