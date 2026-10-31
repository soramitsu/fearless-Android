package jp.co.soramitsu.staking.impl.scenarios.relaychain

import com.google.gson.Gson
import jp.co.soramitsu.common.data.network.rpc.BulkRetriever
import jp.co.soramitsu.common.data.network.runtime.binding.Binder
import jp.co.soramitsu.common.utils.numberConstant
import jp.co.soramitsu.common.utils.staking
import jp.co.soramitsu.core.model.StorageEntry
import jp.co.soramitsu.core.runtime.ChainConnection
import jp.co.soramitsu.core.runtime.storage.returnType
import jp.co.soramitsu.core.storage.StorageCache
import jp.co.soramitsu.core.updater.SubscriptionBuilder
import jp.co.soramitsu.fearless_utils.runtime.definitions.types.Type
import jp.co.soramitsu.fearless_utils.runtime.definitions.types.composite.Struct
import jp.co.soramitsu.fearless_utils.runtime.definitions.types.toHex
import jp.co.soramitsu.fearless_utils.runtime.metadata.storage
import jp.co.soramitsu.fearless_utils.wsrpc.SocketService
import jp.co.soramitsu.fearless_utils.wsrpc.request.runtime.RuntimeRequest
import jp.co.soramitsu.fearless_utils.wsrpc.response.RpcResponse
import jp.co.soramitsu.runtime.assethub.AssetHubStaking
import jp.co.soramitsu.runtime.multiNetwork.ChainRegistry
import jp.co.soramitsu.runtime.storage.source.RemoteStorageSource
import jp.co.soramitsu.runtime.storage.source.StorageDataSource
import jp.co.soramitsu.staking.api.data.StakingSharedState
import jp.co.soramitsu.staking.impl.data.network.blockhain.updaters.activeEraStorageKeyOrNull
import jp.co.soramitsu.staking.impl.data.network.blockhain.updaters.historical.HistoricalTotalValidatorRewardUpdater
import jp.co.soramitsu.staking.impl.data.network.blockhain.updaters.historical.HistoricalUpdateMediator
import jp.co.soramitsu.staking.impl.data.network.blockhain.updaters.historical.HistoricalValidatorRewardPointsUpdater
import jp.co.soramitsu.staking.impl.data.repository.AssetHubMetadataContractTest
import jp.co.soramitsu.staking.impl.domain.rewards.RewardCalculatorFactory
import jp.co.soramitsu.core.models.Ecosystem
import jp.co.soramitsu.fearless_utils.extensions.toHexString
import jp.co.soramitsu.runtime.multiNetwork.ChainService
import jp.co.soramitsu.runtime.multiNetwork.chain.model.Chain
import jp.co.soramitsu.runtime.multiNetwork.runtime.RuntimeProvider
import jp.co.soramitsu.staking.api.domain.api.IdentityRepository
import jp.co.soramitsu.staking.api.domain.model.Nominations
import jp.co.soramitsu.staking.api.domain.model.StakingState
import jp.co.soramitsu.staking.impl.data.repository.StakingConstantsRepository
import jp.co.soramitsu.staking.impl.domain.validators.ValidatorProvider
import jp.co.soramitsu.staking.impl.domain.validators.current.CurrentValidatorsInteractor
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.asFlow
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.anyOrNull
import org.mockito.kotlin.doReturn
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever
import java.io.IOException
import java.math.BigInteger
import jp.co.soramitsu.core.models.Asset as CoreAsset

/**
 * On an Asset Hub the active era is a remote read, and here every such read fails. A failed read is that failure,
 * never an absent era or era zero. The callers that used to close the app on it now survive: the staking history
 * updater works from the era it observes and keeps running after a failed round, and the staking and pool setup
 * screens get zero reward estimates.
 */
class AssetHubActiveEraFailureTest {
    private val runtime = AssetHubMetadataContractTest.runtimes.getValue("polkadot")
    private val staking = runtime.metadata.staking()
    private val block = "0x" + "ab".repeat(32)
    private val socket = mock<SocketService>()
    private val connection = mock<ChainConnection> { on { socketService } doReturn socket }
    private val registry = mock<ChainRegistry>()
    private val local = mock<StorageDataSource>()
    private val provider = mock<RuntimeProvider>()
    private val activeEraKey = requireNotNull(runtime.metadata.activeEraStorageKeyOrNull())
    private val repository =
        StakingRelayChainScenarioRepository(RemoteStorageSource(registry, BulkRetriever()), local, registry, mock(), mock(), mock())

    @Suppress("UNCHECKED_CAST")
    private fun encode(name: String, value: Any): String = (staking.storage(name).returnType() as Type<Any>).toHex(runtime, value)

    private fun era(index: Int) = encode("ActiveEra", Struct.Instance(mapOf("index" to index.toBigInteger(), "start" to 1000.toBigInteger())))

    private val cachedEra by lazy { era(100) }

