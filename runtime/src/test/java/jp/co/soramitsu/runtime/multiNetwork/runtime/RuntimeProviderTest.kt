package jp.co.soramitsu.runtime.multiNetwork.runtime

import jp.co.soramitsu.fearless_utils.runtime.RuntimeSnapshot
import jp.co.soramitsu.runtime.multiNetwork.chain.model.Chain
import jp.co.soramitsu.runtime.multiNetwork.chain.model.TypesUsage
import jp.co.soramitsu.testshared.any
import jp.co.soramitsu.testshared.eq
import jp.co.soramitsu.testshared.thenThrowUnsafe
import jp.co.soramitsu.testshared.whenever
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.first
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

    lateinit var runtimeProvider: RuntimeProvider

    @Before
    fun setup() {
        runBlocking {
            chain = Mocks.chain(id = "1")

            chainSyncFlow = MutableSharedFlow()

            whenever(constructedRuntime.runtime).thenReturn(runtime)
            whenever(runtimeFactory.constructRuntime(any(), any())).thenReturn(constructedRuntime)

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
    fun `should not reconstruct runtime if types and runtime were not synced`() {
        runBlocking {
            initProvider()

            currentChainTypesHash("Hash")
            currentMetadataHash("Hash")

            chainSyncFlow.emit(SyncResult(chain.id, metadataHash =  null, typesHash = null))

            verifyReconstructionNotStarted()
        }
    }

    @Test
    fun `should wait until current job is finished before consider reconstructing runtime on runtime sync event`() {
        runBlocking {
            whenever(runtimeFactory.constructRuntime(any(), any())).thenAnswer {
                runBlocking { chainSyncFlow.first() }  // ensure runtime wont be returned until chainSyncFlow event

                constructedRuntime
            }

            initProvider()

            currentChainTypesHash("Hash")
            currentMetadataHash("Hash")

            chainSyncFlow.emit(SyncResult(chain.id, metadataHash =  null, typesHash = null))

            verifyReconstructionNotStarted()
        }
    }

    @Test
    fun `should report missing cache for chain types or metadata`() {
        runBlocking {
            withRuntimeFactoryFailing(ChainInfoNotInCacheException) {
                verify(runtimeSyncService, times(1)).cacheNotFound(eq(chain.id))
            }
        }
    }

    @Test
    fun `should construct runtime on type usage change`() {
        runBlocking {
            initProvider(typesUsage = TypesUsage.ON_CHAIN)

            runtimeProvider.considerUpdatingTypesUsage(TypesUsage.UNSUPPORTED)

            verifyReconstructionStarted()
        }
    }

    @Test
    fun `should not construct runtime on same type usage`() {
        runBlocking {
            initProvider(typesUsage = TypesUsage.ON_CHAIN)

            runtimeProvider.considerUpdatingTypesUsage(TypesUsage.ON_CHAIN)

            verifyReconstructionNotStarted()
        }
    }

    private suspend fun verifyReconstructionNotStarted() {
        verifyReconstructionAfterInit(0)
    }

    private suspend fun verifyReconstructionStarted() {
        verifyReconstructionAfterInit(1)
    }

    private suspend fun withRuntimeFactoryFailing(exception: Exception = ChainInfoNotInCacheException, block: suspend () -> Unit) {
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

    private fun currentMetadataHash(hash: String?) {
        whenever(constructedRuntime.metadataHash).thenReturn(hash)
    }

    private fun currentChainTypesHash(hash: String?) {
        whenever(constructedRuntime.ownTypesHash).thenReturn(hash)
    }

    private fun initProvider(typesUsage: TypesUsage? = null) {
        val types = when (typesUsage) {
            TypesUsage.ON_CHAIN -> Chain.Types(url = "url", overridesCommon = true)
            else -> null
        }

        whenever(chain.types).thenReturn(types)

        runtimeProvider = RuntimeProvider(runtimeFactory, runtimeSyncService, chain)
    }
}