    private suspend fun setUp() {
        whenever(registry.getRuntime(AssetHubStaking.POLKADOT)).thenReturn(runtime)
        whenever(registry.awaitConnection(AssetHubStaking.POLKADOT)).thenReturn(connection)
        whenever(socket.jsonMapper).thenReturn(Gson())
        whenever(registry.getService(AssetHubStaking.POLKADOT)).thenReturn(ChainService(provider, connection))
        whenever(provider.get()).thenReturn(runtime)
        whenever(socket.executeRequest(any(), any(), any())).thenAnswer { call ->
            val request = call.getArgument<RuntimeRequest>(0)
            val callback = call.getArgument<SocketService.ResponseListener<RpcResponse>>(2)
            val result: Any? = when (request.method) {
                "chain_getFinalizedHead" -> block
                "state_getKeysPaged" -> emptyList<String>()
                else -> {
                    @Suppress("UNCHECKED_CAST")
                    val keys = request.params[0] as List<String>
                    // The read of the Hub's head era fails; a read pinned to the finalized block answers.
                    val pinned = request.params.size > 1
                    if (activeEraKey in keys && !pinned) null else {
                        val changes = keys.map { listOf(it, if (it == activeEraKey) cachedEra else null) }
                        listOf(mapOf("block" to block, "changes" to changes))
                    }
                }
            }
            if (result == null) {
                callback.onError(IOException("Local node dropped the read"))
            } else {
                callback.onNext(RpcResponse("2.0", result, request.id, null))
            }
            mock<SocketService.Cancellable>()
        }
        // CurrentEraUpdater keeps the current era in the local cache.
        whenever(local.query<BigInteger>(any(), any(), anyOrNull(), any())).thenAnswer { call ->
            call.getArgument<Binder<BigInteger>>(3).invoke(encode("CurrentEra", 101.toBigInteger()), runtime)
        }
    }

    @Test
    fun `a failed Hub era read is that failure, not a missing era`() = runTest {
        setUp()
        val failure = runCatching { repository.getActiveEraIndex(AssetHubStaking.POLKADOT) }.exceptionOrNull()
        assertTrue(failure.toString(), failure is IOException)
    }

    @Test
    fun `staking and pool setup estimates fall back to zero`() = runTest {
        setUp()
        val factory = RewardCalculatorFactory(repository, mock(), mock(), mock(), mock())
        val dot = mock<CoreAsset> {
            on { chainId } doReturn AssetHubStaking.POLKADOT
            on { staking } doReturn CoreAsset.StakingType.RELAYCHAIN
        }
        assertEquals(0, factory.create(dot).calculateMaxAPY(AssetHubStaking.POLKADOT).signum())
        assertEquals(0, factory.createWithValidators(dot, listOf(ByteArray(32) { 2 })).calculateAvgAPY().signum())
    }

    @Test
    fun `the history updater works from the era it observes and survives a failed round`() = runTest {
        setUp()
        val cache = mock<StorageCache>()
        val eras = listOf(99, 100).map { StorageEntry(activeEraKey, era(it)) }
        whenever(cache.observeEntry(activeEraKey, AssetHubStaking.POLKADOT)).thenReturn(eras.asFlow())
        var rounds = 0
        whenever(cache.filterKeysInCache(any(), any())).thenAnswer {
            if (rounds++ == 0) throw IllegalStateException("Local cache unavailable") else emptyList<String>()
        }
        val inserted = mutableListOf<StorageEntry>()
        whenever(cache.insert(any<List<StorageEntry>>(), any())).thenAnswer {
            inserted += it.getArgument<List<StorageEntry>>(0)
            Unit
        }
        val shared = mock<StakingSharedState> { onBlocking { chainId() } doReturn AssetHubStaking.POLKADOT }
        val builder = mock<SubscriptionBuilder> { on { socketService } doReturn socket }
        val mediator = HistoricalUpdateMediator(
            listOf(HistoricalTotalValidatorRewardUpdater(), HistoricalValidatorRewardPointsUpdater()),
            shared, BulkRetriever(), repository, cache, registry
        )

        mediator.listenForUpdates(builder).collect()

        // Era 99's round failed; era 100's caches both histories of the eras from CurrentEra - HistoryDepth to 99.
        val historyDepth = staking.numberConstant("HistoryDepth", runtime).toInt()
        assertEquals(2 * (100 - (101 - historyDepth)), inserted.size)
    }

    @Test
    fun `current validators load from the era the screen observes`() = runTest {
        setUp()
        // The staking update system keeps the active era in the local cache.
        whenever(local.observe<BigInteger?>(any(), any(), any())).thenAnswer { call ->
            flowOf(call.getArgument<Binder<BigInteger?>>(2).invoke(cachedEra, runtime))
        }
        // Nothing else is cached: no validator preferences or exposure pages.
        whenever(local.queryByPrefix<Any, Any>(any(), any(), any(), any())).thenReturn(emptyMap())
        val identities = mock<IdentityRepository> { onBlocking { getIdentitiesFromIds(any(), any()) } doReturn emptyMap() }
        val constants = mock<StakingConstantsRepository> { onBlocking { maxValidatorsPerNominator(any()) } doReturn 16 }
        val provider = ValidatorProvider(repository, identities, RewardCalculatorFactory(repository, mock(), mock(), mock(), mock()), constants)
        val chain = mock<Chain> {
            on { id } doReturn AssetHubStaking.POLKADOT
            on { ecosystem } doReturn Ecosystem.Substrate
        }
        val validator = ByteArray(32) { 2 }
        val stash = ByteArray(32) { 6 }
        val nominator = StakingState.Stash.Nominator(chain, stash, stash, stash, Nominations(listOf(validator), 90.toBigInteger(), false))

        val grouped = CurrentValidatorsInteractor(repository, constants, provider).nominatedValidatorsFlow(nominator).first()

        assertEquals(listOf(validator.toHexString()), grouped.values.flatten().map { it.validator.accountIdHex })
    }
}
